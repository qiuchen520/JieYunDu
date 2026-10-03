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

    // TODO(用户抓包): 填入夸克 PC 客户端 User-Agent 原文
    /** 夸克 PC 客户端 User-Agent。 */
    val quarkUserAgent: String = ""

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
