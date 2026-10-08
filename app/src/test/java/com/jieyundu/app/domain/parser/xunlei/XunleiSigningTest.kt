// 文件：XunleiSigningTest.kt
// 职责：锁定迅雷 devicesign 公式（对齐官方抓包值）与 capthca_sign 的「未实现」事实
// 依赖：kotlin.test、XunleiSigning、XunleiConfig
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [XunleiSigning] 的回归测试（【JYD-XUNLEI-P1A-2026-10-08】）。
 *
 * 这是本批**最强的协议锚点**：《抓包事实.md》§4 同时给出了公式与一组官方 fallback 值，
 * 因此可以逐字符验证实现——只要公式或常量被改错，本测试立刻红灯。
 */
class XunleiSigningTest {

    /**
     * 用官方 fallback 值验证 devicesign 公式。
     *
     * 文档值（§4「签名②」）：
     * `deviceId = 78a70629a2b17d0b4302317ffa94807a`，
     * `devicesign = div101.78a70629a2b17d0b4302317ffa94807a31491e163e795b39e798ed33ae58858b`。
     */
    @Test
    fun deviceSign_matchesOfficialCapturedValue() {
        val sign = XunleiSigning.deviceSign(XunleiConfig.FALLBACK_DEVICE_ID)
        assertEquals(
            "div101.78a70629a2b17d0b4302317ffa94807a31491e163e795b39e798ed33ae58858b",
            sign
        )
    }

    /** 换一个 deviceId 时，签名前缀仍是 `div101.{deviceId}`，哈希段随 deviceId 变化。 */
    @Test
    fun deviceSign_usesDeviceIdPrefixAndChangesWithDeviceId() {
        val a = XunleiSigning.deviceSign("0123456789abcdef0123456789abcdef")
        val b = XunleiSigning.deviceSign("fedcba9876543210fedcba9876543210")
        assertEquals("div101.0123456789abcdef0123456789abcdef", a.substring(0, 7 + 32))
        assertEquals(7 + 32 + 32, a.length, "div101. + deviceId(32) + md5(32)")
        assertEquals(32, a.removePrefix("div101.0123456789abcdef0123456789abcdef").length)
        kotlin.test.assertNotEquals(a, b)
    }

    /** 两段哈希的中间值：sha1 / md5 输出必须是小写十六进制、定长。 */
    @Test
    fun hashHelpers_areLowercaseHexWithFixedLength() {
        assertEquals(40, XunleiSigning.sha1Hex("abc").length)
        assertEquals(32, XunleiSigning.md5Hex("abc").length)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", XunleiSigning.md5Hex("abc"))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", XunleiSigning.sha1Hex("abc"))
    }

    /**
     * captcha_sign 的**拼接规则**逐字段锁定（§4 签名①：无分隔符、App 端 CLIENT_ID）。
     *
     * 文档未给出 captcha_sign 的已知向量，故把「算法第一步」作为可断言对象——
     * 顺序写错或加了分隔符都会让服务端拒绝，而这两点正是最容易写错的。
     */
    @Test
    fun captchaRaw_concatenatesFieldsWithoutSeparatorsInDocumentedOrder() {
        val raw = XunleiSigning.captchaRaw(
            deviceId = DEVICE_ID,
            timestampMillis = TIMESTAMP
        )
        assertEquals(
            XunleiConfig.APP_CLIENT_ID + XunleiConfig.APP_VERSION +
                XunleiConfig.PACKAGE_NAME + DEVICE_ID + TIMESTAMP.toString(),
            raw
        )
        // 默认必须用 App 端 CLIENT_ID（文档明确：不是 Web 端）。
        kotlin.test.assertTrue(raw.startsWith(XunleiConfig.APP_CLIENT_ID))
        kotlin.test.assertFalse(raw.contains(XunleiConfig.WEB_CLIENT_ID))
    }

    /**
     * captcha_sign = `1.` + 10 层加盐 md5；此处与**测试内独立实现**交叉验证，
     * 并断言 10 层盐、顺序敏感、时间戳敏感、确定性。
     */
    @Test
    fun captchaSign_foldsTenSaltsInOrder() {
        val sign = XunleiSigning.captchaSign(deviceId = DEVICE_ID, timestampMillis = TIMESTAMP)
        kotlin.test.assertTrue(sign.startsWith("1."))
        assertEquals(1 + 32, sign.length, "1.<32 hex>")

        // 独立实现（照文档再写一遍）：raw → 依次 md5(h + salt) 共 10 次。
        var h = XunleiConfig.APP_CLIENT_ID + XunleiConfig.APP_VERSION +
            XunleiConfig.PACKAGE_NAME + DEVICE_ID + TIMESTAMP.toString()
        XunleiConfig.CAPTCHA_SALTS.forEach { salt -> h = md5Hex(h + salt) }
        assertEquals("1.$h", sign)

        // 确定性。
        assertEquals(sign, XunleiSigning.captchaSign(DEVICE_ID, TIMESTAMP))
        // 时间戳敏感（与 meta.timestamp 必须同值，故时间戳进签名）。
        kotlin.test.assertNotEquals(sign, XunleiSigning.captchaSign(DEVICE_ID, TIMESTAMP + 1))
        // 盐顺序敏感。
        kotlin.test.assertNotEquals(
            XunleiSigning.captchaHash("raw"),
            XunleiSigning.captchaHash("raw", XunleiConfig.CAPTCHA_SALTS.reversed())
        )
    }

    /** 测试内独立实现的 md5（用于交叉验证折叠算法，不依赖被测代码）。 */
    private fun md5Hex(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /** 10 个盐按文档顺序原样留存（照抄，不得增删改序）。 */
    @Test
    fun captchaSalts_areCopiedVerbatimInOrder() {
        assertEquals(10, XunleiConfig.CAPTCHA_SALTS.size)
        assertEquals("9uJNVj/wLmdwKrJaVj/omlQ", XunleiConfig.CAPTCHA_SALTS.first())
        assertEquals("+oK0AN", XunleiConfig.CAPTCHA_SALTS.last())
    }

    private companion object {
        /** 测试用 deviceId（32 位 hex，与格式一致）。 */
        const val DEVICE_ID = "0123456789abcdef0123456789abcdef"

        /** 测试用毫秒时间戳。 */
        const val TIMESTAMP = 1_760_000_000_000L
    }
}
