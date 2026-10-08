// 文件：XunleiParserTest.kt
// 职责：迅雷解析器单元测试（分享解析分流 / 子目录展开 / 文件映射 / 错误码）
// 依赖：XunleiParser、XunleiApi、XunleiModels、kotlin.test、kotlinx-coroutines-test
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * [XunleiParser] 的单元测试（【JYD-XUNLEI-P1A-2026-10-08】）。
 *
 * 覆盖点（全部依据《抓包事实.md》§11.4 #10/#11 的字段与 `share_status` 三态）：
 * 1. 域名判定与网盘类型；
 * 2. 分享解析成功：文件映射（`id` → fid、`kind == drive#folder` → 目录）、
 *    `pass_code_token` → stoken、`title` → 分享标题；
 * 3. `share_status` 分流：需要提取码 / 提取码错误（后者透传服务端描述）；
 * 4. 无提取码时**不带** `pass_code`、有提取码时带上；
 * 5. 子目录展开：走 `share/detail` 且回传 `pass_code_token` 与 `parent_id`；
 * 6. 根目录防御分支：走分享接口（不臆造 `share/detail` 的根取值）；
 * 7. 异常：网络失败 → `XUNLEI_NETWORK_ERROR`。
 *
 * 说明：测试源码同样遵守 C5——注释为中文，字面量全为 ASCII。
 */
class XunleiParserTest {

    /** 迅雷分享链接应由本解析器接管。 */
    @Test
    fun match_acceptsXunleiShareLink() {
        assertTrue(newParser().match(SHARE_URL))
    }

    /** 其它网盘链接不得被本解析器接管。 */
    @Test
    fun match_rejectsOtherNetdiskLinks() {
        val parser = newParser()
        assertFalse(parser.match("https://pan.quark.cn/s/abcdef"))
        assertFalse(parser.match("https://drive.uc.cn/s/abcdef"))
        assertFalse(parser.match("https://pan.baidu.com/s/1abcdef"))
    }

    /** 解析器声明的网盘类型必须是 XUNLEI。 */
    @Test
    fun type_isXunlei() {
        assertEquals(NetdiskType.XUNLEI, newParser().type)
    }

    /** 无码分享：成功映射文件与令牌，且请求**不带** pass_code。 */
    @Test
    fun parse_contentShare_mapsFilesAndOmitsPassCode() = runTest {
        val api = FakeXunleiApi(
            shareResponse = shareResponse(
                title = "demo-share",
                passCodeToken = "token-abc",
                files = listOf(
                    XunleiShareFile(id = "dir-1", name = "folder-a", kind = XunleiConfig.KIND_FOLDER),
                    XunleiShareFile(id = "file-1", name = "movie.mp4", kind = null, size = 0L)
                )
            )
        )
        val result = newParser(api).parse(SHARE_URL, null)

        assertTrue(result is ParseResult.Success, "expected success, got $result")
        result as ParseResult.Success
        assertEquals(SHARE_ID, result.pwdId)
        assertEquals("token-abc", result.stoken)
        assertEquals("demo-share", result.shareTitle)
        assertEquals(2, result.files.size)
        assertEquals("dir-1", result.files[0].fid)
        assertTrue(result.files[0].isDirectory, "kind == drive#folder must map to directory")
        assertEquals("file-1", result.files[1].fid)
        assertFalse(result.files[1].isDirectory)
        assertEquals(null, api.lastPassCode, "content share must not send pass_code")
    }

    /** 带提取码：请求必须带上 pass_code。 */
    @Test
    fun parse_withPassword_sendsPassCode() = runTest {
        val api = FakeXunleiApi()
        newParser(api).parse(SHARE_URL, "ab12")
        assertEquals("ab12", api.lastPassCode)
    }

    /** `PASS_CODE_NEED`：交 UI 弹提取码输入（不是错误）。 */
    @Test
    fun parse_whenPassCodeNeeded_returnsNeedPassword() = runTest {
        val api = FakeXunleiApi(
            shareResponse = shareResponse(status = XunleiConfig.SHARE_STATUS_PASS_CODE_NEED)
        )
        val result = newParser(api).parse(SHARE_URL, null)
        assertTrue(result is ParseResult.NeedPassword, "expected NeedPassword, got $result")
    }

    /** `PASS_CODE_ERROR`：提取码错误，且透传服务端描述。 */
    @Test
    fun parse_whenPassCodeWrong_returnsErrorWithServerText() = runTest {
        val api = FakeXunleiApi(
            shareResponse = shareResponse(
                status = XunleiConfig.SHARE_STATUS_PASS_CODE_ERROR,
                errorDescription = "pass code invalid"
            )
        )
        val result = newParser(api).parse(SHARE_URL, "zzzz")
        assertTrue(result is ParseResult.Error, "expected Error, got $result")
        result as ParseResult.Error
        assertEquals("XUNLEI_WRONG_PASSWORD", result.code)
        assertEquals("pass code invalid", result.message)
    }

    /** 分享链接缺少分享 id：本地直接判无效，不发请求。 */
    @Test
    fun parse_withoutShareId_returnsInvalidLink() = runTest {
        val api = FakeXunleiApi()
        val result = newParser(api).parse("https://pan.xunlei.com/", null)
        assertTrue(result is ParseResult.Error)
        assertEquals("XUNLEI_INVALID_LINK", (result as ParseResult.Error).code)
        assertEquals(0, api.shareCalls, "must not call API for invalid link")
    }

