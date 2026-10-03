// 文件：QuarkParserTest.kt
// 职责：夸克解析器单元测试（含 Cookie 通道与全链路断言）
// 依赖：QuarkParser、QuarkApi、CookieStore、kotlin.test、kotlinx-coroutines-test、OkHttp
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.quark

import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 *   MockWebServer 依赖的前提下，验证 Cookie 通道（整改指令硬伤 1）；
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

    /** 未提供提取码时应直接返回 NeedPassword，且不发任何请求。 */
    @Test
    fun parse_withoutPassword_returnsNeedPassword() = runTest {
        val result = newParser().parse(SHARE_URL, null)
        assertTrue(result is ParseResult.NeedPassword)
    }

    /** 链接不含分享 ID（缺少 /s/ 段）时应返回 Error。 */
    @Test
    fun parse_withoutShareId_returnsError() = runTest {
        val result = newParser().parse("https://pan.quark.cn/", "1234")
        assertTrue(result is ParseResult.Error)
    }

    /**
     * 硬伤 2 断言 A：无法取得 `__puus` 时应返回「缺少 Cookie」错误码。
     */
    @Test
    fun parse_withoutPuusCookie_returnsCookieError() = runTest {
        val parser = newParser(homeCookie = null)
        val result = parser.parse(SHARE_URL, "1234")
        assertTrue(result is ParseResult.Error, "no-cookie case must yield Error")
        assertEquals(CODE_NEED_COOKIE, (result as ParseResult.Error).code)
    }

    /**
     * 硬伤 2 断言 B：取得 `__puus` 后应走通 token -> detail 链路并返回文件列表。
     */
    @Test
    fun parse_withPuusCookie_returnsSuccessWithFiles() = runTest {
        val parser = newParser(
            api = FakeQuarkApi(
                files = listOf(QuarkFile(fid = "fid-1", file_name = "demo.txt", size = 1024L))
            )
        )
        val result = parser.parse(SHARE_URL, "1234")
        assertTrue(result is ParseResult.Success, "with-cookie case must yield Success")
        val files = (result as ParseResult.Success).files
        assertEquals(1, files.size)
        assertEquals("fid-1", files.first().fid)
        assertEquals("demo.txt", files.first().fileName)
        assertEquals(1024L, files.first().fileSize)
    }

    /** 硬伤 2 断言 C：`__puus` 必须被登记进 CookieStore，供后续请求注入。 */
    @Test
    fun parse_registersPuusCookieIntoStore() = runTest {
        val store = CookieStore()
        val parser = newParser(cookieStore = store)
        parser.parse(SHARE_URL, "1234")
        assertEquals(HOME_PUUS_COOKIE, store.findForHost("drive-pc.quark.cn"))
    }

    /** 硬伤 2 断言 D：服务端返回非成功码时应映射为 token 失败错误码。 */
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
        private val stoken: String = "fake-stoken",
        private val files: List<QuarkFile> = emptyList()
    ) : QuarkApi {

        override suspend fun getShareToken(body: Map<String, String>): QuarkResponse<QuarkShareToken> =
            QuarkResponse(code = tokenCode, message = "ok", data = QuarkShareToken(stoken = stoken))

        override suspend fun getShareDetail(params: Map<String, String>): QuarkResponse<QuarkShareDetail> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = QuarkShareDetail(list = files))

        override suspend fun getDownloadUrl(body: Map<String, Any>): QuarkResponse<List<QuarkDownloadUrl>> =
            QuarkResponse(code = SUCCESS_CODE, message = "ok", data = emptyList())
    }

    private companion object {
        /** 与 QuarkParser.CODE_NEED_COOKIE 保持一致（机器可读错误码）。 */
        const val CODE_NEED_COOKIE = "QUARK_NEED_COOKIE"

        const val SHARE_URL = "https://pan.quark.cn/s/abcdef123456"
        const val HOME_PUUS_COOKIE = "__puus=fake-puus"
        const val HEADER_SET_COOKIE = "Set-Cookie"
        const val EMPTY_BODY = ""
        const val HTTP_OK = 200
        const val SUCCESS_CODE = 0

        /** 占位风控码；真实取值待抓包（见 QuarkApi 的 TODO）。 */
        const val RISK_CONTROL_CODE = 31001
    }
}
