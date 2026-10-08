// 文件：XunleiSigning.kt
// 职责：迅雷设备签名计算（devicesign 两段哈希；captcha_sign 待用户抓包补算法）
// 依赖：java.security.MessageDigest
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import java.security.MessageDigest

/**
 * 迅雷签名工具（【JYD-XUNLEI-P1B-2026-10-08】，严格照抄《抓包事实.md》§4 / §11.2）。
 *
 * 两个签名都已有完整算法依据：
 * - [deviceSign]：两段哈希，且**有官方向量可比对**；
 * - [captchaSign]：10 层加盐 md5（§4「签名①」算法已还原），**无已知向量**，
 *   故单测改为锁定「拼接规则 + 折叠规则 + 确定性 + 顺序敏感性」。
 */
object XunleiSigning {

    /**
     * 计算 `devicesign`（§4「签名②：devicesign（两段哈希）」）。
     *
     * 公式（逐字符照抄）：
     * `div101.{deviceId}{ md5( sha1( deviceId + "com.xunlei.downloadprovider" + "40" + APP_KEY ) ) }`
     *
     * 该实现已用文档给出的官方 fallback 组验证：以
     * `deviceId = 78a70629a2b17d0b4302317ffa94807a`、`APP_KEY = 34a062aaa22f906fca4fefe9fb3a3021`
     * 计算，得到文档所载 `div101.78a70629a2b17d0b4302317ffa94807a31491e163e795b39e798ed33ae58858b`。
     *
     * @param deviceId 本机 deviceId（一机一指纹，见 `XunleiFingerprint`）。
     * @param appKey APP_KEY；默认取 [XunleiConfig.APP_KEY]。
     * @param packageName 包名；默认取 [XunleiConfig.PACKAGE_NAME]。
     * @param appId APPID；默认取 [XunleiConfig.APP_ID]。
     * @return `div101.{deviceId}{md5hex}`。
     */
    fun deviceSign(
        deviceId: String,
        appKey: String = XunleiConfig.APP_KEY,
        packageName: String = XunleiConfig.PACKAGE_NAME,
        appId: String = XunleiConfig.APP_ID
    ): String =
        XunleiConfig.DEVICE_SIGN_PREFIX + deviceId +
            md5Hex(sha1Hex(deviceId + packageName + appId + appKey))

    /**
     * 计算 `captcha_sign`（§4「签名①」，算法已由事实文档还原）。
     *
     * 算法（逐字照抄）：
     * ```
     * raw = APP_CLIENT_ID + APP_CLIENT_VERSION + APP_PACKAGE_NAME + deviceId + timestamp_ms
     * h   = raw
     * for salt in SALTS:            # 10 个盐，顺序敏感
     *     h = md5_hex(h + salt)
     * captcha_sign = "1." + h
     * ```
     * 要点：拼接**无任何分隔符**；`timestamp_ms` 为毫秒字符串，且必须与请求体
     * `meta.timestamp` **同一值**；CLIENT_ID 用 **App 端**凭据（非 Web 端）。
     *
     * @param deviceId 本机 deviceId。
     * @param timestampMillis 与 `meta.timestamp` 同值的毫秒时间戳。
     * @param clientId App 端 CLIENT_ID；默认取 [XunleiConfig.APP_CLIENT_ID]。
     * @param clientVersion App 版本；默认取 [XunleiConfig.APP_VERSION]。
     * @param packageName 包名；默认取 [XunleiConfig.PACKAGE_NAME]。
     * @return 形如 `1.<32 位 hex>` 的签名。
     */
    fun captchaSign(
        deviceId: String,
        timestampMillis: Long,
        clientId: String = XunleiConfig.APP_CLIENT_ID,
        clientVersion: String = XunleiConfig.APP_VERSION,
        packageName: String = XunleiConfig.PACKAGE_NAME
    ): String = CAPTCHA_SIGN_PREFIX + captchaHash(
        raw = captchaRaw(
            deviceId = deviceId,
            timestampMillis = timestampMillis,
            clientId = clientId,
            clientVersion = clientVersion,
            packageName = packageName
        )
    )

    /**
     * 拼出 captcha_sign 的初始串 `raw`（算法第一步，无分隔符）。
     *
     * 单独暴露以便单测**逐字段锁定拼接顺序**——顺序或分隔符写错都会导致服务端拒绝，
     * 但签名值本身无已知向量可比对，故把「拼接规则」本身作为可断言的对象。
     *
     * @param deviceId 本机 deviceId。
     * @param timestampMillis 毫秒时间戳。
     * @param clientId App 端 CLIENT_ID。
     * @param clientVersion App 版本。
     * @param packageName 包名。
     * @return 无分隔符的初始串。
     */
    fun captchaRaw(
        deviceId: String,
        timestampMillis: Long,
        clientId: String = XunleiConfig.APP_CLIENT_ID,
        clientVersion: String = XunleiConfig.APP_VERSION,
        packageName: String = XunleiConfig.PACKAGE_NAME
    ): String = clientId + clientVersion + packageName + deviceId + timestampMillis.toString()

    /**
     * 按顺序叠加 10 个盐做 md5 折叠。
     *
     * @param raw 初始串（见 [captchaRaw]）。
     * @param salts 盐列表；默认取 [XunleiConfig.CAPTCHA_SALTS]（顺序敏感）。
     * @return 折叠结果（32 位小写 hex，不含 `1.` 前缀）。
     */
    fun captchaHash(raw: String, salts: List<String> = XunleiConfig.CAPTCHA_SALTS): String {
        var hash = raw
        salts.forEach { salt -> hash = md5Hex(hash + salt) }
        return hash
    }

    /** captcha_sign 固定前缀。 */
    const val CAPTCHA_SIGN_PREFIX = "1."

    /**
     * SHA-1 → 小写 16 进制。
     *
     * @param input 待哈希文本（UTF-8）。
     * @return 40 位小写十六进制串。
     */
    fun sha1Hex(input: String): String = digestHex("SHA-1", input)

    /**
     * MD5 → 小写 16 进制。
     *
     * @param input 待哈希文本（UTF-8）。
     * @return 32 位小写十六进制串。
     */
    fun md5Hex(input: String): String = digestHex("MD5", input)

    /**
     * 通用摘要 → 小写十六进制。
     *
     * @param algorithm `MessageDigest` 算法名。
     * @param input 待哈希文本（UTF-8）。
     * @return 十六进制串。
     */
    private fun digestHex(algorithm: String, input: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (value < 0x10) {
                builder.append('0')
            }
            builder.append(Integer.toHexString(value))
        }
        return builder.toString()
    }
}
