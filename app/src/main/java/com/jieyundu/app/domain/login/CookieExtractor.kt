// 文件：CookieExtractor.kt
// 职责：从 WebView 提取登录态 Cookie（方案 A 原生读取 + 拦截兜底合并），并判定是否已登录
// 依赖：android.webkit.CookieManager、NetdiskType、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.login

import android.webkit.CookieManager
import com.jieyundu.app.domain.model.NetdiskType
import timber.log.Timber

/**
 * 登录态 Cookie 提取器（阶段 11）。
 *
 * 提取策略（Owner 裁决）：**方案 A 为主 + 拦截兜底**。
 * - 方案 A（主）：`CookieManager.getCookie(url)` 读取 WebView 原生 cookie 库。HttpOnly
 *   只限制 JavaScript 的 `document.cookie`，不限制原生 API，因此可拿到 `__puus` 等登录态。
 * - 拦截兜底：`WebViewClient.shouldInterceptRequest` 读取 WebView 实际发出的 `Cookie`
 *   请求头（同样包含 HttpOnly），作为方案 A 失手时的补充。
 *
 * 说明：本类仅做纯函数式解析（无状态、无 Android 生命周期耦合），便于复用与测试。
 */
object CookieExtractor {

    /** 夸克登录态判定 Cookie 名（须与 [PUS_COOKIE] 同时存在）。 */
    const val QUARK_LOGIN_COOKIE = "__puus"

    /** 登录态判定 Cookie 名之二（夸克 / UC 同构，须与 [QUARK_LOGIN_COOKIE] 同时存在）。 */
    const val PUS_COOKIE = "__pus"

    /** Cookie 名值对分隔符（RFC6265）。 */
    private const val PAIR_SEPARATOR = "; "

    /** 单个 Cookie 名值分隔符。 */
    private const val NAME_VALUE_SEPARATOR = '='

    /**
     * 某网盘的登录入口 URL（用于 WebView 打开与按域提取 Cookie）。
     *
     * @param type 网盘类型。
     * @return 登录首页 URL。
     */
    fun loginUrlOf(type: NetdiskType): String = when (type) {
        NetdiskType.QUARK -> "https://pan.quark.cn/?fr=pc&platform=pc"
        NetdiskType.BAIDU -> "https://pan.baidu.com"
        NetdiskType.UC -> "https://drive.uc.cn"
        NetdiskType.XUNLEI -> "https://pan.xunlei.com"
    }

    /**
     * 某网盘 Cookie 应归属的域名后缀（供 `CookieStore` 按 host 命中的依据）。
     *
     * @param type 网盘类型。
     * @return 域名后缀（例如 `quark.cn`）。
     */
    fun cookieDomainOf(type: NetdiskType): String = when (type) {
        NetdiskType.QUARK -> "quark.cn"
        NetdiskType.BAIDU -> "baidu.com"
        NetdiskType.UC -> "uc.cn"
        NetdiskType.XUNLEI -> "xunlei.com"
    }

    /**
     * 提取 Cookie 时需覆盖的 URL 列表（跨子域）。
     *
     * @param type 网盘类型。
     * @return 需要逐一 `getCookie` 的 URL 列表。
     */
    fun cookieUrlsOf(type: NetdiskType): List<String> = when (type) {
        NetdiskType.QUARK -> listOf(
            "https://pan.quark.cn",
            "https://drive-pc.quark.cn",
            "https://drive.quark.cn"
        )

        // 百度：BDUSS 通常挂在 `.baidu.com`，读取 pan 子域即可拿到；
        // 但 App 的列表 / 配额接口走 `yun.baidu.com`（《抓包事实.md》§11.3 #5/#14），
        // 故一并覆盖该子域，避免只挂在 yun 子域上的 Cookie 漏采。
        NetdiskType.BAIDU -> listOf("https://pan.baidu.com", "https://yun.baidu.com")
        NetdiskType.UC -> listOf("https://drive.uc.cn")
        NetdiskType.XUNLEI -> listOf("https://pan.xunlei.com")
    }

