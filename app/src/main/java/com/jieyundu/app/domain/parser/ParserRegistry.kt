// 文件：ParserRegistry.kt
// 职责：解析器注册表，按链接或网盘类型路由到对应解析器
// 依赖：NetdiskParser、NetdiskType
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser

import com.jieyundu.app.domain.model.NetdiskType

/**
 * 解析器注册表。
 *
 * 设计说明：
 * - 构造参数为普通 [List]，不在此处标注 DI 注解，避免阶段 2 尚未注册任何解析器时
 *   Dagger 依赖图不完整；阶段 3 起由 Hilt 模块以 `@Provides` 注入具体列表。
 * - 查找顺序与列表顺序一致，匹配即返回，保证行为可预测。
 *
 * @param parsers 全部可用解析器。
 */
class ParserRegistry(
    private val parsers: List<NetdiskParser>
) {

    /**
     * 按链接查找可处理的解析器。
     *
     * @param url 分享链接。
     * @return 匹配到的解析器，全部不匹配时返回 null。
     */
    fun findParser(url: String): NetdiskParser? =
        parsers.firstOrNull { parser -> parser.match(url) }

    /**
     * 按网盘类型查找解析器。
     *
     * @param type 网盘类型。
     * @return 匹配到的解析器，未注册时返回 null。
     */
    fun findParser(type: NetdiskType): NetdiskParser? =
        parsers.firstOrNull { parser -> parser.type == type }

    /**
     * 当前已注册的网盘类型列表（用于设置页展示支持范围）。
     *
     * @return 去重后的网盘类型列表。
     */
    fun registeredTypes(): List<NetdiskType> =
        parsers.map { parser -> parser.type }.distinct()
}
