// 文件：NetdiskType.kt
// 职责：定义支持的网盘类型枚举及其展示名
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.model

/**
 * 支持的网盘类型。
 *
 * @property displayName 面向用户的展示名。
 *
 * 说明：`displayName` 为中文常量，与《要求.md》7.4 的精确签名保持一致。
 * 与编码风格 C5（.kt 不得出现中文字面量）的冲突已上报用户裁决，结论为
 * 「方案 A：保持 7.4 原样，本文件 4 个中文字面量登记为 C5 唯一豁免」。
 *
 * 补充约定（本次会话裁决）：
 * - `displayName` 仅供日志与兜底展示使用；
 * - UI 显示层必须走 strings.xml 映射，阶段 8 起不允许直接用 `displayName` 渲染文本
 *   （domain 层不得反向依赖 Android 资源系统）。
 */
enum class NetdiskType(val displayName: String) {
    BAIDU("百度网盘"),
    UC("UC网盘"),
    QUARK("夸克网盘"),
    XUNLEI("迅雷云盘")
}
