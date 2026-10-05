// 文件：HomeUiState.kt
// 职责：首页（链接解析）的 UI 状态模型，以及网盘类型 / 错误码到 strings.xml 的唯一映射点
// 依赖：ParseResult、NetdiskType、androidx.annotation.StringRes、R
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home

import androidx.annotation.StringRes
import com.jieyundu.app.R
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult

/**
 * 分享浏览上下文：展开子目录与转存下载所需的不可变参数。
 *
 * @property netdiskType 网盘类型。
 * @property title 分享标题（根目录展示名）。
 * @property pwdId 分享 ID。
 * @property stoken 分享临时令牌。
 */
data class ShareContext(
    val netdiskType: NetdiskType,
    val title: String,
    val pwdId: String,
    val stoken: String
)

/**
 * 分享浏览的一层（路径栈元素）。
 *
 * @property pdirFid 本层目录的 fid（根为 `0`）。
 * @property name 本层展示名（根为分享标题，子层为文件夹名）。
 * @property files 本层条目（文件夹在前、文件在后由 UI 自行处理）。
 */
data class BrowseLevel(
    val pdirFid: String,
    val name: String,
    val files: List<FileInfo>
)

/**
 * 首页 UI 状态。
 *
 * @property inputLink 输入框中的原始文本（可包含提取码）。
 * @property inputCode 单独填写的提取码（选填，布局修订）。
 * @property isParsing 是否正在解析。
 * @property result 解析结果（domain 层模型）；尚未解析或已清空时为 null。
 *   成功时同时填充 [shareContext] 与 [stack]，由 UI 优先按路径栈渲染。
 * @property errorRes 本地校验类错误（未识别链接 / 不支持网盘）的文案资源；无错误时为 null。
 * @property passwordPrompt 是否应弹出「输入提取码」弹窗（阶段 8 整改）。
 * @property passwordErrorRes 弹窗内的错误提示（如提取码错误）；无错误时为 null。
 * @property shareContext 分享浏览上下文（仅解析成功后有值）。
 * @property stack 目录路径栈；栈底为分享根目录，栈顶为当前目录。空表示未在浏览。
 * @property isLoadingDir 是否正在展开某个子目录。
 * @property dirErrorRes 展开子目录失败的文案资源；无错误时为 null。
 * @property isPreparingDownload 是否正在转存并换取直链（下载前的准备阶段）。
 * @property downloadErrorRes 下载启动失败的文案资源；无错误时为 null。
 */
data class HomeUiState(
    val inputLink: String = "",
    val inputCode: String = "",
    val isParsing: Boolean = false,
    val result: ParseResult? = null,
    @StringRes val errorRes: Int? = null,
    val passwordPrompt: Boolean = false,
    @StringRes val passwordErrorRes: Int? = null,
    val shareContext: ShareContext? = null,
    val stack: List<BrowseLevel> = emptyList(),
    val isLoadingDir: Boolean = false,
    @StringRes val dirErrorRes: Int? = null,
    val isPreparingDownload: Boolean = false,
    @StringRes val downloadErrorRes: Int? = null
) {
    /** 输入非空且当前未在解析时，允许触发解析。 */
    val canParse: Boolean
        get() = inputLink.isNotBlank() && !isParsing

    /** 当前浏览的目录层；未在浏览时为 null。 */
    val currentLevel: BrowseLevel?
        get() = stack.lastOrNull()

    /** 是否可以返回上一级（栈深大于 1）。 */
    val canNavigateUp: Boolean
        get() = stack.size > 1
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
    "QUARK_INVALID_LINK", "UC_INVALID_LINK", "BAIDU_INVALID_LINK", "APP_INVALID_LINK" ->
        R.string.parse_code_invalid_link
    "QUARK_TOKEN_FAILED", "UC_TOKEN_FAILED" -> R.string.parse_code_token_failed
    "QUARK_DETAIL_FAILED", "UC_DETAIL_FAILED", "BAIDU_DETAIL_FAILED" ->
        R.string.parse_code_detail_failed
    "QUARK_DOWNLOAD_FAILED" -> R.string.parse_code_download_failed
    "QUARK_TRANSFER_FAILED" -> R.string.parse_code_transfer_failed
    // 提取码错误在两家的机器码不同（QUARK_/UC_），UI 统一映射为「重试」提示。
    "QUARK_WRONG_PASSWORD", "UC_WRONG_PASSWORD", "BAIDU_WRONG_PASSWORD" ->
        R.string.password_error_retry
    // 百度 errno 语义（JYD-BAIDU-ERRNO-2026-10-05）：-12=提取码错误（上一行复用），
    // -6=需要提取码或登录，其它非 -12 的 verify 失败，以及 errno=2 的子目录认证失败。
    "BAIDU_VERIFY_FAILED" -> R.string.parse_code_baidu_verify_failed
    "BAIDU_NEED_PASSWORD_OR_LOGIN" -> R.string.parse_code_baidu_need_password_or_login
    "BAIDU_SUB_DIR_AUTH_FAILED" -> R.string.parse_code_baidu_sub_dir_auth_failed
    "BAIDU_SHARE_EXPIRED" -> R.string.parse_code_share_expired
    "BAIDU_FILE_NOT_FOUND" -> R.string.parse_code_file_not_found
    "BAIDU_NOT_IMPLEMENTED", "XUNLEI_NOT_IMPLEMENTED" ->
        R.string.parse_code_not_implemented
    "QUARK_NETWORK_ERROR", "UC_NETWORK_ERROR", "BAIDU_NETWORK_ERROR", "APP_NETWORK_ERROR" ->
        R.string.parse_code_network
    "QUARK_PROTOCOL_ERROR", "UC_PROTOCOL_ERROR", "BAIDU_PROTOCOL_ERROR", "APP_PROTOCOL_ERROR" ->
        R.string.parse_code_protocol
    else -> R.string.parse_code_unknown
}