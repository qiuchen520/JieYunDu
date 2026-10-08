// 文件：LoginValidationConfigTest.kt
// 职责：登录校验配置（校验端点 / 成功判据 / UA）的单元测试（JYD-BAIDU-COOKIE-2026-10-05）
// 依赖：JUnit4、LoginValidator、UserAgentProvider、OkHttpClient
// 协议：AGPL-3.0

package com.jieyundu.app.domain.login

import com.jieyundu.app.data.remote.UserAgentProvider
import com.jieyundu.app.domain.model.NetdiskType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录校验配置的单元测试。
 *
 * 背景（本次修复的真实 bug）：百度**没有**校验端点时，[LoginValidator.validate] 因
 * `validationUrlOf(type) == null` 恒返回 false，导致百度 Cookie 无论自动保存还是手动保存
 * 都**永远存不进去**。本测试锁死「百度必须有校验端点与专属判据」，防止将来又被摘掉。
 */
class LoginValidationConfigTest {

    private val validator = LoginValidator(OkHttpClient(), UserAgentProvider())
    private val agents = UserAgentProvider()

    // ---- 校验端点 ----

    @Test
    fun `baidu has a validation endpoint`() {
        val url = validator.validationUrlOf(NetdiskType.BAIDU)
        assertNotNull("百度缺少校验端点会导致 Cookie 永远存不进", url)
        assertTrue(url!!.startsWith("https://pan.baidu.com/api/gettemplatevariable"))
        assertTrue("必须带上 app_id=250528", url.contains("app_id=250528"))
        assertTrue("必须以 bdstoken 为探测字段", url.contains("bdstoken"))
    }

    @Test
    fun `quark and uc keep their existing endpoints`() {
        assertEquals(
            "https://pan.quark.cn/account/info",
            validator.validationUrlOf(NetdiskType.QUARK)
        )
        assertEquals(
            "https://drive.uc.cn/account/info",
            validator.validationUrlOf(NetdiskType.UC)
        )
    }

    @Test
    fun `xunlei has no cookie based validation endpoint`() {
        // 迅雷走 access_token（JWT），非 Cookie 体系。
        assertNull(validator.validationUrlOf(NetdiskType.XUNLEI))
    }

    // ---- 成功判据 ----

    @Test
    fun `baidu success marker is bdstoken`() {
        assertEquals("\"bdstoken\"", validator.markerOf(NetdiskType.BAIDU))
    }

    @Test
    fun `other platforms keep the data marker`() {
        assertEquals("\"data\"", validator.markerOf(NetdiskType.QUARK))
        assertEquals("\"data\"", validator.markerOf(NetdiskType.UC))
    }

    // ---- UA 选择 ----

    @Test
    fun `baidu validation uses web user agent`() {
        val agent = validator.userAgentOf(NetdiskType.BAIDU)
        assertTrue("百度校验需网页 UA（§11.3 #1）", agent.contains("Chrome/124"))
        assertFalse("不得使用夸克客户端 UA", agent.contains("quark-cloud-drive"))
    }

    @Test
    fun `webview user agent is per netdisk type`() {
        assertTrue(agents.webUserAgentOf(NetdiskType.BAIDU).contains("Chrome/124"))
        assertTrue(agents.webUserAgentOf(NetdiskType.QUARK).contains("QuarkPC"))
        assertTrue(agents.webUserAgentOf(NetdiskType.UC).contains("Chrome/120"))
        // 迅雷：JYD-XUNLEI-P1A-2026-10-08 起已有事实依据（《抓包事实.md》§4「UA」之 Web），
        // 故由「空串」改为文档所载网页 UA；仍必须**不拿别家 UA 顶替**。
        val xunleiWeb = agents.webUserAgentOf(NetdiskType.XUNLEI)
        assertTrue(xunleiWeb.contains("Chrome"), "xunlei web UA must be the documented web UA")
        assertTrue(xunleiWeb.startsWith("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
        assertFalse(xunleiWeb.contains("QuarkPC"), "must not borrow quark UA")
        assertFalse(xunleiWeb.contains("Chrome/124"), "must not borrow baidu UA")
        assertFalse(xunleiWeb.contains("uc-cloud-drive"), "must not borrow uc UA")
    }
}