    /**
     * 判定登录态所需的 Cookie 名列表（**全部出现**方视为已登录）。
     *
     * 语义（据《WebView登录与Cookie提取实践》§4.1 与《抓包事实.md》）：
     * 夸克 / UC 必须**同时**存在 `__puus` 与 `__pus`；百度仅需 `BDUSS`。
     * 迅雷走 `access_token`（JWT）体系、非普通 Cookie，不在本判定覆盖范围。
     *
     * @param type 网盘类型。
     * @return 必须全部命中的 Cookie 名列表；空列表表示该网盘不适用本判定。
     */
    fun loginCookieNamesOf(type: NetdiskType): List<String> = when (type) {
        NetdiskType.QUARK -> listOf(QUARK_LOGIN_COOKIE, PUS_COOKIE)
        NetdiskType.UC -> listOf(QUARK_LOGIN_COOKIE, PUS_COOKIE)
        NetdiskType.BAIDU -> listOf("BDUSS")
        // 迅雷登录态为 access_token（JWT），非 Cookie，本判定不适用（阶段 9 单独实现）。
        NetdiskType.XUNLEI -> emptyList()
    }

    /**
     * 方案 A：读取 CookieManager 中指定 URL 列表的 Cookie，并按名去重合并。
     *
     * @param cookieManager WebView 的 CookieManager。
     * @param urls 需要读取的 URL 列表（越靠后越优先覆盖同名 Cookie）。
     * @return 形如 `a=1; b=2` 的合并 Cookie 串；全空时返回空串。
     */
    fun extract(cookieManager: CookieManager, urls: List<String>): String {
        val merged = LinkedHashMap<String, String>()
        urls.forEach { url ->
            val raw = runCatching { cookieManager.getCookie(url) }.getOrElse { error ->
                Timber.e(error, "CookieExtractor getCookie failed for %s", url)
                null
            }
            parseInto(merged, raw)
        }
        return join(merged)
    }

    /**
     * 把多个 Cookie 串合并为一个（后出现的同名 Cookie 覆盖先前的）。
     *
     * @param cookieStrings 待合并的 Cookie 串（可为 null / 空）。
     * @return 合并后的 Cookie 串；全空时返回空串。
     */
    fun merge(vararg cookieStrings: String?): String {
        val merged = LinkedHashMap<String, String>()
        cookieStrings.forEach { parseInto(merged, it) }
        return join(merged)
    }

    /**
     * 廉价预检：判定给定 Cookie 串的「必需字段」是否**全部**齐全。
     *
     * 注意：本方法只做零成本的「字段齐全性」判断，**不代表登录有效**——凭证可能
     * 尚未生效或已过期。真正的登录成立须由网络校验（[LoginValidator]）判定。
     *
     * @param type 网盘类型。
     * @param cookie 合并后的 Cookie 串。
     * @return true 表示所需字段全部出现（进入网络校验的前置条件）。
     */
    fun isLoggedIn(type: NetdiskType, cookie: String): Boolean {
        val names = loginCookieNamesOf(type)
        if (names.isEmpty() || cookie.isBlank()) return false
        val present = parseNames(cookie)
        return names.all { name -> present.contains(name) }
    }

    /**
     * 把一段 Cookie 串按名值对写入目标映射（同名覆盖）。
     *
     * @param target 目标映射（就地修改）。
     * @param raw 原始 Cookie 串。
     */
    private fun parseInto(target: MutableMap<String, String>, raw: String?) {
        if (raw.isNullOrBlank()) return
        raw.split(PAIR_SEPARATOR).forEach { pair ->
            val index = pair.indexOf(NAME_VALUE_SEPARATOR)
            if (index <= 0) return@forEach
            val name = pair.substring(0, index).trim()
            val value = pair.substring(index + 1).trim()
            if (name.isNotEmpty()) {
                target[name] = value
            }
        }
    }

    /**
     * 解析 Cookie 串中出现的全部 Cookie 名。
     *
     * @param cookie Cookie 串。
     * @return Cookie 名集合。
     */
    private fun parseNames(cookie: String): Set<String> {
        val names = LinkedHashSet<String>()
        cookie.split(PAIR_SEPARATOR).forEach { pair ->
            val index = pair.indexOf(NAME_VALUE_SEPARATOR)
            if (index > 0) {
                names.add(pair.substring(0, index).trim())
            }
        }
        return names
    }

    /**
     * 把名值对映射拼回 Cookie 串。
     *
     * @param merged 已去重的名值对映射。
     * @return Cookie 串；空映射返回空串。
     */
    private fun join(merged: Map<String, String>): String =
        merged.entries.joinToString(PAIR_SEPARATOR) { (name, value) -> "$name=$value" }
}