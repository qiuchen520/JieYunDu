// 文件：XunleiParser.kt
// 职责：迅雷云盘分享链接解析器（阶段 9 占位骨架，真实实现待用户抓包后补全）
// 依赖：NetdiskParser、LinkExtractor、NetdiskType、ParseResult
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.util.LinkExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 迅雷云盘解析器（占位骨架）。
 *
 * 说明（《要求.md》第十一部分 · 阶段 9）：
 * - 本阶段**仅交付接口骨架与占位**，不实现真实解析流程；
 * - 所有涉及真实接口、参数、字段名、风控规则的位置一律以 `TODO(用户抓包):` 标注，
 *   等待抓包数据填入（铁律 R3）；
 * - 解析统一走 [LinkExtractor.detectType] 判定域名，避免各处重复维护域名列表。
 *
 * @see NetdiskParser
 */
@Singleton
class XunleiParser @Inject constructor() : NetdiskParser {

    override val type: NetdiskType = NetdiskType.XUNLEI

    /**
     * 迅雷云盘分享链接判定：交由统一的域名正则。
     *
     * @param url 分享链接。
     * @return 是否属于迅雷云盘。
     */
    override fun match(url: String): Boolean =
        LinkExtractor.detectType(url) == NetdiskType.XUNLEI

    /**
     * 解析迅雷云盘分享链接。
     *
     * ⚠️ 阶段 9 仅交付占位骨架：真实流程（分享信息接口 → 文件列表 → 直链，
     * 以及提取码校验）待用户抓包后补全（铁律 R3）。
     *
     * @param url 分享链接。
     * @param pwd 提取码，可为 null。
     * @return 固定返回 [ParseResult.Error]（[CODE_NOT_IMPLEMENTED]），由 UI 映射为中性文案。
     */
    override suspend fun parse(url: String, pwd: String?): ParseResult =
        // TODO(用户抓包): 补全迅雷云盘解析流程（分享信息 → 文件列表 → 直链）
        ParseResult.Error(type, CODE_NOT_IMPLEMENTED, CODE_NOT_IMPLEMENTED)

    private companion object {
        /** 占位错误码：该网盘解析尚未实现（UI 侧映射为「开发中」文案）。 */
        const val CODE_NOT_IMPLEMENTED = "XUNLEI_NOT_IMPLEMENTED"
    }
}