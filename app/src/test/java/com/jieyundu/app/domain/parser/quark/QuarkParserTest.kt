// 文件：QuarkParserTest.kt
// 职责：夸克解析器单元测试（含 Cookie 通道、标题/文件夹映射、目录展开与全链路断言）
// 依赖：QuarkParser、QuarkApi、CookieStore、kotlin.test、kotlinx-coroutines-test、OkHttp
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.quark

import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * [QuarkParser] 的单元测试。
 *
 * 说明（阶段 13 职责重划）：
 * - 解析器只负责「握手 → token → detail（根目录 / 子目录）」浏览链路；
 *   转存 + 取直链已移交 `ShareTransfer`，故此处不再断言直链回填；
 * - 用 [FakeQuarkApi] 替代真实接口，不触网；
 * - 用「短路拦截器」构造假的首页响应（含 / 不含 Set-Cookie），验证 `__puus` 通道（best-effort）；
 * - 测试源码同样遵守 C5（注释为中文，字面量全为 ASCII）。
 */
class QuarkParserTest {

    /** 夸克分享链接应被本解析器接管。 */
    @Test
    fun match_acceptsQuarkShareLink() {
        assertTrue(newParser().match(SHARE_URL))
    }

    /** 非夸克链接不得被本解析器接管。 */
    @Test
    fun match_rejectsOtherNetdiskLink() {
        val parser = newParser()
        assertFalse(parser.match("https://pan.baidu.com/s/1abcdef"))
        assertFalse(parser.match("https://drive.uc.cn/s/abcdef"))
        assertFalse(parser.match("https://pan.xunlei.com/s/abcdef"))
    }

    /** 解析器声明的网盘类型必须是 QUARK。 */
    @Test
    fun type_isQuark() {
        assertEquals(NetdiskType.QUARK, newParser().type)
    }

    /**
     * BUGFIX JYD-BUG-03-01 断言 A：链接未携带提取码时，不得直接判定「需要提取码」，
     * 而应先**不带 passcode** 调 token 接口；服务器返回成功时照常给出文件列表。
     */
    @Test
    fun parse_withoutPassword_triesServerWithoutPasscode() = runTest {
        val api = FakeQuarkApi(
            files = listOf(QuarkFile(fid = "fid-1", file_name = "demo.txt", size = 1024L))
        )
        val result = newParser(api = api).parse(SHARE_URL, null)
        assertTrue(result is ParseResult.Success, "no-password link must be tried against server")
        val body = api.lastTokenBody
        assertEquals(SHARE_ID, body?.get(KEY_PWD_ID))
        assertFalse(body?.containsKey(KEY_PASSCODE) == true, "passcode must be omitted when blank")
    }

    /**
     * BUGFIX JYD-BUG-03-01 断言 B：仅当服务器明确返回「需要提取码」时才提示用户输入。
     */
    @Test
    fun parse_withoutPassword_whenServerAsks_returnsNeedPassword() = runTest {
        val api = FakeQuarkApi(tokenCode = NEED_PASSWORD_CODE)
        val result = newParser(api = api).parse(SHARE_URL, null)
        assertTrue(result is ParseResult.NeedPassword)
    }

    /**
     * BUGFIX JYD-BUG-03-01 断言 C：链接携带提取码时，passcode 必须随请求体发出。
     */
    @Test
    fun parse_withPassword_sendsPasscode() = runTest {
        val api = FakeQuarkApi()
        newParser(api = api).parse(SHARE_URL, PASSWORD)
        assertEquals(PASSWORD, api.lastTokenBody?.get(KEY_PASSCODE))
    }

    /** 链接不含分享 ID（缺少 /s/ 段）时应返回 Error。 */
    @Test
    fun parse_withoutShareId_returnsError() = runTest {
        val result = newParser().parse("https://pan.quark.cn/", "1234")
        assertTrue(result is ParseResult.Error)
    }

    /**
     * 实测校准断言 A：取不到 `__puus` 时**不再**返回「缺少 Cookie」错误，
     * 而是照常走完 token -> detail（token/detail 实测无需任何 Cookie）。
     */
    @Test
    fun parse_withoutPuusCookie_stillSucceeds() = runTest {
        val parser = newParser(
            api = FakeQuarkApi(
                files = listOf(QuarkFile(fid = "fid-1", file_name = "demo.txt", size = 1024L))
            ),
            homeCookie = null
        )
        val result = parser.parse(SHARE_URL, "1234")
        assertTrue(result is ParseResult.Success, "missing __puus must not fail the parse")
    }

