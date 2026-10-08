// 文件：CookieInterceptors.kt
// 职责：OkHttp 的两条 Cookie 拦截器（按域名注入 Cookie / 采集响应 Set-Cookie 回写仓库）
// 依赖：OkHttp、CookieStore、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

// 【修订 JYD-DEBT11-2026-10-07】自 di/NetworkModule.kt 拆出：两条拦截器（约 95 行）与它们
// 依赖的域名常量，本属「Cookie 复用层」而非「依赖注入装配」；拆出后 NetworkModule 只负责
// 构建 OkHttpClient / Retrofit 与各网卡 API 实例。可见性 internal（模块内使用）。

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/** 夸克 Cookie 域名后缀（同时覆盖 pan/drive-pc/drive.quark.cn 等）。 */
internal const val COOKIE_DOMAIN_QUARK = "quark.cn"

/** UC Cookie 域名后缀（同时覆盖 drive.uc.cn 与 pc-api.uc.cn）。 */
internal const val COOKIE_DOMAIN_UC = "uc.cn"

/** 百度 Cookie 域名后缀（同时覆盖 pan.baidu.com / yun.baidu.com / d.pcs.baidu.com）。 */
internal const val COOKIE_DOMAIN_BAIDU = "baidu.com"

/** 多 Cookie 拼接分隔符（HTTP Cookie 头规范）。 */
internal const val COOKIE_SEPARATOR = "; "

/** Cookie 请求头名。 */
internal const val HEADER_COOKIE = "Cookie"

/** 响应 Cookie 头名。 */
internal const val HEADER_SET_COOKIE = "Set-Cookie"

/** HTTP GET 方法名（用于把「写类调用」从诊断日志中区分出来）。 */
internal const val METHOD_GET = "GET"

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
internal class CookieInterceptor(private val cookieStore: CookieStore) : Interceptor {

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
internal class ResponseCookieInterceptor(private val cookieStore: CookieStore) : Interceptor {

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
            host == COOKIE_DOMAIN_BAIDU || host.endsWith(".$COOKIE_DOMAIN_BAIDU") ->
                COOKIE_DOMAIN_BAIDU
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
