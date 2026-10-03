// 文件：UserAgentProvider.kt
// 职责：集中管理四家网盘的 User-Agent 与 Referer 常量
// 依赖：Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

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

    // TODO(用户抓包): 填入百度网盘客户端 User-Agent 原文
    /** 百度网盘 User-Agent。 */
    val baiduUserAgent: String = ""

    /** 百度 Referer。 */
    val baiduReferer: String = "https://pan.baidu.com/"

    // TODO(用户抓包): 填入 UC 网盘客户端 User-Agent 原文
    /** UC 网盘 User-Agent。 */
    val ucUserAgent: String = ""

    /** UC Referer。 */
    val ucReferer: String = "https://drive.uc.cn/"

    // TODO(用户抓包): 填入迅雷云盘客户端 User-Agent 原文
    /** 迅雷云盘 User-Agent。 */
    val xunleiUserAgent: String = ""

    /** 迅雷 Referer。 */
    val xunleiReferer: String = "https://pan.xunlei.com/"
}