    /** 实测校准断言 B：分享标题应取自 token 响应的 data.title。 */
    @Test
    fun parse_fillsShareTitleFromToken() = runTest {
        val result = newParser(
            api = FakeQuarkApi(tokenTitle = "shared-title")
        ).parse(SHARE_URL, null)
        assertEquals("shared-title", (result as ParseResult.Success).shareTitle)
    }

    /** 阶段 13 断言：解析成功须回填 pwdId / stoken，供后续展开与转存使用。 */
    @Test
    fun parse_fillsPwdIdAndStoken() = runTest {
        val result = newParser(
            api = FakeQuarkApi(stoken = "fake-stoken")
        ).parse(SHARE_URL, null)
        val success = result as ParseResult.Success
        assertEquals(SHARE_ID, success.pwdId)
        assertEquals("fake-stoken", success.stoken)
    }

    /** 阶段 13 断言：分享条目的 share_fid_token 必须映射进 FileInfo（转存需要）。 */
    @Test
    fun parse_fillsShareFidToken() = runTest {
        val api = FakeQuarkApi(
            files = listOf(
                QuarkFile(
                    fid = "fid-1",
                    file_name = "demo.txt",
                    size = 1024L,
                    share_fid_token = "tok-1"
                )
            )
        )
        val result = newParser(api = api).parse(SHARE_URL, null)
        val file = (result as ParseResult.Success).files.first()
        assertEquals("tok-1", file.shareFidToken)
    }

    /** 实测校准断言 D：文件夹条目应标记 isDirectory、无直链，且解析阶段不请求直链。 */
    @Test
    fun parse_marksDirectoryEntries() = runTest {
        val api = FakeQuarkApi(
            files = listOf(
                QuarkFile(fid = "dir-1", file_name = "folder", size = 0L, dir = true)
            )
        )
        val result = newParser(api = api).parse(SHARE_URL, null)
        val entry = (result as ParseResult.Success).files.first()
        assertTrue(entry.isDirectory)
        assertNull(entry.downloadUrl)
        assertNull(api.lastDownloadBody, "parse must not trigger a download request")
    }

    /** 阶段 13 断言：detail 接口失败时应映射为获取文件列表失败错误码。 */
    @Test
    fun parse_detailFails_returnsError() = runTest {
        val api = FakeQuarkApi(detailCode = RISK_CONTROL_CODE)
        val result = newParser(api = api).parse(SHARE_URL, null)
        assertTrue(result is ParseResult.Error)
        assertEquals("QUARK_DETAIL_FAILED", (result as ParseResult.Error).message)
    }

    /** 阶段 13 断言：展开子目录须以该目录 fid 作为 pdir_fid 请求。 */
    @Test
    fun listChildren_requestsGivenPdirFid() = runTest {
        val api = FakeQuarkApi(
            childEntries = mapOf(
                "dir-1" to listOf(
                    QuarkFile(fid = "fid-2", file_name = "inner.txt", size = 10L)
                )
            )
        )
        val parser = newParser(api = api)
        val children = parser.listChildren(SHARE_ID, "fake-stoken", "dir-1")
        assertEquals("dir-1", api.lastDetailParams?.get(KEY_PDIR_FID))
        assertEquals(listOf("fid-2"), children.map { entry -> entry.fid })
    }

    /** 硬伤 2 断言：成功取得的 `__puus` 必须被登记进 CookieStore，供后续请求注入。 */
    @Test
    fun parse_registersPuusCookieIntoStore() = runTest {
        val store = CookieStore(null)
        val parser = newParser(cookieStore = store)
        parser.parse(SHARE_URL, "1234")
        assertEquals(HOME_PUUS_COOKIE, store.findForHost("drive-pc.quark.cn"))
    }

    /** 硬伤 2 断言：服务端返回非成功码时应映射为 token 失败错误码。 */
    @Test
    fun parse_withRiskControlCode_mapsTokenError() = runTest {
        val parser = newParser(api = FakeQuarkApi(tokenCode = RISK_CONTROL_CODE))
        val result = parser.parse(SHARE_URL, "1234")
        assertTrue(result is ParseResult.Error, "risk-control code must yield Error")
        assertEquals(RISK_CONTROL_CODE.toString(), (result as ParseResult.Error).code)
    }

    /**
     * 构造被测解析器：默认带可用 `__puus`，接口返回成功。
     *
     * @param api 接口替身。
     * @param homeCookie 首页 Set-Cookie 的值；为 null 表示拿不到 Cookie。
     * @param cookieStore Cookie 仓库。
     * @return 解析器实例。
     */
    private fun newParser(
        api: QuarkApi = FakeQuarkApi(),
        homeCookie: String? = HOME_PUUS_COOKIE,
        cookieStore: CookieStore = CookieStore(null)
    ): QuarkParser = QuarkParser(
        api = api,
        okHttpClient = homeClient(homeCookie),
        cookieStore = cookieStore
    )

