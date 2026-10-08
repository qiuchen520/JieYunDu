// 文件：XunleiLoginBodyTest.kt
// 职责：锁定 baseLoginBody 公共体的 17 个字段、按接口差异与「扁平合并」语义
// 依赖：kotlin.test、XunleiLoginBody、XunleiSigning、XunleiConfig
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [XunleiLoginBody] 的逐字段回归测试（【JYD-XUNLEI-P1B2-2026-10-08】）。
 *
 * 事实来源：《抓包事实.md》§6.3「`baseLoginBody` 公共体」（17 字段）+ 其后的
 * 「按接口取值差异」表。本测试把每个字段名与取值都钉住——**装机最易挂的点就是漏字段**
 * （§6.3 原文警告：只发业务字段、漏掉公共体会直接报缺字段 / `1007`）。
 *
 * 说明：测试源码遵守 C5（注释中文，字面量全 ASCII）。
 */
class XunleiLoginBodyTest {

    /** 公共体必须恰好 17 个字段，且关键字面逐一对上（顺序亦按文档）。 */
    @Test
    fun base_hasSeventeenFieldsInDocumentedOrder() {
        val base = XunleiLoginBody.base(
            kind = XunleiLoginBody.Kind.PASSWORD,
            deviceId = DEVICE_ID,
            peerId = PEER_ID,
            sequenceNo = SEQUENCE_NO
        )

        assertEquals(EXPECTED_COMMON_KEYS, base.keys.toList())
        assertEquals(17, base.size)
        assertEquals("301", base["protocolVersion"])
        assertEquals(SEQUENCE_NO, base["sequenceNo"])
        assertEquals("10", base["platformVersion"])
        assertEquals("0", base["isCompressed"])
        assertEquals("40", base["appid"])
        assertEquals(XunleiLoginBody.CLIENT_VERSION_LOGIN, base["clientVersion"])
        assertEquals(PEER_ID, base["peerID"])
        assertEquals("ANDROID-com.xunlei.downloadprovider", base["appName"])
        assertEquals(XunleiLoginBody.SDK_VERSION_LOGIN, base["sdkVersion"])
        assertEquals(XunleiSigning.deviceSign(DEVICE_ID), base["devicesign"])
        assertEquals("WIFI", base["netWorkType"])
        assertEquals("NONE", base["providerName"])
        assertEquals("M2004J7AC", base["deviceModel"])
        assertEquals("Xiaomi_M2004j7ac", base["deviceName"])
        assertEquals("12", base["OSVersion"])
        assertEquals("", base["creditkey"])
        assertEquals("zh-CN", base["hl"])
        // §6.3 尾注：公共体本身**不含** device_id（设备绑定靠 peerID + devicesign）。
        assertFalse(base.containsKey("device_id"))
    }

    /** 按接口差异：`v3/login` 用 25.0.5.25 / 513006；发短信与短信登录用 App 版本 / 231500。 */
    @Test
    fun perEndpointClientAndSdkVersionsMatchDocumentedTable() {
        val sms = XunleiLoginBody.base(
            XunleiLoginBody.Kind.SEND_SMS, DEVICE_ID, PEER_ID, sequenceNo = SEQUENCE_NO
        )
        val smsLogin = XunleiLoginBody.base(
            XunleiLoginBody.Kind.SMS_LOGIN, DEVICE_ID, PEER_ID,
            creditkey = "ck-1", sequenceNo = SEQUENCE_NO
        )

        assertEquals("25.0.5.25", XunleiLoginBody.clientVersionOf(XunleiLoginBody.Kind.PASSWORD))
        assertEquals("513006", XunleiLoginBody.sdkVersionOf(XunleiLoginBody.Kind.PASSWORD))
        assertEquals(XunleiConfig.APP_VERSION, sms["clientVersion"])
        assertEquals("231500", sms["sdkVersion"])
        assertEquals(XunleiConfig.APP_VERSION, smsLogin["clientVersion"])
        assertEquals("231500", smsLogin["sdkVersion"])
        // creditkey：仅短信登录填 sendsms 的返回值，其余为空串。
        assertEquals("", sms["creditkey"])
        assertEquals("ck-1", smsLogin["creditkey"])
    }

