// 文件：UserAgentProvider.kt
// 职责：集中管理四家网盘的 User-Agent 与 Referer 常量
// 依赖：Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

import com.jieyundu.app.domain.model.NetdiskType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 四家网盘请求头常量提供者（依据【修订 JYD-ERRATA-2026-10-03】修订三）。
 *
 * 说明：Referer 取自《要求.md》第八部分给出的各网盘域名；User-Agent 需伪装成对应
 * 网盘客户端，具体 UA 字符串须由用户抓包确认后填入（R3 / D7：不得编造）。
 */
@Singleton
class UserAgentProvider @Inject constructor() {

    /**
     * 夸克 API / 客户端 User-Agent（取链与下载链路使用）。
     *
     * 来源：《抓包事实.md》第 1 节「API / 客户端」一行。
     */
    val quarkUserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 " +
            "Electron/18.3.5.12-a038f7b798 Safari/537.36 Channel/pckk_other_ch"

    /**
     * 夸克网页 / 登录态 User-Agent（内嵌 WebView 登录页使用）。
     *
     * 来源：《抓包事实.md》第 1 节「网页 / 登录态」一行。
     * 注意：网页 UA 与 API UA **不得混用**（第 1 节明确要求）。
     */
    val quarkWebUserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 QuarkPC/6.0.8.649"

    /** 夸克 Referer。 */
    val quarkReferer: String = "https://pan.quark.cn/"

    /**
     * 百度网盘**客户端** User-Agent（`yun/api/list`、`filemanager`、`locatedownload` 用）。
     *
     * 来源：《抓包事实.md》§3「两套 UA」之②（原文 `netdisk;12.24.6;…`）。
     * 注意：百度在**同一个 host 上混用两套 UA**——按接口（路径）区分，见 `NetworkModule`。
     */
    val baiduNetdiskUserAgent: String =
        "netdisk;12.24.6;piano;android-android;16;JSbridge4.4.0;jointBridge;1.1.0"

    /**
     * 百度网盘**网页 / 登录态** User-Agent（`share/verify`、`xpan/share`、
     * `gettemplatevariable`、`filemetas` 用）。
     *
     * 来源：《抓包事实.md》§3「两套 UA」之①（Chrome 124）。
     */
    val baiduWebUserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 百度 Referer。 */
    val baiduReferer: String = "https://pan.baidu.com/"

    /**
     * UC 网盘客户端 User-Agent（登录态 API / 取链链路使用）。
     *
     * 来源：《抓包事实.md》第 2 节「三套 UA」之②「云盘客户端（登录态取链）」。
     * 说明：App 的转存 / 取链均需登录态（Cookie），故 API 请求统一使用该客户端 UA。
     */
    val ucUserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) uc-cloud-drive/1.6.1 Chrome/100.0.4896.160 " +
            "Electron/18.3.5.16-b62cf9c50d Safari/537.36 Channel/ucpan_other_ch"

    /**
     * UC 网页 / 登录态 User-Agent（内嵌 WebView 登录页使用）。
     *
     * 来源：《抓包事实.md》第 2 节「三套 UA」之①「登录态网页」。
     */
    val ucWebUserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** UC Referer（缺它直链会被限速到约 100KB/s；《抓包事实.md》§2）。 */
    val ucReferer: String = "https://drive.uc.cn/"

    // TODO(用户抓包): 填入迅雷云盘客户端 User-Agent 原文
    /** 迅雷云盘 User-Agent。 */
    val xunleiUserAgent: String = ""

    /** 迅雷 Referer。 */
    val xunleiReferer: String = "https://pan.xunlei.com/"

    /**
     * 按网盘类型取**网页 / 登录态** User-Agent（内嵌 WebView 登录页、登录校验用）。
     *
     * 存在理由（【JYD-BAIDU-COOKIE-2026-10-05】）：此前 WebView 登录页与登录校验都写死
     * 夸克 UA，导致百度登录页以夸克 UA 打开、且百度 Cookie 校验请求带错 UA。
     * 收敛到本方法作为**单一来源**，各调用方不再各自拼 UA。
     *
     * @param type 网盘类型。
     * @return 对应网页 UA；该平台尚未抓包（如迅雷）时返回空串，调用方应保持 WebView 默认 UA。
     */
    fun webUserAgentOf(type: NetdiskType): String = when (type) {
        NetdiskType.QUARK -> quarkWebUserAgent
        NetdiskType.UC -> ucWebUserAgent
        NetdiskType.BAIDU -> baiduWebUserAgent
        NetdiskType.XUNLEI -> xunleiUserAgent
    }
}