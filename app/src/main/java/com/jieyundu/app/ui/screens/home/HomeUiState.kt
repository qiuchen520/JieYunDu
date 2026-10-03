// 文件：HomeUiState.kt
// 职责：首页（链接解析）的 UI 状态模型，以及网盘类型 / 错误码到 strings.xml 的唯一映射点
// 依赖：ParseResult、NetdiskType、androidx.annotation.StringRes、R
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home

import androidx.annotation.StringRes
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult

/**
 * 首页 UI 状态。
 *
 * @property inputLink 输入框中的原始文本（可包含提取码）。
 * @property isParsing 是否正在解析。
 * @property result 解析结果（domain 层模型）；尚未解析或已清空时为 null。
 * @property errorRes 本地校验类错误（未识别链接 / 不支持网盘）的文案资源；无错误时为 null。
 * @property passwordPrompt 是否应弹出「输入提取码」弹窗（阶段 8 整改）。
 * @property passwordErrorRes 弹窗内的错误提示（如提取码错误）；无错误时为 null。
 */
data class HomeUiState(
    val inputLink: String = "",
    val isParsing: Boolean = false,
    val result: ParseResult? = null,
    @StringRes val errorRes: Int? = null,
    val passwordPrompt: Boolean = false,
    @StringRes val passwordErrorRes: Int? = null
) {
    /** 输入非空且当前未在解析时，允许触发解析。 */
    val canParse: Boolean
        get() = inputLink.isNotBlank() && !isParsing
}

/**
 * 网盘类型 → UI 文案资源 id 的唯一映射点。
 *
 * 说明（C5）：domain 层的 `NetdiskType.displayName` 为中文常量，仅供日志与兜底使用；
 * UI 显示必须走本映射（阶段 2 KDoc 已约定），避免 domain 反向依赖 Android 资源系统。
 *
 * @return 对应的 strings.xml 文案资源 id。
 */
@StringRes
internal fun NetdiskType.uiLabelRes(): Int = when (this) {
    NetdiskType.BAIDU -> R.string.netdisk_baidu
    NetdiskType.UC -> R.string.netdisk_uc
    NetdiskType.QUARK -> R.string.netdisk_quark
    NetdiskType.XUNLEI -> R.string.netdisk_xunlei
}

/**
 * 解析错误码 → UI 文案资源 id 的映射。
 *
 * 说明：解析器返回的 `code` / `message` 为机器可读标识（见各解析器内的 CODE_* 常量），
 * 由 UI 层在此处统一映射为 strings.xml 文案，domain 层不依赖 Android 资源系统。
 *
 * @param code domain 层错误码。
 * @return 文案资源 id；未收录的错误码返回 [R.string.parse_code_unknown]。
 */
@StringRes
internal fun parseErrorLabelRes(code: String): Int = when (code) {
    "QUARK_INVALID_LINK", "APP_INVALID_LINK" -> R.string.parse_code_invalid_link
    "QUARK_TOKEN_FAILED" -> R.string.parse_code_token_failed
    "QUARK_DETAIL_FAILED" -> R.string.parse_code_detail_failed
    "QUARK_DOWNLOAD_FAILED" -> R.string.parse_code_download_failed
    "QUARK_WRONG_PASSWORD" -> R.string.password_error_retry
    "BAIDU_NOT_IMPLEMENTED", "UC_NOT_IMPLEMENTED", "XUNLEI_NOT_IMPLEMENTED" ->
        R.string.parse_code_not_implemented
    "QUARK_NETWORK_ERROR", "APP_NETWORK_ERROR" -> R.string.parse_code_network
    "QUARK_PROTOCOL_ERROR", "APP_PROTOCOL_ERROR" -> R.string.parse_code_protocol
    else -> R.string.parse_code_unknown
}