// 文件：XunleiSigning.kt
// 职责：迅雷设备签名计算（devicesign 两段哈希；captcha_sign 待用户抓包补算法）
// 依赖：java.security.MessageDigest
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

import java.security.MessageDigest

/**
 * 迅雷签名工具（【JYD-XUNLEI-P1A-2026-10-08】，严格照抄《抓包事实.md》§4 / §11.2）。
 *
 * 本类**只实现文档给出完整公式的部分**；缺算法的部分一律留 `TODO(用户抓包)`，不猜测。
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
     * 计算 `captcha_sign`。
     *
     * ⚠️ `TODO(用户抓包)`：文档只给出「`1.<10层md5>`」与 10 个盐（见
     * [XunleiConfig.CAPTCHA_SALTS]），**未给出折叠算法**（初始输入、逐层顺序、是否混入
     * deviceId / timestamp）。故此处**不实现**任何猜测版本，调用方应视为「不可用」。
     *
     * @return 恒为 null，表示算法未确认。
     */
    fun captchaSign(): String? =
        // TODO(用户抓包): captcha_sign 的折叠算法（初始输入与 10 层盐的串联顺序）
        null

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
