// 文件：NetdiskParser.kt
// 职责：定义所有网盘解析器必须实现的统一接口
// 依赖：ParseResult、NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser

import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult

/**
 * 网盘分享链接解析器统一接口。
 *
 * 新增网盘时只需实现本接口并在 [ParserRegistry] 中注册，
 * 解析流程与下载引擎无须改动。
 */
interface NetdiskParser {

    /** 本解析器负责的网盘类型。 */
    val type: NetdiskType

    /**
     * 判断该解析器是否处理此链接。
     *
     * @param url 分享链接。
     * @return true 表示由本解析器处理。
     */
    fun match(url: String): Boolean

    /**
     * 解析分享链接。
     *
     * @param url 分享链接。
     * @param pwd 提取码，可为 null。
     * @return 解析结果，见 [ParseResult]。
     * @throws java.io.IOException 网络不可用或请求失败时抛出。
     * @throws kotlinx.serialization.SerializationException 响应体结构与预期不符时抛出。
     */
    suspend fun parse(url: String, pwd: String?): ParseResult
}
