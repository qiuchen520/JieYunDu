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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
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

    /** 请求体 MIME 类型。 */
    private const val CONTENT_TYPE_JSON = "application/json"

    /** User-Agent 请求头名。 */
    private const val HEADER_USER_AGENT = "User-Agent"

    /** Cookie 请求头名。 */
    private const val HEADER_COOKIE = "Cookie"

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
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .apply {
                        val agent = userAgentProvider.quarkUserAgent
                        if (agent.isNotBlank()) {
                            header(HEADER_USER_AGENT, agent)
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(CookieInterceptor(cookieStore))
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
         * @param chain 拦截器链。
         * @return 下游响应。
         */
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val cookie = cookieStore.findForHost(request.url.host)
            if (cookie.isNullOrBlank() || request.header(HEADER_COOKIE) != null) {
                return chain.proceed(request)
            }
            return chain.proceed(
                request.newBuilder()
                    .header(HEADER_COOKIE, cookie)
                    .build()
            )
        }
    }
}
