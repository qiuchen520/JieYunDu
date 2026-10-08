// 文件：XunleiLoginBody.kt
// 职责：迅雷登录类请求体的公共体（baseLoginBody，17 字段）与三个接口的业务字段合成
// 依赖：无（纯 Map 构造，便于单测）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import kotlin.random.Random

/**
 * 迅雷 `baseLoginBody` 公共体构造器（【JYD-XUNLEI-P1B2-2026-10-08】）。
 *
 * 事实来源：《抓包事实.md》**§6.3「baseLoginBody 公共体」**（17 字段，2026-10-08 从源码补全）
 * 与 §6.3 的「按接口取值差异」表；三个登录接口把公共体与各自业务字段合成**同一个扁平 JSON**
 * （**不是**两层嵌套，§6.3 原文明示）。
 *
 * ⚠️ 装机最易挂的点（§6.3 原文）：只发业务字段、漏掉公共体 → 真机直接报缺字段 / `1007`。
 *
 * 设计说明：
 * - 全部字段值在文档中均为字符串，故用 `Map<String, String>` 构造，既保证**扁平**，也避免
 *   为三个接口各写一份 22 字段的 data class；
 * - 本类**不持有**任何设备状态：`deviceId` / `peerID` / `devicesign` 由调用方从
 *   `XunleiFingerprint` 取（保证与该次 `captcha/init` 是**同一套指纹**，§6.3 尾注要求）。
 */
object XunleiLoginBody {

    /** 登录类接口（决定 `clientVersion` / `sdkVersion` / `creditkey`）。 */
    enum class Kind {
        /** 账号密码登录 `v3/login`。 */
        PASSWORD,

        /** 发短信 `v3/sendsms`。 */
        SEND_SMS,

        /** 短信登录 `v3/smslogin`。 */
        SMS_LOGIN
    }

    /**
     * 构造公共体（17 字段，§6.3）。
     *
     * @param kind 目标接口。
     * @param deviceId 本机 deviceId（32 位 hex，来自一机一指纹）。
     * @param peerId 本机 peerID（32 位 hex）。
     * @param creditkey 发短信返回的 creditkey；仅 [Kind.SMS_LOGIN] 使用，其余传空串。
     * @param sequenceNo 随机 8 位数字；默认现生成（每次请求都要重新随机，§6.3 尾注）。
     * @return 公共体键值对（顺序与 §6.3 列表一致）。
     */
    fun base(
        kind: Kind,
        deviceId: String,
        peerId: String,
        creditkey: String = "",
        sequenceNo: String = randomSequenceNo(random = Random.Default)
    ): Map<String, String> {
        require(sequenceNo.length == SEQUENCE_NO_LENGTH) {
            "sequenceNo must be $SEQUENCE_NO_LENGTH digits"
        }
        // devicesign 按 §4 签名② 由 deviceId 现算（与 captcha/init 同一指纹）。
        val deviceSign = XunleiSigning.deviceSign(deviceId)
        return linkedMapOf(
            KEY_PROTOCOL_VERSION to PROTOCOL_VERSION,
            KEY_SEQUENCE_NO to sequenceNo,
            KEY_PLATFORM_VERSION to PLATFORM_VERSION,
            KEY_IS_COMPRESSED to IS_COMPRESSED,
            KEY_APPID to XunleiConfig.APP_ID,
            KEY_CLIENT_VERSION to clientVersionOf(kind),
            KEY_PEER_ID to peerId,
            KEY_APP_NAME to APP_NAME,
            KEY_SDK_VERSION to sdkVersionOf(kind),
            KEY_DEVICE_SIGN to deviceSign,
            KEY_NETWORK_TYPE to NETWORK_TYPE,
            KEY_PROVIDER_NAME to PROVIDER_NAME,
            KEY_DEVICE_MODEL to DEVICE_MODEL,
            KEY_DEVICE_NAME to DEVICE_NAME,
            KEY_OS_VERSION to OS_VERSION,
            // creditkey：按接口差异表，仅短信登录填 sendsms 的 creditkey，其余为空串。
            KEY_CREDITKEY to if (kind == Kind.SMS_LOGIN) creditkey else "",
            KEY_HL to HL
        )
    }

