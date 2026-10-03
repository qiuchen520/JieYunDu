// 文件：CookieStore.kt
// 职责：内存态 Cookie 仓库，按域名后缀保存与取出 Cookie 串
// 依赖：无（纯 Kotlin + JVM 并发容器，不依赖 Android）
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内存态 Cookie 仓库。
 *
 * 背景（依据《阶段 6 交付后整改指令》硬伤 1）：解析器在握手阶段拿到的 Cookie
 * （如夸克首页返回的 `__puus`）必须能传递到后续 Retrofit 请求。QuarkApi 的方法
 * 签名在《要求.md》7.6 中已定死、不允许增加 Header / Cookie 参数，故采用
 * 「解析器写入仓库 → CookieInterceptor 按域名注入」的旁路方案（方案 B）：
 * 解析器只负责拿 Cookie，注入由网络层统一完成，职责清晰且不污染接口签名。
 *
 * 线程安全：读写均基于 [ConcurrentHashMap]，可在多协程并发下使用。
 *
 * 生命周期：App 进程内存态，进程结束即失效（阶段 7 之后再评估是否持久化）。
 */
@Singleton
class CookieStore @Inject constructor() {

    /** 域名后缀 -> 该域可用的 Cookie 串（形如 `__puus=xxx`）。 */
    private val byDomainSuffix = ConcurrentHashMap<String, String>()

    /**
     * 保存 / 覆盖某个域名后缀下的 Cookie。
     *
     * @param domainSuffix 域名后缀（例如 `quark.cn`，可同时覆盖 pan.quark.cn 与
     *   drive-pc.quark.cn）。
     * @param cookie 原始 Cookie 串（形如 `__puus=xxx`）；为空时忽略，避免写入脏值。
     */
    fun save(domainSuffix: String, cookie: String) {
        if (domainSuffix.isBlank() || cookie.isBlank()) return
        byDomainSuffix[domainSuffix] = cookie
    }

    /**
     * 取出可作用于指定 host 的 Cookie。
     *
     * 匹配规则：host 等于后缀本身，或以 `.后缀` 结尾即命中
     * （`drive-pc.quark.cn` 命中 `quark.cn`，`notquark.cn` 不命中）。
     *
     * @param host 请求目标主机名。
     * @return 命中的 Cookie 串；无命中返回 null。
     */
    fun findForHost(host: String): String? =
        byDomainSuffix.entries
            .firstOrNull { (suffix, _) -> host == suffix || host.endsWith(".$suffix") }
            ?.value

    /** 清空全部 Cookie。 */
    fun clear() {
        byDomainSuffix.clear()
    }
}
