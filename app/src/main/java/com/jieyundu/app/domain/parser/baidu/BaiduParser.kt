// 文件：BaiduParser.kt
// 职责：百度网盘分享链接解析与分享目录展开（B3-1，含 errno 两阶段判定）
// 依赖：BaiduApi、BaiduErrnoRules、LinkExtractor、FileInfo、ParseResult、ShareBrowser、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.baidu

import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.ShareBrowser
import com.jieyundu.app.domain.util.LinkExtractor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 百度网盘解析器（《要求.md》阶段 B3；B3-1：分享解析与浏览）。
 *
 * 已实现（依《抓包事实.md》§1 / §3 / §6.2 / §11.3 与 Owner 补充的 errno 语义）：
 * - 分享短码归一化（`LinkExtractor` 负责去掉百度 `/s/1xxxx` 的前导 `1`）；
 * - 提取码校验 `share/verify` → `randsk`（当 `sekey` 用；其 `Set-Cookie: BDCLND` 由网络层采集）；
 * - 分享文件列表 `xpan/share?method=list`（顶层 `root=1`）；
 * - 分享目录逐级展开（子目录 `root=0` + `dir=<完整路径>`，路径即目录 id）；
 * - **两阶段 errno 判定**（见 [BaiduErrnoRules]）：`-12` 提取码错误、`-6` 需要提取码或登录、
 *   `2` 子目录认证失败（**不是**提取码）、`403` 分享失效、`31066` 文件不存在；
 *   「需要提取码」是**组合判定**（无 `sekey` 且列分享失败），没有专属 errno。
 *
 * 失败时优先**透传服务端消息**（`err_msg` / `show_msg`），并附 `errno`，形如
 * `服务端原文 (errno=-12)`；UI 侧另起一行展示该原文，便于用户看到精确原因。
 *
 * 尚未实现（**B3-2**）：转存 `share/transfer` → 高速直链 `locatedownload` → 下载 → 清理。
 * 故本批解析出的文件**尚未带直链**（[FileInfo.downloadUrl] 为 null），这是有意的分批。
 */
