// 文件：UcParser.kt
// 职责：UC 网盘分享链接解析器——解析分享根目录 / 按需展开子目录 / 个人网盘浏览（照夸克同构）
// 依赖：UcApi、ShareBrowser、PersonalBrowser、NetdiskParser、LinkExtractor、CookieStore、OkHttpClient、Timber
// 协议：AGPL-3.0
package com.jieyundu.app.domain.parser.uc

import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.model.QuotaInfo
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.PersonalBrowser
import com.jieyundu.app.domain.parser.PersonalListQuery
import com.jieyundu.app.domain.parser.ShareBrowser
import com.jieyundu.app.domain.parser.uc.UcDeleteRequest
import com.jieyundu.app.domain.util.LinkExtractor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * UC 网盘解析器（浏览职责）。
 *
 * 流程（照 [com.jieyundu.app.domain.parser.quark.QuarkParser] 同构，参数/域名对齐
 * 《抓包事实.md》§2 / §6.1）：
 * 1. best-effort 请求 `https://drive.uc.cn/` 首页取 `__pus` / `__puus`（合并登记）；
 * 2. 从分享链接提取 pwd_id（[LinkExtractor.extractShareId]）；
 * 3. 调 token 接口换 stoken（**携带 `share_for_transfer`，UC 与夸克字段不同，不可抄混**）；
 * 4. 调 `transfer_share/detail`（GET，带 stoken）取根目录文件列表；该接口**同时**返回
 *    `share_fid_token`（取链令牌），故浏览与取链同源，`fid` 不会漂移（《参考实现_源码通读研究.md》§9.3①）。
 *    响应兼容 `detail_info.list` / `list` / `file_list`；
 * 5. 点文件夹时，以该文件夹 fid 为 `pdir_fid` 再调同一接口取子目录（[listChildren]）。
 *
 * 转存 + 轮询 + 取直链不在解析阶段（由 [com.jieyundu.app.domain.transfer.UcShareTransfer] 在点下载时执行）。
 *
 * 容量（[fetchQuota]）：UC 与夸克同构，走 `1/clouddrive/member`（《抓包事实.md》§10.1）——
 * 原「文档未给出端点、返回 null」的占位说明已随 B2 落地修正。
 *
 * 提取码判定（《评审清单.md》§12）：夸克 / UC **不使用「提取码」专用数字业务码**，
 * 失败以 `status` / `code` + `message` 表达；本类改按「本次是否携带提取码」归类（见 [parse]），
 * 原 `NEED_PASSWORD_CODE` / `WRONG_PASSWORD_CODE`（41011 / 41012）自造占位已移除。
 *
 * 线程约束：网络与 IO 全部运行在 [Dispatchers.IO]（编码风格 C9）。
 *
 * @param api UC 接口（BaseUrl 与固定 Header 见 di/NetworkModule）。
 * @param okHttpClient 复用的 OkHttp 客户端，用于第 1 步 best-effort 取 Cookie（超时见 C4）。
 * @param cookieStore 内存态 Cookie 仓库，用于登记 `__pus`/`__puus`。
 */
