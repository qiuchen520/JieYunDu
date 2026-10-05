// 文件：BaiduErrnoRules.kt
// 职责：百度分享链路的 errno 判定规则（两阶段：verify / listShare）
// 依赖：无（纯逻辑，便于单测）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.baidu

/**
 * 百度分享链路的 `errno` 判定规则（阶段 B3-1 修正批）。
 *
 * 存在理由：百度的失败语义**不是单一 errno**，必须分两个阶段看，这是最容易踩混的点：
 *
 * | 场景 | 判定 | 出现阶段 |
 * |---|---|---|
 * | 提取码错误 | `errno == -12`（专属码） | `share/verify` |
 * | 需要提取码 | **组合判定**：`sekey` 为空 且 `errno != 0`（无专属码） | `xpan/share?method=list` |
 * | 未登录 / 身份验证失败 | `errno == -6`（需与「需要提取码」并列提示，避免让用户白找密码） | `listShare` |
 * | 缺 `BDCLND` / 子目录认证失败 | `errno == 2`（**不是**提取码问题） | 子目录 `listShare`、`transfer`、`mkdir` |
 * | 分享已失效 | `errno == 403` | 通用 |
 * | 文件不存在 | `errno == 31066` | 通用 |
 *
 * 特别提醒：`errno == 2` 极易被误判成「需要提取码」——它实际是「子目录列表没带
 * `BDCLND=<randsk>`」或「建目录误走 `filemanager?opera=mkdir`」。
 *
 * 说明：本对象只做**纯判定**（无网络、无 Android 依赖），返回机器可读的 code，
 * 由解析器转成 [com.jieyundu.app.domain.model.ParseResult]，UI 再映射为文案（C5）。
 */
internal object BaiduErrnoRules {

    /** 成功。 */
    const val OK = 0

    /** 提取码错误（`share/verify` 专属码）。 */
    const val WRONG_PASSWORD = -12

    /** 未登录 / 身份验证失败（游客态常见）。 */
    const val NOT_LOGGED_IN = -6

    /** 缺 `BDCLND` cookie / 子目录认证失败（**不是**提取码问题）。 */
    const val MISSING_BDCLND = 2

    /** 分享已失效。 */
    const val SHARE_EXPIRED = 403

    /** 文件不存在。 */
    const val FILE_NOT_FOUND = 31066

    /** 链接无法识别。 */
    const val CODE_INVALID_LINK = "BAIDU_INVALID_LINK"

    /** 提取码错误。 */
    const val CODE_WRONG_PASSWORD = "BAIDU_WRONG_PASSWORD"

    /** 提取码校验失败（非 -12 的失败）。 */
    const val CODE_VERIFY_FAILED = "BAIDU_VERIFY_FAILED"

    /** 该分享需要提取码。 */
    const val CODE_NEED_PASSWORD = "BAIDU_NEED_PASSWORD"

    /** 该分享需要提取码，**或**需要登录（`errno == -6`）。 */
    const val CODE_NEED_PASSWORD_OR_LOGIN = "BAIDU_NEED_PASSWORD_OR_LOGIN"

    /** 子目录认证失败（缺 `BDCLND`，`errno == 2`）——不要提示提取码。 */
    const val CODE_SUB_DIR_AUTH_FAILED = "BAIDU_SUB_DIR_AUTH_FAILED"

    /** 分享已失效（`errno == 403`）。 */
    const val CODE_SHARE_EXPIRED = "BAIDU_SHARE_EXPIRED"

    /** 文件不存在（`errno == 31066`）。 */
    const val CODE_FILE_NOT_FOUND = "BAIDU_FILE_NOT_FOUND"

    /** 分享列表获取失败（其他 errno）。 */
    const val CODE_DETAIL_FAILED = "BAIDU_DETAIL_FAILED"

    /**
     * 判定 `share/verify` 阶段结果。
     *
     * @param errno 服务端 errno。
     * @return 失败时返回对应 code；成功返回 null。
     */
    fun classifyVerify(errno: Int): String? = when (errno) {
        OK -> null
        WRONG_PASSWORD -> CODE_WRONG_PASSWORD
        SHARE_EXPIRED -> CODE_SHARE_EXPIRED
        FILE_NOT_FOUND -> CODE_FILE_NOT_FOUND
        else -> CODE_VERIFY_FAILED
    }

    /**
     * 判定 `xpan/share?method=list` 阶段结果（合规的**组合判定**在这里）。
     *
     * @param errno 服务端 errno。
     * @param hasSekey 本次是否携带 `sekey`（即是否已过提取码校验）。
     * @param isSubDirectory 是否在列**子目录**（`root=0`）。
     * @return 失败时返回对应 code；成功返回 null。
     */
    fun classifyList(errno: Int, hasSekey: Boolean, isSubDirectory: Boolean): String? = when {
        errno == OK -> null
        // 子目录认证失败优先判定：errno=2 与提取码无关，别提示用户去找密码。
        errno == MISSING_BDCLND && isSubDirectory -> CODE_SUB_DIR_AUTH_FAILED
        errno == SHARE_EXPIRED -> CODE_SHARE_EXPIRED
        errno == FILE_NOT_FOUND -> CODE_FILE_NOT_FOUND
        // 「需要提取码」= 无 sekey + 列分享失败（组合判定，无专属码）。
        !hasSekey && errno == NOT_LOGGED_IN -> CODE_NEED_PASSWORD_OR_LOGIN
        !hasSekey -> CODE_NEED_PASSWORD
        else -> CODE_DETAIL_FAILED
    }
}
