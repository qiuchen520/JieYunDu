// 文件：XunleiFingerprint.kt
// 职责：迅雷「一机一指纹」——首次生成 deviceId / peerId，持久化后永久复用，异常时回退官方值
// 依赖：XunleiConfig、XunleiSigning、EncryptedSharedPreferences、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jieyundu.app.domain.parser.xunlei.XunleiConfig
import com.jieyundu.app.domain.parser.xunlei.XunleiSigning
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * 迅雷设备指纹（【JYD-XUNLEI-P1A-2026-10-08】；依据《抓包事实.md》§4「签名②」与
 * 《参考实现_源码通读研究.md》§11.2「一机一指纹」）。
 *
 * 为什么要一机一指纹（Owner 硬性约束①）：
 * 所有用户共用官方抓包指纹会被风控**连带封禁**；因此每台设备首次启动生成一套随机指纹并
 * 永久复用。同时必须**绝不使用官方指纹做正常路径**——它只作为「存储不可用 / 生成失败」时的
 * 兜底，保证功能不崩（宁可退化，不可崩溃）。
 *
 * 指纹组成：
 * - `deviceId`：32 位十六进制（与官方值同形）；随机生成后持久化；
 * - `peerId`：32 位十六进制；随机生成后持久化；
 * - `deviceSign`：由 `deviceId` 按文档公式计算（见 [XunleiSigning.deviceSign]），不单独存储，
 *   避免「deviceId 变了但签名没变」的不一致。
 *
 * 线程安全：读取走内存缓存 + `SharedPreferences`；生成走 `synchronized` 保证并发首启只生成一次。
 *
 * @param prefs 加密偏好存储；为 null 时退化为「本进程内随机 + 官方兜底」。
 */
@Singleton
class XunleiFingerprint internal constructor(
    private val prefs: SharedPreferences?
) {

    /** 设备 id（一机一指纹的核心）。 */
    val deviceId: String = ensureValue(KEY_DEVICE_ID, XunleiConfig.FALLBACK_DEVICE_ID)

    /** 对等 id（同为本机指纹的一部分）。 */
    val peerId: String = ensureValue(KEY_PEER_ID, XunleiConfig.FALLBACK_PEER_ID)

    /** 设备签名（由 [deviceId] 计算，不落盘）。 */
    val deviceSign: String = XunleiSigning.deviceSign(deviceId)

    /**
     * 应用上下文构造（生产路径）：用 EncryptedSharedPreferences 持久化指纹。
     *
     * @param context 应用上下文。
     */
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(createEncryptedPreferences(context, XunleiConfig.PREFS_FILE_FINGERPRINT))

    /**
     * 读取已持久化的值；缺失则生成一次并落盘。
     *
     * @param key 偏好键。
     * @param officialFallback 极端退化路径使用的官方抓包值（仅生成也失败时才用）。
     * @return 32 位十六进制指纹。
     */
    private fun ensureValue(key: String, officialFallback: String): String {
        prefs?.getString(key, null)?.takeIf { value -> value.isNotBlank() }?.let { value ->
            return value
        }
        // 正常路径：随机生成（一机一指纹）。存储不可用时仍用本次生成的值（进程内一致），
        // **不**主动退化成所有设备共用的官方指纹——共用官方指纹正是要避免的风控连坐。
        val generated = runCatching { randomFingerprint() }.getOrElse { error ->
            // 极端退化（随机源不可用）：按文档回退官方值，保证不崩，并留下告警便于排查。
            Timber.e(error, "XunleiFingerprint random generation failed for %s, falling back to official value", key)
            return officialFallback
        }
        val stored = runCatching { prefs?.edit()?.putString(key, generated)?.apply() }.getOrNull()
        if (prefs != null && stored == null) {
            Timber.w("XunleiFingerprint persist failed for %s, using in-memory value", key)
        }
        return generated
    }

    /**
     * 生成 32 位十六进制随机指纹（与官方值同形；文档只要求「随机生成并持久化」）。
     *
     * @return 32 位十六进制串。
     */
    private fun randomFingerprint(): String =
        UUID.randomUUID().toString().replace("-", "")

    private companion object {
        /** 偏好键：deviceId。 */
        const val KEY_DEVICE_ID = "device_id"

        /** 偏好键：peerId。 */
        const val KEY_PEER_ID = "peer_id"

        /**
         * 创建加密偏好存储；不可用时返回 null（调用方降级）。
         *
         * ponytail: 与 `CookieStore` 的私有实现同形（约 12 行重复）；项目铁律要求本批
         * 「不动夸克 / UC 代码」，故未抽取公共 helper——待第三个加密存储出现时再统一。
         *
         * @param context 应用上下文。
         * @param fileName 偏好文件名。
         * @return 加密偏好；创建失败返回 null。
         */
        fun createEncryptedPreferences(context: Context, fileName: String): SharedPreferences? = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (exception: Exception) {
            // 与 CookieStore 同策略：加密存储不可用时降级，不阻断业务。
            Timber.e(exception, "XunleiFingerprint encrypted prefs unavailable, fallback to in-memory")
            null
        }
    }
}