    /** 三个接口的 body 都是「公共体 + 业务字段」的**扁平**合并（不是两层嵌套）。 */
    @Test
    fun bodiesAreFlatMergesOfCommonAndBusinessFields() {
        val login = XunleiLoginBody.passwordLogin(
            userName = "u-1", password = "p-1",
            deviceId = DEVICE_ID, peerId = PEER_ID, sequenceNo = SEQUENCE_NO
        )
        val sms = XunleiLoginBody.sendSms(
            mobile = "13800000000",
            deviceId = DEVICE_ID, peerId = PEER_ID, sequenceNo = SEQUENCE_NO
        )
        val smsLogin = XunleiLoginBody.smsLogin(
            mobile = "13800000000", smsCode = "123456", token = "tk", creditkey = "ck",
            deviceId = DEVICE_ID, peerId = PEER_ID, sequenceNo = SEQUENCE_NO
        )

        // 17 + 业务字段数。
        assertEquals(17 + 5, login.size)
        assertEquals(17 + 2, sms.size)
        assertEquals(17 + 4, smsLogin.size)
        // 业务字段取值（§6.3）。
        assertEquals("u-1", login["userName"])
        assertEquals("p-1", login["passWord"])
        assertEquals("", login["verifyKey"])
        assertEquals("", login["verifyCode"])
        assertEquals("0", login["isMd5Pwd"])
        assertEquals("13800000000", sms["mobile"])
        assertEquals("0", sms["register"])
        assertEquals("123456", smsLogin["smsCode"])
        assertEquals("tk", smsLogin["token"])
        assertEquals("ck", smsLogin["creditkey"])
        // 公共体字段在同一次请求里仍然存在（扁平）。
        listOf(login, sms, smsLogin).forEach { body ->
            EXPECTED_COMMON_KEYS.forEach { key -> assertTrue(body.containsKey(key), "missing $key") }
        }
        // 不允许出现「嵌套的 baseLoginBody 子对象」。
        assertFalse(login.containsKey("baseLoginBody"))
    }

    /** devicesign 必须是「与本次同一 deviceId」派生（§6.3 尾注：与 captcha/init 同一套指纹）。 */
    @Test
    fun devicesignIsDerivedFromSameDeviceId() {
        val base = XunleiLoginBody.base(
            XunleiLoginBody.Kind.PASSWORD, DEVICE_ID, PEER_ID, sequenceNo = SEQUENCE_NO
        )
        assertEquals(XunleiSigning.deviceSign(DEVICE_ID), base["devicesign"])
        assertTrue(base["devicesign"]!!.startsWith("div101.$DEVICE_ID"))
    }

    /** sequenceNo：8 位数字字符串，且默认每次重新随机（§6.3）。 */
    @Test
    fun sequenceNoIsEightDigitsAndRandomPerRequest() {
        repeat(200) {
            val value = XunleiLoginBody.randomSequenceNo()
            assertEquals(8, value.length)
            assertTrue(value.all { symbol -> symbol.isDigit() }, "not digits: $value")
        }
        val a = XunleiLoginBody.randomSequenceNo()
        val b = XunleiLoginBody.randomSequenceNo()
        // 极小概率相同，允许相等但要求都在合法域内（此处只断言形态，不断言必然不同）。
        assertTrue(a.length == 8 && b.length == 8)
    }

    private companion object {
        const val DEVICE_ID = "0123456789abcdef0123456789abcdef"
        const val PEER_ID = "fedcba9876543210fedcba9876543210"
        const val SEQUENCE_NO = "04291734"

        /** §6.3 公共体字段顺序（17 个）。 */
        val EXPECTED_COMMON_KEYS = listOf(
            "protocolVersion", "sequenceNo", "platformVersion", "isCompressed", "appid",
            "clientVersion", "peerID", "appName", "sdkVersion", "devicesign",
            "netWorkType", "providerName", "deviceModel", "deviceName", "OSVersion",
            "creditkey", "hl"
        )
    }
}
