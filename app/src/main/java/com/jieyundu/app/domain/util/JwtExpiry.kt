// 文件：JwtExpiry.kt
// 职责：从 JWT 中读取过期时间（exp）与用户 id（sub），不校验签名（只读自身令牌）
// 依赖：java.util.Base64（minSdk 26 起可用，且便于 JVM 单测）、kotlinx.serialization
// 协议：AGPL-3.0

package com.jieyundu.app.domain.util

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * JWT 负载解析（【JYD-XUNLEI-P1A-2026-10-08】）。
 *
 * 存在理由（Owner 硬性约束②）：迅雷 `access_token` 是 JWT，带 `exp`；据此判断是否过期、
 * 过期即用 refresh_token 刷新，可避免每次请求都撞 401。
 *
 * 边界：**只读 payload，不校验签名**——本函数只用于解析我们**自己持有**的令牌，
 * 不用于信任外部输入；解析失败一律返回 null（调用方按「未知 / 视为过期」处理）。
 */
object JwtExpiry {

    /** 宽松 JSON：字段可能增删。 */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 读取 JWT 的过期时间。
     *
     * @param jwt 形如 `header.payload.signature` 的令牌。
     * @return 过期时间（epoch 毫秒）；无法解析时返回 null。
     */
    fun expiresAtMillis(jwt: String): Long? = payload(jwt)
        ?.get("exp")
        ?.let { element -> runCatching { element.jsonPrimitive.longOrNull }.getOrNull() }
        ?.let { seconds -> seconds * MILLIS_PER_SECOND }

    /**
     * 读取 JWT 的用户 id（迅雷为 `sub`，captcha/init 的 `meta.user_id` 需要它）。
     *
     * @param jwt 令牌。
     * @return 用户 id；无法解析时返回 null。
     */
    fun subject(jwt: String): String? = payload(jwt)
        ?.get("sub")
        ?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }

    /**
     * 判断令牌是否已过期（含提前量）。
     *
     * @param jwt 令牌。
     * @param nowMillis 当前时间；默认取系统时间。
     * @param skewMillis 提前量（默认 60 秒，避免边界上刚好过期）。
     * @return true 表示已过期或无法解析（保守视为过期）。
     */
    fun isExpired(jwt: String, nowMillis: Long = System.currentTimeMillis(), skewMillis: Long = DEFAULT_SKEW_MILLIS): Boolean {
        val expiresAt = expiresAtMillis(jwt) ?: return true
        return nowMillis + skewMillis >= expiresAt
    }

    /**
     * 解析 JWT payload 段。
     *
     * @param jwt 令牌。
     * @return payload JSON 对象；结构不合法或不是 UTF-8 时返回 null。
     */
    private fun payload(jwt: String): JsonObject? {
        val parts = jwt.split('.')
        if (parts.size < 2) {
            return null
        }
        return runCatching {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as? JsonObject
        }.getOrNull()
    }

    /** 每秒毫秒数。 */
    private const val MILLIS_PER_SECOND = 1000L

    /** 默认提前量：60 秒。 */
    private const val DEFAULT_SKEW_MILLIS = 60_000L
}