    /**
     * 账号密码登录 body：公共体 + 业务字段（§6.3）。
     *
     * @param userName 账号。
     * @param password 密码。
     * @param verifyCode 验证码；无则空串。
     * @param deviceId 本机 deviceId。
     * @param peerId 本机 peerID。
     * @param sequenceNo 随机序号。
     * @param random 随机源。
     * @return 扁平请求体。
     */
    fun passwordLogin(
        userName: String,
        password: String,
        verifyCode: String = "",
        deviceId: String,
        peerId: String,
        sequenceNo: String = randomSequenceNo(random = Random.Default),
        random: Random = Random.Default
    ): Map<String, String> = base(
        kind = Kind.PASSWORD,
        deviceId = deviceId,
        peerId = peerId,
        sequenceNo = sequenceNo
    ) + linkedMapOf(
        KEY_USER_NAME to userName,
        KEY_PASS_WORD to password,
        KEY_VERIFY_KEY to EMPTY_VALUE,
        KEY_VERIFY_CODE to verifyCode,
        KEY_IS_MD5_PWD to IS_MD5_PWD
    )

    /**
     * 发短信 body：公共体 + `mobile` / `register`（§6.3）。
     *
     * @param mobile 手机号。
     * @param deviceId 本机 deviceId。
     * @param peerId 本机 peerID。
     * @param sequenceNo 随机序号。
     * @param random 随机源。
     * @return 扁平请求体。
     */
    fun sendSms(
        mobile: String,
        deviceId: String,
        peerId: String,
        sequenceNo: String = randomSequenceNo(random = Random.Default),
        random: Random = Random.Default
    ): Map<String, String> = base(
        kind = Kind.SEND_SMS,
        deviceId = deviceId,
        peerId = peerId,
        sequenceNo = sequenceNo
    ) + linkedMapOf(
        KEY_MOBILE to mobile,
        KEY_REGISTER to ZERO_VALUE
    )

    /**
     * 短信登录 body：公共体（`creditkey` 填 sendsms 的返回值）+ 业务字段（§6.3）。
     *
     * @param mobile 手机号。
     * @param smsCode 短信验证码。
     * @param token 发短信返回的 token。
     * @param creditkey 发短信返回的 creditkey。
     * @param deviceId 本机 deviceId。
     * @param peerId 本机 peerID。
     * @param sequenceNo 随机序号。
     * @param random 随机源。
     * @return 扁平请求体。
     */
    fun smsLogin(
        mobile: String,
        smsCode: String,
        token: String,
        creditkey: String,
        deviceId: String,
        peerId: String,
        sequenceNo: String = randomSequenceNo(random = Random.Default),
        random: Random = Random.Default
    ): Map<String, String> = base(
        kind = Kind.SMS_LOGIN,
        deviceId = deviceId,
        peerId = peerId,
        creditkey = creditkey,
        sequenceNo = sequenceNo
    ) + linkedMapOf(
        KEY_MOBILE to mobile,
        KEY_SMS_CODE to smsCode,
        KEY_TOKEN to token,
        KEY_REGISTER to ZERO_VALUE
    )

    /**
     * 生成公共体用的随机序号（8 位数字字符串，§6.3）。
     *
     * @param random 随机源。
     * @return 形如 `04291734` 的 8 位数字串（允许前导零，仍为「8 位数字」）。
     */
    fun randomSequenceNo(random: Random = Random.Default): String =
        random.nextInt(SEQUENCE_NO_UPPER_BOUND).toString().padStart(SEQUENCE_NO_LENGTH, '0')

