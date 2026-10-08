// 文件：XunleiSigningTest.kt
// 职责：锁定迅雷 devicesign 公式（对齐官方抓包值）与 capthca_sign 的「未实现」事实
// 依赖：kotlin.test、XunleiSigning、XunleiConfig
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
     * `captcha_sign` 在拿到折叠算法前**必须返回 null**（不许猜测实现）。
     *
     * 一旦用户补上算法，本测试应改为断言真实值；在那之前它是一道「不许偷偷编造」的闸门。
     */
    @Test
    fun captchaSign_isNotImplementedWithoutAlgorithm() {
        assertNull(XunleiSigning.captchaSign())
    }

    /** 10 个盐按文档顺序原样留存（照抄，不得增删改序）。 */
    @Test
    fun captchaSalts_areCopiedVerbatimInOrder() {
        assertEquals(10, XunleiConfig.CAPTCHA_SALTS.size)
        assertEquals("9uJNVj/wLmdwKrJaVj/omlQ", XunleiConfig.CAPTCHA_SALTS.first())
        assertEquals("+oK0AN", XunleiConfig.CAPTCHA_SALTS.last())
    }
}
