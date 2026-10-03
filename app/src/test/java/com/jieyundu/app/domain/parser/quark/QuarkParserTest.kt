// 文件：QuarkParserTest.kt
// 职责：夸克解析器单元测试（含 Cookie 通道、标题/直链/文件夹映射与全链路断言）
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
 * 说明：
 * - 用 [FakeQuarkApi] 替代真实接口，不触网；
 * - 用「短路拦截器」构造假的首页响应（含 / 不含 Set-Cookie），从而在不引入
 *   MockWebServer 依赖的前提下，验证 `__puus` 通道（best-effort）；
 * - 断言依据 2026-10-03 的夸克接口终端实测：token/detail 无需 Cookie、
 *   download 响应 data[] 含 fid 与 download_url、条目含 dir 文件夹标记；
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

    /** 实测校准断言 C：真实文件的 download_url 应按 fid 回填到对应 FileInfo。 */
    @Test
    fun parse_fillsDownloadUrlForFiles() = runTest {
        val api = FakeQuarkApi(
            files = listOf(QuarkFile(fid = "fid-1", file_name = "demo.txt", size = 1024L)),
            downloadEntries = listOf(
                QuarkDownloadUrl(fid = "fid-1", download_url = DIRECT_URL)
            )
        )
        val result = newParser(api = api).parse(SHARE_URL, null)
        val file = (result as ParseResult.Success).files.first()
        assertEquals(DIRECT_URL, file.downloadUrl)
    }

    /** 实测校准断言 D：文件夹条目应标记 isDirectory，且不请求直链。 */
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
        assertNull(api.lastDownloadBody, "directories must not trigger a download request")
    }

    /** 实测校准断言 E：download 接口返回非成功码时应映射为直链获取失败错误码。 */
    @Test
    fun parse_downloadFails_returnsError() = runTest {
        val api = FakeQuarkApi(
            files = listOf(QuarkFile(fid = "fid-1", file_name = "demo.txt", size = 1024L)),
            downloadCode = RISK_CONTROL_CODE
        )
        val result = newParser(api = api).parse(SHARE_URL, null)
        assertTrue(result is ParseResult.Error)
        assertEquals(RISK_CONTROL_CODE.toString(), (result as ParseResult.Error).code)
    }

    /** 硬伤 2 断言：成功取得的 `__puus` 必须被登记进 CookieStore，供后续请求注入。 */
    @Test
    fun parse_registersPuusCookieIntoStore() = runTest {
        val store = CookieStore()
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
        cookieStore: CookieStore = CookieStore()
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
        private val downloadCode: Int = SUCCESS_CODE,
        private val downloadEntries: List<QuarkDownloadUrl> = emptyList()
    ) : QuarkApi {

        /** 最近一次 token 请求体，供断言「是否携带 passcode」使用。 */
        var lastTokenBody: Map<String, String>? = null
            private set

        /** 最近一次 download 请求体，供断言「文件夹是否触发直链请求」使用。 */
        var lastDownloadBody: Map<String, Any>? = null
            private set

        override suspend fun getShareToken(body: Map<String, String>): QuarkResponse<QuarkShareToken> {
            lastTokenBody = body
            return QuarkResponse(
                code = tokenCode,
                message = "ok",
                data = QuarkShareToken(stoken = stoken, title = tokenTitle)
            )
        }

        override suspend fun getShareDetail(params: Map<String, String>): QuarkResponse<QuarkShareDetail> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = QuarkShareDetail(list = files))

        override suspend fun getDownloadUrl(body: Map<String, Any>): QuarkResponse<List<QuarkDownloadUrl>> {
            lastDownloadBody = body
            return QuarkResponse(code = downloadCode, message = "ok", data = downloadEntries)
        }
    }

    private companion object {
        const val SHARE_URL = "https://pan.quark.cn/s/abcdef123456"
        const val SHARE_ID = "abcdef123456"
        const val PASSWORD = "1234"
        const val DIRECT_URL = "https://dl-guest-zb-u.drive.quark.cn/fake"
        const val KEY_PWD_ID = "pwd_id"
        const val KEY_PASSCODE = "passcode"

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