    /**
     * 按接口取 `clientVersion`（§6.3「按接口取值差异」表）。
     *
     * @param kind 目标接口。
     * @return `v3/login` 为 `25.0.5.25`；发短信 / 短信登录为 App 版本。
     */
    fun clientVersionOf(kind: Kind): String = when (kind) {
        Kind.PASSWORD -> CLIENT_VERSION_LOGIN
        Kind.SEND_SMS, Kind.SMS_LOGIN -> XunleiConfig.APP_VERSION
    }

    /**
     * 按接口取 `sdkVersion`（§6.3「按接口取值差异」表）。
     *
     * @param kind 目标接口。
     * @return `v3/login` 为 `513006`；发短信 / 短信登录为 `231500`。
     */
    fun sdkVersionOf(kind: Kind): String = when (kind) {
        Kind.PASSWORD -> SDK_VERSION_LOGIN
        Kind.SEND_SMS, Kind.SMS_LOGIN -> SDK_VERSION_SMS
    }

    /** 固定值（§6.3 公共体）。 */
    const val PROTOCOL_VERSION = "301"
    const val PLATFORM_VERSION = "10"
    const val IS_COMPRESSED = "0"
    const val APP_NAME = "ANDROID-com.xunlei.downloadprovider"
    const val NETWORK_TYPE = "WIFI"
    const val PROVIDER_NAME = "NONE"
    const val DEVICE_MODEL = "M2004J7AC"
    const val DEVICE_NAME = "Xiaomi_M2004j7ac"
    const val OS_VERSION = "12"
    const val HL = "zh-CN"

    /** 业务字段固定值（§6.3）。 */
    const val IS_MD5_PWD = "0"

    /** 按接口差异值（§6.3 表）。 */
    const val CLIENT_VERSION_LOGIN = "25.0.5.25"
    const val SDK_VERSION_LOGIN = "513006"
    const val SDK_VERSION_SMS = "231500"

    /** 随机序号长度（§6.3：随机 8 位数字）。 */
    const val SEQUENCE_NO_LENGTH = 8

    /** 8 位数字的上界（10^8）。 */
    private const val SEQUENCE_NO_UPPER_BOUND = 100_000_000

    private const val EMPTY_VALUE = ""
    private const val ZERO_VALUE = "0"

    /** 公共体字段名（§6.3，顺序即发送顺序）。 */
    private const val KEY_PROTOCOL_VERSION = "protocolVersion"
    private const val KEY_SEQUENCE_NO = "sequenceNo"
    private const val KEY_PLATFORM_VERSION = "platformVersion"
    private const val KEY_IS_COMPRESSED = "isCompressed"
    private const val KEY_APPID = "appid"
    private const val KEY_CLIENT_VERSION = "clientVersion"
    private const val KEY_PEER_ID = "peerID"
    private const val KEY_APP_NAME = "appName"
    private const val KEY_SDK_VERSION = "sdkVersion"
    private const val KEY_DEVICE_SIGN = "devicesign"
    private const val KEY_NETWORK_TYPE = "netWorkType"
    private const val KEY_PROVIDER_NAME = "providerName"
    private const val KEY_DEVICE_MODEL = "deviceModel"
    private const val KEY_DEVICE_NAME = "deviceName"
    private const val KEY_OS_VERSION = "OSVersion"
    private const val KEY_CREDITKEY = "creditkey"
    private const val KEY_HL = "hl"

    /** 业务字段名（§6.3）。 */
    private const val KEY_USER_NAME = "userName"
    private const val KEY_PASS_WORD = "passWord"
    private const val KEY_VERIFY_KEY = "verifyKey"
    private const val KEY_VERIFY_CODE = "verifyCode"
    private const val KEY_IS_MD5_PWD = "isMd5Pwd"
    private const val KEY_MOBILE = "mobile"
    private const val KEY_SMS_CODE = "smsCode"
    private const val KEY_TOKEN = "token"
    private const val KEY_REGISTER = "register"
}
