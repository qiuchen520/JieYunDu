// 文件：JwtExpiryTest.kt
// 职责：JWT 过期解析（exp / sub / 提前量 / 非法输入）的单元测试
// 依赖：kotlin.test、JwtExpiry、java.util.Base64
// 协议：AGPL-3.0

package com.jieyundu.app.domain.util

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [JwtExpiry] 的单元测试（【JYD-XUNLEI-P1A-2026-10-08】）。
 *
 * 说明：只验证「读 payload」的能力——本工具不校验签名（仅用于解析自身持有的令牌）。
 */
class JwtExpiryTest {

    /** 正常令牌：exp 换算为毫秒，sub 可读，未过期判 false。 */
    @Test
    fun readsExpAndSubjectFromPayload() {
        val jwt = jwt("""{"exp":1700000000,"sub":"123456789"}""")
        assertEquals(1_700_000_000_000L, JwtExpiry.expiresAtMillis(jwt))
        assertEquals("123456789", JwtExpiry.subject(jwt))
        assertFalse(JwtExpiry.isExpired(jwt, nowMillis = 1_699_999_000_000L))
    }

    /** 已过期（含 60 秒提前量）：边界前 30 秒即视为过期。 */
    @Test
    fun treatsTokenAsExpiredInsideSkewWindow() {
        val jwt = jwt("""{"exp":1700000000}""")
        val expiresAt = 1_700_000_000_000L
        assertFalse(JwtExpiry.isExpired(jwt, nowMillis = expiresAt - 120_000L))
        assertTrue(JwtExpiry.isExpired(jwt, nowMillis = expiresAt - 30_000L))
        assertTrue(JwtExpiry.isExpired(jwt, nowMillis = expiresAt))
    }

    /** 非法输入一律「保守视为过期」，并且不抛异常。 */
    @Test
    fun malformedTokensAreTreatedAsExpired() {
        listOf("", "abc", "abc.def", "a.b.c", "a.!!!.c").forEach { broken ->
            assertNull(JwtExpiry.expiresAtMillis(broken), "exp of $broken")
            assertTrue(JwtExpiry.isExpired(broken), "isExpired of $broken")
        }
    }

    /** payload 缺 exp：视为过期（不能因为解不出就当作有效）。 */
    @Test
    fun missingExpIsTreatedAsExpired() {
        val jwt = jwt("""{"sub":"1"}""")
        assertNull(JwtExpiry.expiresAtMillis(jwt))
        assertTrue(JwtExpiry.isExpired(jwt))
    }

    /**
     * 构造 JWT（header.payload.signature；签名段随意，本工具不校验）。
     *
     * @param payloadJson payload JSON 文本。
     * @return 形如 JWT 的字符串。
     */
    private fun jwt(payloadJson: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString(payloadJson.toByteArray())
        return "$header.$payload.signature-not-verified"
    }
}