@Singleton
class UcParser @Inject constructor(
    private val api: UcApi,
    private val okHttpClient: OkHttpClient,
    private val cookieStore: CookieStore
) : NetdiskParser, ShareBrowser, PersonalBrowser {
    override val type: NetdiskType = NetdiskType.UC

    /**
     * UC 分享链接判定：交由统一的域名正则。
     *
     * @param url 分享链接。
     * @return 是否属于 UC 网盘。
     */
    override fun match(url: String): Boolean =
        LinkExtractor.detectType(url) == NetdiskType.UC

    /**
     * 解析 UC 分享链接（仅根目录一层）。
     *
     * @param url 分享链接。
     * @param pwd 提取码；为 null / 空白时按「无提取码」直接尝试，仅当服务器明确要求时才返回
     *   [ParseResult.NeedPassword]。
     * @return 解析结果。
     * @throws IOException 网络不可用或请求失败。
     * @throws SerializationException 响应体结构与预期不符。
     */
    override suspend fun parse(url: String, pwd: String?): ParseResult {
        val pwdId = LinkExtractor.extractShareId(url)
        if (pwdId.isBlank()) {
            return ParseResult.Error(type, CODE_INVALID_LINK, CODE_INVALID_LINK)
        }
        return withContext(Dispatchers.IO) {
            try {
                // 第 1 步：best-effort 取 __pus / __puus。失败仅告警、不中断流程。
                registerHandshakeCookies()
                // 第 2 步：换 stoken（UC 携带 share_for_transfer）。
                val tokenResponse = api.getShareToken(buildTokenBody(pwdId, pwd))
                if (tokenResponse.code != SUCCESS_CODE) {
                    // 《评审清单.md》§12：夸克 / UC **不使用「提取码」专用数字业务码**，
                    // 失败一律以 `status` / `code` + `message` 表达（《抓包事实.md》§6.1①）。
                    // 原 `NEED_PASSWORD_CODE` / `WRONG_PASSWORD_CODE`（41011 / 41012）为自造
                    // 占位，已废弃。domain 层不得内嵌中文（C5），无法按 `message` 文案匹配，
                    // 故改按**请求上下文**归类：本次未携带提取码 → 提示输入；已携带 → 判为提取码错误。
                    // 精确区分（如「分享已失效」）需对接独立提取码校验接口，标 TODO(用户抓包)。
                    Timber.w("UcParser token failed, code=%d", tokenResponse.code)
                    return@withContext if (pwd.isNullOrBlank()) {
                        ParseResult.NeedPassword(type)
                    } else {
                        ParseResult.Error(type, CODE_WRONG_PASSWORD, CODE_WRONG_PASSWORD)
                    }
                }
                val tokenData = tokenResponse.data
                val stoken = tokenData?.stoken.orEmpty()
                if (stoken.isBlank()) {
                    Timber.w("UcParser token data missing, code=%d", tokenResponse.code)
                    return@withContext ParseResult.Error(
                        type,
                        CODE_TOKEN_FAILED,
                        CODE_TOKEN_FAILED
                    )
                }
                // 第 3 步：取根目录文件列表（真实结构 data.detail_info.list，兼容 data.list）。
                val entries = fetchEntries(pwdId, stoken, pwd.orEmpty(), ROOT_PDIR_FID)
                    ?: return@withContext ParseResult.Error(
                        type,
                        CODE_DETAIL_FAILED,
                        CODE_DETAIL_FAILED
                    )
                ParseResult.Success(
                    netdiskType = type,
                    shareTitle = tokenData?.title.orEmpty(),
                    files = entries.map { entry -> entry.toFileInfo() },
                    pwdId = pwdId,
                    stoken = stoken
                )
            } catch (cancellation: CancellationException) {
                // C3：协程取消必须原样抛出，不得吞掉
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "UcParser parse failed: network error")
                ParseResult.Error(type, CODE_NETWORK, CODE_NETWORK)
            } catch (serialization: SerializationException) {
                Timber.e(serialization, "UcParser parse failed: unexpected response body")
                ParseResult.Error(type, CODE_PROTOCOL, CODE_PROTOCOL)
            }
        }
    }

    /**
     * 列出分享内指定目录的直接子项（点文件夹展开用）。
     *
     * 说明：浏览阶段拿不到 passcode（[ShareBrowser] 接口签名不含），`transfer_share/detail`
     * 允许 passcode 为空串；带密码的分享在 token 阶段已校验通过，后续列表无需再次带码。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param pdirFid 目标目录 fid。
     * @return 该目录下的条目列表；请求失败返回空列表。
     */
    override suspend fun listChildren(
        pwdId: String,
        stoken: String,
        pdirFid: String
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        fetchEntries(pwdId, stoken, EMPTY_PASSCODE, pdirFid)
            ?.map { entry -> entry.toFileInfo() }
            .orEmpty()
    }

    /**
     * 拉取分享内某目录的条目。
     *
     * @param pwdId 分享 ID。
     * @param stoken 分享临时令牌。
     * @param passcode 提取码；无则空串。
     * @param pdirFid 目标目录 fid。
     * @return 条目列表；服务端返回非成功码时返回 null。
     */
    private suspend fun fetchEntries(
        pwdId: String,
        stoken: String,
        passcode: String,
        pdirFid: String
    ): List<UcFile>? {
        // 列表走 `transfer_share/detail`（与取链令牌同源）。依据《参考实现_源码通读研究.md》
        // §9.3①：UC 分享的「文件列表」与「share_fid_token」同源于本接口；若浏览改用 v2/detail，
        // 其 fid 与 transfer_share/detail 的条目可能对不上，取链时按 fid 匹配令牌会落空。
        val detailResponse = api.transferShareDetail(
            UcTransferDetailQuery.build(pwdId, stoken, pdirFid, passcode)
        )
        if (detailResponse.code != SUCCESS_CODE) {
            Timber.w("UcParser transfer-detail code=%d pdir=%s", detailResponse.code, pdirFid)
            return null
        }
        return detailResponse.data?.entries ?: emptyList()
    }

    /**
     * 列出**个人网盘**指定目录的直接子项（流程 B：网盘管理）。
     *
     * 依据：《抓包事实.md》§2——UC 走 `1/clouddrive/file`。
     *
     * @param pdirFid 目标目录 fid；根目录为 `0`。
     * @return 该目录下的条目列表；失败返回空列表。
     */
    override suspend fun listPersonalChildren(pdirFid: String): List<FileInfo> =
        withContext(Dispatchers.IO) {
            val response = api.listFiles(buildPersonalListParams(pdirFid))
            if (response.code != SUCCESS_CODE) {
                Timber.w("UcParser personal list code=%d pdir=%s", response.code, pdirFid)
                return@withContext emptyList()
            }
            response.data?.list.orEmpty().map { entry -> entry.toFileInfo() }
        }

    /**
     * 查询**个人网盘**容量（流程 B：网盘管理头部）。
     *
     * 依据：《抓包事实.md》§10.1——UC 与夸克同构，走 `1/clouddrive/member`。
     *
     * @return 容量信息；失败返回 null。
     */
    override suspend fun fetchQuota(): QuotaInfo? = withContext(Dispatchers.IO) {
        val response = api.getMember(buildMemberParams())
        if (response.code != SUCCESS_CODE) {
            Timber.w("UcParser member code=%d", response.code)
            return@withContext null
        }
        val member = response.data ?: return@withContext null
        QuotaInfo(used = member.use_capacity, total = member.total_capacity)
    }

    /**
     * 删除个人网盘中的文件 / 目录（网盘管理页「删除」，§10.3 `file/delete`）。
     *
     * 依据：《抓包事实.md》§10.3——UC 与夸克同构（UC 的路径不带 `uc_param_str=`）。
     * 语义同为**移入回收站**（`action_type = 2`），可在网盘回收站恢复。
     *
     * 安全判定不在此层：UI 先经用户确认，再由 `TempFolderGuard.mayDeleteUserInitiated` 放行。
     *
     * @param fid 目标文件 / 目录 fid。
     * @return true 表示服务端接受删除请求。
     */
    override suspend fun deletePersonalFile(fid: String): Boolean = withContext(Dispatchers.IO) {
        if (fid.isBlank()) {
            return@withContext false
        }
        val response = runCatching {
            api.deleteFiles(UcDeleteRequest(filelist = listOf(fid)))
        }.getOrElse { error ->
            Timber.w(error, "UcParser personal delete failed fid=%s", fid)
            return@withContext false
        }
        if (response.code != SUCCESS_CODE) {
            Timber.w("UcParser personal delete code=%d fid=%s", response.code, fid)
            return@withContext false
        }
        Timber.i("UcParser personal delete accepted fid=%s", fid)
        true
    }

    /**
     * 取个人网盘文件的直链——**当前不支持，明确返回 null**（R3：不编造接口参数）。
     *
     * 原因：UC 的 `file/download`（[UcApi.getDownloadUrl]）请求体强制要求
     * `pwd_id` / `stoken` / `fids_token` 三个**分享态**字段（见 [UcDownloadRequest]），
     * 而个人网盘文件没有分享上下文；个人文件的取链报文至今**没有抓包依据**，
     * 因此这里不猜测参数、也不复用分享请求体，直接返回 null，由 UI 明确提示用户。
     *
     * 后续若拿到 UC 个人文件取链的抓包事实，只需实现本方法即可打通（UI 无需改动）。
     *
     * @param fid 目标文件 fid。
     * @return 恒为 null（待抓包事实补齐后实现）。
     */
    override suspend fun fetchPersonalDownloadUrl(fid: String): String? {
        Timber.i("UcParser personal download url unsupported (no capture evidence) fid=%s", fid)
        return null
    }

    /**
     * 构造个人网盘列表查询参数（《抓包事实》§2）。
     *
     * 写法约定：`pr` / `fr` 由本方法提供，[UcApi.listFiles] 路径里不再写死——与
     * `detail` / `save` 统一，「固定参数」只有一处来源。
     *
     * @param pdirFid 目标目录 fid。
     * @return 查询参数键值对。
     */
    private fun buildPersonalListParams(pdirFid: String): Map<String, String> =
        PersonalListQuery.build(
            pr = PersonalListQuery.UC_PR,
            fr = PersonalListQuery.UC_FR,
            pdirFid = pdirFid
        )

    /**
     * 构造容量查询参数（《抓包事实.md》§10.1）。
     *
     * @return 查询参数键值对。
     */
    private fun buildMemberParams(): Map<String, String> = mapOf(
        KEY_PR to PersonalListQuery.UC_PR,
        KEY_FR to PersonalListQuery.UC_FR,
        KEY_FETCH_SUBSCRIBE to TRUE_VALUE,
        KEY_CH to HOME_CHANNEL
    )

    /**
     * 第 1 步（best-effort）：访问 UC 首页并合并取出 `__pus` 与 `__puus`。
     *
     * 实测：token 与 detail 接口无需任何 Cookie，故本步骤任何失败都不影响解析，
     * 仅在取到时登记以提升后续（download / CDN）链路的成功率。
     */
    private fun registerHandshakeCookies() {
        val request = Request.Builder()
            .url(UC_HOME_URL)
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w(
                        "UcParser home request failed with code %d (cookies optional)",
                        response.code
                    )
                    return
                }
                val cookie = response.headers(HEADER_SET_COOKIE)
                    .mapNotNull { raw -> raw.substringBefore(';').trim() }
                    .filter { pair ->
                        pair.startsWith("$PUS_COOKIE_NAME=") ||
                            pair.startsWith("$PUUS_COOKIE_NAME=") ||
                            pair.startsWith("$PUGS_COOKIE_NAME=")
                    }
                    .joinToString(COOKIE_SEPARATOR)
                if (cookie.isNotBlank()) {
                    cookieStore.save(UC_COOKIE_HOST_SUFFIX, cookie)
                }
            }
        } catch (io: IOException) {
            Timber.w(io, "UcParser home request failed (cookies optional)")
        }
    }

    /**
     * 构造 token 接口请求体。
     *
     * 说明：
     * - 无提取码时**不带** `passcode`，交由服务器判定；
     * - 恒定携带 `share_for_transfer=true`（**UC 特有字段**，对应夸克的
     *   `support_visit_limit_private_share`，不可互抄；《抓包事实.md》§6.1①）。
     *
     * @param pwdId 分享 ID。
     * @param passcode 提取码；为 null / 空白表示链接未携带提取码。
     * @return 请求体键值对。
     */
    private fun buildTokenBody(pwdId: String, passcode: String?): Map<String, String> {
        val body = linkedMapOf(
            KEY_PWD_ID to pwdId,
            KEY_SHARE_FOR_TRANSFER to TRUE_VALUE
        )
        if (!passcode.isNullOrBlank()) {
            body[KEY_PASSCODE] = passcode
        }
        return body
    }

    /**
     * 把 UC 的文件条目映射为领域模型（浏览阶段无直链）。
     *
     * @return 领域层文件描述。
     */
    private fun UcFile.toFileInfo(): FileInfo = FileInfo(
        fid = fid,
        fileName = file_name,
        fileSize = size,
        isDirectory = dir,
        downloadUrl = null,
        shareFidToken = share_fid_token
    )

    private companion object {
        /** UC 首页，用于 best-effort 获取 __pus / __puus Cookie。 */
        const val UC_HOME_URL = "https://drive.uc.cn/"

        /** Cookie 名（__pugs 为未登录游客兜底，见 §9.3③；登录取到时一并登记，无害）。 */
        const val PUS_COOKIE_NAME = "__pus"
        const val PUUS_COOKIE_NAME = "__puus"
        const val PUGS_COOKIE_NAME = "__pugs"

        /** 响应 Set-Cookie 头名与拼接分隔符。 */
        const val HEADER_SET_COOKIE = "Set-Cookie"
        const val COOKIE_SEPARATOR = "; "

        /** Cookie 域名后缀：覆盖 drive.uc.cn（握手）与 pc-api.uc.cn（接口）。 */
        const val UC_COOKIE_HOST_SUFFIX = "uc.cn"

        /** 根目录的 pdir_fid 取值（实测根目录为 "0"）。 */
        const val ROOT_PDIR_FID = "0"

        /** 无提取码时传给 detail 的空串（《抓包事实.md》§6.1②）。 */
        const val EMPTY_PASSCODE = ""

        /** 分页参数。 */
        const val FIRST_PAGE = "1"
        const val PERSONAL_PAGE_SIZE = "100"

        /** 排序表达式（folder 优先）。 */
        const val PERSONAL_SORT = "file_type:asc,updated_at:desc"

        /** 布尔 / 占位字面量。 */
        const val TRUE_VALUE = "true"
        const val ONE_VALUE = "1"
        const val ZERO_VALUE = "0"

        /** 请求参数名。 */
        const val KEY_PWD_ID = "pwd_id"
        const val KEY_PASSCODE = "passcode"
        const val KEY_SHARE_FOR_TRANSFER = "share_for_transfer"
        const val KEY_PDIR_FID = "pdir_fid"
        const val KEY_PR = "pr"
        const val KEY_FR = "fr"
        const val KEY_PAGE = "_page"
        const val KEY_SIZE = "_size"
        const val KEY_SORT = "_sort"
        const val KEY_FETCH_TOTAL = "_fetch_total"
        const val KEY_FETCH_SUB_DIRS = "_fetch_sub_dirs"
        const val KEY_FETCH_SUBSCRIBE = "fetch_subscribe"
        const val KEY_CH = "_ch"

        /** 容量查询的首页频道（《抓包事实.md》§10.1）。 */
        const val HOME_CHANNEL = "home"

        /** 成功状态码（实测为 0）。 */
        const val SUCCESS_CODE = 0

        /**
         * 错误码。为保持 domain 层不依赖 Android 资源系统，
         * code 与 message 均使用机器可读标识，由 UI 层映射为 strings.xml 文案。
         */
        const val CODE_INVALID_LINK = "UC_INVALID_LINK"
        const val CODE_TOKEN_FAILED = "UC_TOKEN_FAILED"
        const val CODE_DETAIL_FAILED = "UC_DETAIL_FAILED"
        const val CODE_WRONG_PASSWORD = "UC_WRONG_PASSWORD"
        const val CODE_NETWORK = "UC_NETWORK_ERROR"
        const val CODE_PROTOCOL = "UC_PROTOCOL_ERROR"
    }
}