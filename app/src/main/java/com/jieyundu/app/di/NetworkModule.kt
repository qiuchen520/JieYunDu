// 文件：NetworkModule.kt
// 职责：提供 OkHttpClient、Retrofit、Json 与各网盘 API 实例（Cookie 拦截器见 data/remote）
// 依赖：OkHttp、Retrofit、kotlinx.serialization、QuarkApi、UserAgentProvider
// 协议：AGPL-3.0

package com.jieyundu.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.jieyundu.app.BuildConfig
import com.jieyundu.app.data.remote.COOKIE_DOMAIN_BAIDU
import com.jieyundu.app.data.remote.COOKIE_DOMAIN_UC
import com.jieyundu.app.data.remote.CookieInterceptor
import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.data.remote.ResponseCookieInterceptor
import com.jieyundu.app.data.remote.UserAgentProvider
import com.jieyundu.app.domain.parser.baidu.BaiduApi
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

    /** 百度网盘接口 BaseUrl（《抓包事实.md》§3：`pan.baidu.com`）。 */
    private const val BASE_URL_BAIDU = "https://pan.baidu.com/"

    /** UC PC 接口 BaseUrl（《抓包事实.md》§2：业务基址 pc-api.uc.cn，**不是** drive-pc.quark.cn）。 */
    private const val BASE_URL_UC = "https://pc-api.uc.cn/"

    /** 请求体 MIME 类型。 */
    private const val CONTENT_TYPE_JSON = "application/json"

    /** User-Agent 请求头名。 */
    private const val HEADER_USER_AGENT = "User-Agent"

    /** 百度 CDN 直链主机（该主机的请求一律使用客户端 UA）。 */
    private const val HOST_BAIDU_PCS = "d.pcs.baidu.com"

    /**
     * 百度**客户端 UA** 的路径前缀（同一 host 上按接口区分两套 UA）。
     *
     * 依据：《抓包事实.md》§11.3 #4/#5/#9/#14——建目录 / 个人列表 / filemanager / 配额用客户端 UA；
     * 其余（`share/verify`、`xpan/share`、`gettemplatevariable`、`filemetas`）用网页 UA。
     */
    private val BAIDU_NETDISK_PATH_PREFIXES =
        listOf("/api/list", "/api/filemanager", "/api/create", "/api/quota")

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
                // 按目标 host 选择对应网盘的 UA：
                // - UC：统一用「网页 / 登录态」UA（普通 Chrome 120）。依据评审方
                //   《UC取链请求_逐字段对照.txt》——token / transfer_share/detail / file/download
                //   均以普通 Chrome UA 下发，用云盘客户端 UA 取链会被拒。
                // - 夸克：用 API / 客户端 UA。
                // 依据：两家 UA 不得混用（《抓包事实.md》§2「三套 UA」与 §1「两套 UA」）。
                val host = original.url.host
                val agent = when {
                    isUcHost(host) -> userAgentProvider.ucWebUserAgent
                    // 百度：**同一 host 混用两套 UA**，按「CDN 主机 or 客户端接口路径」区分
                    // （《抓包事实.md》§3 / §11.3）。
                    isBaiduHost(host) -> if (host == HOST_BAIDU_PCS ||
                        isBaiduNetdiskPath(original.url.encodedPath)
                    ) {
                        userAgentProvider.baiduNetdiskUserAgent
                    } else {
                        userAgentProvider.baiduWebUserAgent
                    }
                    else -> userAgentProvider.quarkUserAgent
                }
                // 方法级 @Headers 已指定 UA 的请求不再覆盖（保留给后续需要固定 UA 的接口）。
                val request = if (agent.isNotBlank() && original.header(HEADER_USER_AGENT) == null) {
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
     * 提供百度接口实现（《抓包事实.md》§3：业务基址 `pan.baidu.com`）。
     *
     * 说明：`locatedownload` 走另一主机 `d.pcs.baidu.com`（§11.3 #7），
     * 该端点在 B3-2 接入时以 `@Url` 绝对地址调用，无需第二个 Retrofit 实例。
     *
     * @param okHttpClient 全局客户端。
     * @param json JSON 解析器。
     * @return BaiduApi 动态代理实例。
     */
    @Provides
    @Singleton
    fun provideBaiduApi(okHttpClient: OkHttpClient, json: Json): BaiduApi =
        Retrofit.Builder()
            .baseUrl(BASE_URL_BAIDU)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(CONTENT_TYPE_JSON.toMediaType()))
            .build()
            .create(BaiduApi::class.java)

    /**
     * 判定目标 host 是否属于 UC 域名族。
     *
     * @param host 请求目标主机名（如 `pc-api.uc.cn`）。
     * @return true 表示 UC。
     */
    private fun isUcHost(host: String): Boolean =
        host == COOKIE_DOMAIN_UC || host.endsWith(".$COOKIE_DOMAIN_UC")

    /**
     * 判定目标 host 是否属于百度域名族。
     *
     * @param host 请求目标主机名（如 `pan.baidu.com` / `d.pcs.baidu.com`）。
     * @return true 表示百度。
     */
    private fun isBaiduHost(host: String): Boolean =
        host == COOKIE_DOMAIN_BAIDU || host.endsWith(".$COOKIE_DOMAIN_BAIDU")

    /**
     * 判定百度请求路径是否属于「客户端 UA」接口。
     *
     * @param path 请求路径（如 `/api/list`）。
     * @return true 表示应使用客户端 UA。
     */
    private fun isBaiduNetdiskPath(path: String): Boolean =
        BAIDU_NETDISK_PATH_PREFIXES.any { prefix -> path.startsWith(prefix) }

    /** 日志 TAG。 */
    private const val TAG_HTTP = "OkHttp"

}