    /** 网络失败：归类为网络错误，不抛出。 */
    @Test
    fun parse_networkFailure_returnsNetworkCode() = runTest {
        val api = FakeXunleiApi(throwOnShare = IOException("boom"))
        val result = newParser(api).parse(SHARE_URL, null)
        assertTrue(result is ParseResult.Error)
        assertEquals("XUNLEI_NETWORK_ERROR", (result as ParseResult.Error).code)
    }

    /** 子目录展开：走 share/detail，并回传 pass_code_token 与 parent_id。 */
    @Test
    fun listChildren_usesShareDetailWithTokenAndParent() = runTest {
        val api = FakeXunleiApi(
            detailResponse = XunleiShareDetailResponse(
                files = listOf(XunleiShareFile(id = "file-2", name = "child.bin"))
            )
        )
        val children = newParser(api).listChildren(SHARE_ID, "token-abc", "dir-1")

        assertEquals(1, children.size)
        assertEquals("file-2", children[0].fid)
        assertEquals("dir-1", api.lastDetailParentId)
        assertEquals("token-abc", api.lastDetailPassCodeToken)
        assertEquals(SHARE_ID, api.lastDetailShareId)
    }

    /** 根目录防御分支：不得用 share/detail 猜根取值，改走分享接口。 */
    @Test
    fun listChildren_rootFallsBackToShareEndpoint() = runTest {
        val api = FakeXunleiApi(
            shareResponse = shareResponse(files = listOf(XunleiShareFile(id = "root-1", name = "a.bin")))
        )
        val children = newParser(api).listChildren(SHARE_ID, "token-abc", "0")

        assertEquals(1, children.size)
        assertEquals(1, api.shareCalls)
        assertEquals(0, api.detailCalls)
    }

    /** 子目录请求失败：返回空列表（不抛出，由 UI 呈现空目录）。 */
    @Test
    fun listChildren_failure_returnsEmptyList() = runTest {
        val api = FakeXunleiApi(throwOnDetail = IOException("boom"))
        assertTrue(newParser(api).listChildren(SHARE_ID, "token-abc", "dir-1").isEmpty())
    }

    /**
     * 构造被测试解析器。
     *
     * @param api 假接口实现。
     * @return 解析器实例。
     */
    private fun newParser(api: FakeXunleiApi = FakeXunleiApi()): XunleiParser = XunleiParser(api)

    /**
     * 构造分享响应（字段名严格对应 §11.4 #10）。
     *
     * @param title 标题。
     * @param files 条目。
     * @param passCodeToken 提取码令牌。
     * @param status 分享状态。
     * @param errorDescription 服务端描述。
     * @return 响应对象。
     */
    private fun shareResponse(
        title: String = "t",
        files: List<XunleiShareFile> = emptyList(),
        passCodeToken: String? = null,
        status: String? = XunleiConfig.SHARE_STATUS_PASS_CODE_EMPTY,
        errorDescription: String? = null
    ): XunleiShareResponse = XunleiShareResponse(
        title = title,
        files = files,
        pass_code_token = passCodeToken,
        next_page_token = null,
        share_status = status,
        error = null,
        error_description = errorDescription
    )

    /**
     * 假迅雷接口（记录调用参数，便于断言请求形态）。
     *
     * @property shareResponse 分享接口固定返回。
     * @property detailResponse 子目录接口固定返回。
     * @property throwOnShare 分享接口抛出的异常。
     * @property throwOnDetail 子目录接口抛出的异常。
     */
    private class FakeXunleiApi(
        private val shareResponse: XunleiShareResponse = XunleiShareResponse(),
        private val detailResponse: XunleiShareDetailResponse = XunleiShareDetailResponse(),
        private val throwOnShare: IOException? = null,
        private val throwOnDetail: IOException? = null
    ) : XunleiApi {

        var lastPassCode: String? = null
        var shareCalls = 0
        var detailCalls = 0
        var lastDetailShareId: String? = null
        var lastDetailParentId: String? = null
        var lastDetailPassCodeToken: String? = null

        override suspend fun share(
            shareId: String,
            passCode: String?,
            limit: Int,
            pageToken: String?,
            thumbnailSize: String
        ): XunleiShareResponse {
            shareCalls++
            lastPassCode = passCode
            throwOnShare?.let { error -> throw error }
            return shareResponse
        }

        override suspend fun shareDetail(
            shareId: String,
            parentId: String,
            passCodeToken: String?,
            limit: Int,
            pageToken: String?,
            thumbnailSize: String
        ): XunleiShareDetailResponse {
            detailCalls++
            lastDetailShareId = shareId
            lastDetailParentId = parentId
            lastDetailPassCodeToken = passCodeToken
            throwOnDetail?.let { error -> throw error }
            return detailResponse
        }

        override suspend fun about(): XunleiAboutResponse = XunleiAboutResponse()
    }

    private companion object {
        /** 测试用分享链接（迅雷分享码允许 - 与 _）。 */
        const val SHARE_URL = "https://pan.xunlei.com/s/VN_abc-123"

        /** 从 [SHARE_URL] 提取出的分享 id（LinkExtractor 取 `/s/` 之后一段）。 */
        const val SHARE_ID = "VN_abc-123"
    }
}