    /**
     * 构造一个「短路」OkHttp 客户端：不触网，直接返回携带指定 Set-Cookie 的 200 响应。
     *
     * @param homeCookie Set-Cookie 值；为 null 时返回不带 Set-Cookie 的响应。
     * @return 测试用客户端。
     */
    private fun homeClient(homeCookie: String?): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(HTTP_OK)
                    .message("OK")
                    .body(EMPTY_BODY.toResponseBody(null))
                if (homeCookie != null) {
                    builder.header(HEADER_SET_COOKIE, homeCookie)
                }
                builder.build()
            }
            .build()

    /**
     * 测试替身：返回可配置的占位响应，不触网。
     */
    private class FakeQuarkApi(
        private val tokenCode: Int = SUCCESS_CODE,
        private val tokenTitle: String = "fake-title",
        private val stoken: String = "fake-stoken",
        private val files: List<QuarkFile> = emptyList(),
        private val childEntries: Map<String, List<QuarkFile>> = emptyMap(),
        private val detailCode: Int = SUCCESS_CODE
    ) : QuarkApi {

        /** 最近一次 token 请求体，供断言「是否携带 passcode」使用。 */
        var lastTokenBody: Map<String, String>? = null
            private set

        /** 最近一次 detail 查询参数，供断言 pdir_fid 使用。 */
        var lastDetailParams: Map<String, String>? = null
            private set

        /** 最近一次 download 请求体；解析阶段应始终为 null。 */
        var lastDownloadBody: QuarkDownloadRequest? = null
            private set

        override suspend fun getShareToken(body: Map<String, String>): QuarkResponse<QuarkShareToken> {
            lastTokenBody = body
            return QuarkResponse(
                code = tokenCode,
                message = "ok",
                data = QuarkShareToken(stoken = stoken, title = tokenTitle)
            )
        }

        override suspend fun getShareDetail(params: Map<String, String>): QuarkResponse<QuarkShareDetail> {
            lastDetailParams = params
            val pdirFid = params[KEY_PDIR_FID]
            val entries = when {
                pdirFid == null || pdirFid == ROOT_PDIR_FID -> files
                else -> childEntries[pdirFid].orEmpty()
            }
            return QuarkResponse(
                code = detailCode,
                message = "ok",
                data = QuarkShareDetail(list = entries)
            )
        }

        override suspend fun saveShare(body: QuarkSaveRequest): QuarkResponse<QuarkSaveResult> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = QuarkSaveResult())

        override suspend fun getTask(params: Map<String, String>): QuarkResponse<QuarkTask> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = QuarkTask())

        override suspend fun getDownloadUrl(
            body: QuarkDownloadRequest
        ): QuarkResponse<List<QuarkDownloadUrl>> {
            lastDownloadBody = body
            return QuarkResponse(code = SUCCESS_CODE, message = "ok", data = emptyList())
        }

        override suspend fun listFiles(params: Map<String, String>): QuarkResponse<QuarkFileList> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = QuarkFileList())

        override suspend fun createFolder(
            body: QuarkCreateFolderRequest
        ): QuarkResponse<QuarkCreateFolderResult> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = QuarkCreateFolderResult())

        override suspend fun deleteFiles(
            body: QuarkDeleteRequest
        ): QuarkResponse<QuarkDeleteResult> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = QuarkDeleteResult())
    }

    private companion object {
        const val SHARE_URL = "https://pan.quark.cn/s/abcdef123456"
        const val SHARE_ID = "abcdef123456"
        const val PASSWORD = "1234"
        const val KEY_PWD_ID = "pwd_id"
        const val KEY_PASSCODE = "passcode"
        const val KEY_PDIR_FID = "pdir_fid"

        /** 与 QuarkParser.ROOT_PDIR_FID 对齐的分享根目录取值。 */
        const val ROOT_PDIR_FID = "0"

        /** 与 QuarkParser.NEED_PASSWORD_CODE 对齐的占位值（真实取值待抓包）。 */
        const val NEED_PASSWORD_CODE = 41011

        /** 首页 Set-Cookie 样例。 */
        const val HOME_PUUS_COOKIE = "__puus=fake-puus"
        const val HEADER_SET_COOKIE = "Set-Cookie"
        const val EMPTY_BODY = ""
        const val HTTP_OK = 200
        const val SUCCESS_CODE = 0

        /** 占位风控码；真实取值待抓包（见 QuarkParser 常量注释）。 */
        const val RISK_CONTROL_CODE = 31001
    }
}