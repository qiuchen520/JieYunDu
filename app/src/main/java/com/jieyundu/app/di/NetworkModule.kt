// 文件：NetworkModule.kt
// 职责：提供 OkHttpClient、Retrofit、Json 与各网盘 API 实例
// 依赖：OkHttp、Retrofit、kotlinx.serialization、QuarkApi、UserAgentProvider
// 协议：AGPL-3.0

package com.jieyundu.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.jieyundu.app.BuildConfig
import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.data.remote.UserAgentProvider
import com.jieyundu.app.domain.parser.quark.QuarkApi
import com.jieyundu.app.domain.parser.uc.UcApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import timber.log.Timber

/**
 * 网络层依赖提供者。
 *
 * 注意（D1 边界）：模块内不做任何业务解析，只负责构造与配置网络组件。
 *
 * UA / Referer 常量集中在 data/remote/UserAgentProvider 中维护（依据【修订
 * JYD-ERRATA-2026-10-03】修订三），本模块只负责把默认 User-Agent 注入请求头。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** 连接超时（C4：15 秒）。 */
    private const val TIMEOUT_CONNECT_SECONDS = 15L

    /** 读取超时（C4：30 秒）。 */
    private const val TIMEOUT_READ_SECONDS = 30L

    /** 写入超时（C4：30 秒）。 */
    private const val TIMEOUT_WRITE_SECONDS = 30L

    /** 夸克 PC 接口 BaseUrl。 */
    private const val BASE_URL_QUARK = "https://drive-pc.quark.cn/"

    /** UC PC 接口 BaseUrl（《抓包事实.md》§2：业务基址 pc-api.uc.cn，**不是** drive-pc.quark.cn）。 */
    private const val BASE_URL_UC = "https://pc-api.uc.cn/"

    /** 请求体 MIME 类型。 */
    private const val CONTENT_TYPE_JSON = "application/json"

    /** User-Agent 请求头名。 */
    private const val HEADER_USER_AGENT = "User-Agent"

    /** Cookie 请求头名。 */
    private const val HEADER_COOKIE = "Cookie"

    /** 响应 Cookie 头名。 */
    private const val HEADER_SET_COOKIE = "Set-Cookie"

    /** 夸克 Cookie 域名后缀（同时覆盖 pan/drive-pc/drive.quark.cn 等）。 */
    private const val COOKIE_DOMAIN_QUARK = "quark.cn"

    /** UC Cookie 域名后缀（同时覆盖 drive.uc.cn 与 pc-api.uc.cn）。 */
    private const val COOKIE_DOMAIN_UC = "uc.cn"

    /** 多 Cookie 拼接分隔符（HTTP Cookie 头规范）。 */
    private const val COOKIE_SEPARATOR = "; "

    /** HTTP GET 方法名（用于把「写类调用」从诊断日志中区分出来）。 */
    private const val METHOD_GET = "GET"

    /** 连接池最大空闲连接数（B2 功能②：配合 32–512 分片并发，避免频繁重建 TLS 连接）。 */
    private const val MAX_IDLE_CONNECTIONS = 512

    /** 空闲连接保活时长（分钟）。 */
    private const val KEEP_ALIVE_MINUTES = 5L

    /** OkHttp 分派器最大请求数（含异步；同步下载主要受线程数限制，此处一并抬高）。 */
    private const val MAX_REQUESTS = 512

    /** OkHttp 分派器单主机最大请求数（直链 CDN 同主机多分片需放宽）。 */
    private const val MAX_REQUESTS_PER_HOST = 512

    /**
     * 提供全局 OkHttpClient。
     *
     * @param userAgentProvider 四家网盘 UA 常量提供者。
     * @param cookieStore 内存态 Cookie 仓库，供 [CookieInterceptor] 按域名注入。
     * @return 配置好超时、默认 UA、Cookie 注入与调试日志的客户端。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        userAgentProvider: UserAgentProvider,
        cookieStore: CookieStore
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_CONNECT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_READ_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_WRITE_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // B2 功能②：抬高连接池与分派器上限，支撑 32–512 分片的高并发直链下载。
            .connectionPool(
                ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES)
            )
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = MAX_REQUESTS
                    maxRequestsPerHost = MAX_REQUESTS_PER_HOST
                }
            )
            .addInterceptor { chain ->
                val original = chain.request()
                // 按目标 host 选择对应网盘的 UA：UC 用 ucUserAgent，其余（夸克）用 quarkUserAgent。
                // 依据：两家 UA 不得混用（《抓包事实.md》§2「三套 UA」与 §1「两套 UA」）。
                val agent = if (isUcHost(original.url.host)) {
                    userAgentProvider.ucUserAgent
                } else {
                    userAgentProvider.quarkUserAgent
                }
                val request = if (agent.isNotBlank()) {
                    original.newBuilder().header(HEADER_USER_AGENT, agent).build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .addInterceptor(CookieInterceptor(cookieStore))
            .addInterceptor(ResponseCookieInterceptor(cookieStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor { message ->
                        Timber.tag(TAG_HTTP).d(message)
                    }
                    logging.level = HttpLoggingInterceptor.Level.BASIC
                    addInterceptor(logging)
                }
            }
            .build()

    /**
     * 提供宽松的 JSON 解析器：忽略未知字段，避免网盘增删字段导致解析崩溃。
     *
     * @return Json 实例。
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * 提供 Retrofit 实例（夸克 BaseUrl 起步，后续网盘由各自 API 的完整路径兼容）。
     *
     * @param okHttpClient 全局客户端。
     * @param json JSON 解析器。
     * @return Retrofit 实例。
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL_QUARK)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(CONTENT_TYPE_JSON.toMediaType()))
            .build()

    /**
     * 提供夸克接口实现。
     *
     * @param retrofit Retrofit 实例。
     * @return QuarkApi 动态代理实例。
     */
    @Provides
    @Singleton
    fun provideQuarkApi(retrofit: Retrofit): QuarkApi = retrofit.create(QuarkApi::class.java)

    /**
     * 提供 UC 接口实现。
     *
     * 说明：UC 的业务基址是 `pc-api.uc.cn`（与夸克不同），无法复用夸克 Retrofit，
     * 故在此**独立构建**一个 Retrofit（复用同一 OkHttpClient / Json 转换器）。
     *
     * @param okHttpClient 全局客户端（含按 host 选 UA、Cookie 注入/采集拦截器）。
     * @param json JSON 解析器。
     * @return UcApi 动态代理实例。
     */
    @Provides
    @Singleton
    fun provideUcApi(okHttpClient: OkHttpClient, json: Json): UcApi =
        Retrofit.Builder()
            .baseUrl(BASE_URL_UC)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(CONTENT_TYPE_JSON.toMediaType()))
            .build()
            .create(UcApi::class.java)

    /**
     * 判定目标 host 是否属于 UC 域名族。
     *
     * @param host 请求目标主机名（如 `pc-api.uc.cn`）。
     * @return true 表示 UC。
     */
    private fun isUcHost(host: String): Boolean =
        host == COOKIE_DOMAIN_UC || host.endsWith(".$COOKIE_DOMAIN_UC")

    /** 日志 TAG。 */
    private const val TAG_HTTP = "OkHttp"

    /**
     * 应用级 Cookie 注入拦截器（整改指令硬伤 1 · 方案 B）。
     *
     * 从 [CookieStore] 按目标 host 取出 Cookie，若请求尚未携带 Cookie 头则注入。
     * 仅改写请求头，不修改响应、不写盘，全程无副作用；仓库无命中时等价于空操作。
     *
     * 之所以放在网络层而非 API 签名上：QuarkApi 三方法签名已在《要求.md》7.6
     * 定死，方案 A / C 都会改动签名，故采用本旁路方案。
     *
     * @param cookieStore 内存态 Cookie 仓库。
     */
    private class CookieInterceptor(private val cookieStore: CookieStore) : Interceptor {

        /**
         * 注入 Cookie 头。
         *
         * 写操作诊断（B1.2）：非 GET（转存 / 建目录 / 取直链等写类调用）额外记录本次携带的
         * Cookie **名**（不含值，避免日志泄露凭证）。目的是回答「`__puus` 有没有被送出去」——
         * 若下一份日志里服务端返回体为空，这是唯一可判定的信号。
         *
         * @param chain 拦截器链。
         * @return 下游响应。
         */
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val cookie = cookieStore.findForHost(request.url.host)
            if (request.method != METHOD_GET) {
                Timber.d(
                    "CookieInterceptor %s %s cookieNames=%s",
                    request.method,
                    request.url.encodedPath,
                    cookie?.let { value -> cookieNamesOf(value) }.orEmpty()
                )
            }
            if (cookie.isNullOrBlank() || request.header(HEADER_COOKIE) != null) {
                return chain.proceed(request)
            }
            return chain.proceed(
                request.newBuilder()
                    .header(HEADER_COOKIE, cookie)
                    .build()
            )
        }

        /**
         * 提取 Cookie 串中出现过的**名字**（逗号分隔，**不含值**）。
         *
         * @param cookie 待解析的 Cookie 串。
         * @return 形如 `__pus,__puus,__pugs`；无有效名时为空串。
         */
        private fun cookieNamesOf(cookie: String): String =
            cookie.split(COOKIE_SEPARATOR)
                .mapNotNull { pair ->
                    pair.substringBefore('=').trim().takeIf { name -> name.isNotEmpty() }
                }
                .joinToString(",")
    }

    /**
     * 响应 Cookie 持久化拦截器（夸克通道）。
     *
     * 背景（夸克接口实测 2026-10-03）：`file/download` 响应会下发 `__pugs` Cookie
     * （Domain=quark.cn，Max-Age=10800），而 CDN 直链
     * （`dl-guest-*.drive.quark.cn`）**必须**携带该 Cookie，否则返回 HTTP 412。
     * Retrofit 的返回体不含响应头，故在此统一把 `.quark.cn` 响应的 `Set-Cookie`
     * 登记到 [CookieStore]，供后续请求（含下载引擎的分片请求）按域名注入。
     *
     * 说明：[CookieStore.save] 现按域名后缀**合并**存储（同名键以后者为准），因此
     * `__pus` / `__puus`（登录态）与下载响应下发的 `__pugs` 会共存、不会互相冲掉
     * （《解析Bug分析.md》P1-1）；CDN 直链只依赖 `__pugs`，而它正是在 download 响应中下发。
     *
     * @param cookieStore 内存态 Cookie 仓库。
     */
    private class ResponseCookieInterceptor(private val cookieStore: CookieStore) : Interceptor {

        /**
         * 采集响应 Cookie。
         *
         * @param chain 拦截器链。
         * @return 下游响应（原样返回，不修改）。
         */
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val host = chain.request().url.host
            // 按目标 host 判定域名族：夸克（quark.cn）或 UC（uc.cn）。两家分别登记到各自后缀。
            val domainSuffix = when {
                host == COOKIE_DOMAIN_QUARK || host.endsWith(".$COOKIE_DOMAIN_QUARK") ->
                    COOKIE_DOMAIN_QUARK
                host == COOKIE_DOMAIN_UC || host.endsWith(".$COOKIE_DOMAIN_UC") ->
                    COOKIE_DOMAIN_UC
                else -> null
            }
            if (domainSuffix != null) {
                val cookie = response.headers.values(HEADER_SET_COOKIE)
                    .mapNotNull { raw ->
                        raw.substringBefore(';').takeIf { pair -> pair.contains('=') }
                    }
                    .joinToString(COOKIE_SEPARATOR)
                if (cookie.isNotBlank()) {
                    cookieStore.save(domainSuffix, cookie)
                }
            }
            return response
        }
    }
}