@Singleton
class BaiduParser @Inject constructor(
    private val api: BaiduApi
) : NetdiskParser, ShareBrowser {

    override val type: NetdiskType = NetdiskType.BAIDU

    /**
     * 百度网盘分享链接判定：交由统一的域名正则。
     *
     * @param url 分享链接。
     * @return 是否属于百度网盘。
     */
    override fun match(url: String): Boolean =
        LinkExtractor.detectType(url) == NetdiskType.BAIDU

    /**
     * 解析百度网盘分享链接。
     *
     * @param url 分享链接。
     * @param pwd 提取码，可为 null（公共分享无需提取码，会跳过 verify 直接列分享）。
     * @return 解析结果（成功 / 需要提取码 / 失败）。
     */
    override suspend fun parse(url: String, pwd: String?): ParseResult {
        val surl = LinkExtractor.extractShareId(url)
        if (surl.isBlank()) {
            return ParseResult.Error(
                type,
                BaiduErrnoRules.CODE_INVALID_LINK,
                BaiduErrnoRules.CODE_INVALID_LINK
            )
        }
        return withContext(Dispatchers.IO) {
            try {
                // 阶段一：有提取码才校验（公共分享跳过 verify，sekey 置空——与抓包实证一致）。
                val sekey: String? = if (!pwd.isNullOrBlank()) {
                    when (val outcome = verifyShare(surl, pwd)) {
                        is VerifyOutcome.Success -> outcome.sekey
                        is VerifyOutcome.Failure -> return@withContext outcome.error
                    }
                } else {
                    null
                }

                // 阶段二：列分享（顶层 root=1）。
                val listed = api.listShareFiles(
                    shorturl = surl,
                    root = BaiduApi.ROOT_TOP_LEVEL,
                    sekey = sekey
                )
                val failure = BaiduErrnoRules.classifyList(
                    errno = listed.errno,
                    hasSekey = !sekey.isNullOrBlank(),
                    isSubDirectory = false
                )
                when (failure) {
                    null -> Unit
                    // 组合判定得出的「需要提取码」→ 交 UI 弹提取码输入（不当作错误）。
                    BaiduErrnoRules.CODE_NEED_PASSWORD -> return@withContext ParseResult.NeedPassword(type)
                    else -> return@withContext ParseResult.Error(
                        type,
                        failure,
                        serverDetail(listed.errno, listed.err_msg, listed.show_msg)
                    )
                }

                ParseResult.Success(
                    netdiskType = type,
                    shareTitle = listed.title.orEmpty(),
                    files = listed.list.map { entry -> entry.toFileInfo() },
                    pwdId = surl,
                    // 百度无独立 stoken：提取码换来的 randsk（即 sekey）承担该角色。
                    stoken = sekey.orEmpty()
                )
            } catch (cancellation: CancellationException) {
                // C3：协程取消必须原样抛出，不得吞掉
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "BaiduParser parse failed: network error")
                ParseResult.Error(type, CODE_NETWORK, CODE_NETWORK)
            } catch (serialization: SerializationException) {
                Timber.e(serialization, "BaiduParser parse failed: unexpected response body")
                ParseResult.Error(type, CODE_PROTOCOL, CODE_PROTOCOL)
            }
        }
    }

    /**
     * 校验提取码并取回 `sekey`（`randsk`）。
     *
     * 说明：结果以 [VerifyOutcome] **局部返回**，不使用任何实例/伴生状态——
     * 解析器是 `@Singleton`，若把失败结果暂存在成员里，两次并发解析会互相串结果。
     *
     * @param surl 分享短码。
     * @param pwd 提取码。
     * @return 成功（含 `sekey`）或失败（含已组装的错误结果）。
     */
    private suspend fun verifyShare(surl: String, pwd: String): VerifyOutcome {
        val verify = api.verifyShare(
            surl = surl,
            pwd = pwd,
            referer = buildShareReferer(surl)
        )
        val detail = serverDetail(verify.errno, verify.err_msg, verify.show_msg)
        val failure = BaiduErrnoRules.classifyVerify(verify.errno)
        if (failure != null) {
            Timber.w("BaiduParser verify failed, errno=%d", verify.errno)
            return VerifyOutcome.Failure(ParseResult.Error(type, failure, detail))
        }
        val randsk = verify.randsk?.takeIf { value -> value.isNotBlank() }
        if (randsk == null) {
            // errno==0 却没拿到 randsk：响应结构异常（协议层问题），不是提取码错误。
            Timber.w("BaiduParser verify ok but randsk missing")
            return VerifyOutcome.Failure(
                ParseResult.Error(type, BaiduErrnoRules.CODE_VERIFY_FAILED, detail)
            )
        }
        return VerifyOutcome.Success(randsk)
    }

    /**
     * 提取码校验结果（局部类型，避免在单例上暂存状态）。
     */
    private sealed interface VerifyOutcome {

        /** 校验通过。 */
        data class Success(val sekey: String) : VerifyOutcome

        /** 校验失败（已组装好对外错误）。 */
        data class Failure(val error: ParseResult.Error) : VerifyOutcome
    }

    /**
     * 展开分享内的某个目录（子目录 `root=0` + `dir=<完整路径>`）。
     *
     * 说明：百度以**路径**作为目录 id。失败时记录**分类后的**原因（含 `errno=2` 的
     * 「子目录认证失败」语义，避免被误读为提取码问题），并返回空列表。
     *
     * @param pwdId 分享短码。
     * @param stoken 提取码换取的 `randsk`（无提取码时为空串）。
     * @param pdirFid 目录完整路径。
     * @return 该目录下的条目列表；失败返回空列表。
     */
    override suspend fun listChildren(
        pwdId: String,
        stoken: String,
        pdirFid: String
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        val isRoot = pdirFid.isBlank() || pdirFid == ROOT_DIR_PATH
        val listed = api.listShareFiles(
            shorturl = pwdId,
            root = if (isRoot) BaiduApi.ROOT_TOP_LEVEL else BaiduApi.ROOT_SUB_DIRECTORY,
            dir = if (isRoot) null else pdirFid,
            sekey = stoken.takeIf { value -> value.isNotBlank() }
        )
        val failure = BaiduErrnoRules.classifyList(
            errno = listed.errno,
            hasSekey = stoken.isNotBlank(),
            isSubDirectory = !isRoot
        )
        if (failure != null) {
            Timber.w(
                "BaiduParser listChildren failed, reason=%s errno=%d dir=%s",
                failure,
                listed.errno,
                pdirFid
            )
            return@withContext emptyList()
        }
        listed.list.map { entry -> entry.toFileInfo() }
    }

    /**
     * 组装失败详情：优先服务端消息，并附 `errno`。
     *
     * 说明：括号用 ASCII（`(errno=N)`）而非全角，以符合编码风格 C5
     * （`.kt` 源码不得出现中文字面量）。
     *
     * @param errno 服务端 errno。
     * @param messages 候选服务端消息（`err_msg` / `show_msg`），按优先级排列。
     * @return 详情文本。
     */
    private fun serverDetail(errno: Int, vararg messages: String?): String {
        val serverText = messages.firstOrNull { value -> !value.isNullOrBlank() }
        return if (serverText != null) "$serverText (errno=$errno)" else "errno=$errno"
    }

    /**
     * 构造分享页 Referer（提取码校验接口要求带分享短码）。
     *
     * @param surl 分享短码。
     * @return 形如 `https://pan.baidu.com/s/<短码>`。
     */
    private fun buildShareReferer(surl: String): String = "$SHARE_PAGE_PREFIX$surl"

    /**
     * 把百度列表条目映射为统一模型。
     *
     * 说明（《抓包事实.md》§11.3 #3）：**目录用 `path` 当 id、文件用 `fs_id`**；
     * 本批尚未接转存取链，`downloadUrl` 一律为 null（B3-2 补齐）。
     *
     * @return 统一文件条目。
     */
    private fun BaiduShareFile.toFileInfo(): FileInfo {
        val directory = isDirectory
        return FileInfo(
            fid = if (directory) path else fs_id?.toString().orEmpty(),
            fileName = server_filename,
            fileSize = if (directory) 0L else size,
            isDirectory = directory,
            downloadUrl = null
        )
    }

    private companion object {
        /** 分享页 URL 前缀（构造 Referer 用）。 */
        const val SHARE_PAGE_PREFIX = "https://pan.baidu.com/s/"

        /** 根目录路径（百度以 `/` 表示根）。 */
        const val ROOT_DIR_PATH = "/"

        /** 网络异常。 */
        const val CODE_NETWORK = "BAIDU_NETWORK_ERROR"

        /** 响应结构异常。 */
        const val CODE_PROTOCOL = "BAIDU_PROTOCOL_ERROR"
    }
}
