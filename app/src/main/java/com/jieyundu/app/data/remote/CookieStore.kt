// 文件：CookieStore.kt
// 职责：按域名后缀保存与取出 Cookie 串，并做加密持久化（EncryptedSharedPreferences）
// 依赖：androidx.security.crypto（EncryptedSharedPreferences）、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Cookie 仓库：内存热缓存 + 加密持久化。
 *
 * 背景（依据《阶段 6 交付后整改指令》硬伤 1 +【修订 JYD-CHANGE-2026-10-03】阶段 11）：
 * 解析器在握手阶段拿到的 Cookie（如夸克首页返回的 `__puus`）必须能传递到后续 Retrofit
 * 请求；QuarkApi 的方法签名在《要求.md》7.6 中已定死、不允许增加 Header / Cookie 参数，
 * 故采用「解析器写入仓库 → CookieInterceptor 按域名注入」的旁路方案：
 * 解析器只负责拿 Cookie，注入由网络层统一完成，职责清晰且不污染接口签名。
 *
 * 持久化（阶段 11）：登录态 Cookie 用 [EncryptedSharedPreferences]（AES-256）落盘，
 * 使 App 重启后仍可复用登录态；若设备密钥库异常导致加密存储不可用，则**降级为纯内存**
 * （仅本次进程有效），绝不因存储失败而崩溃（D15 / §9.8 降级精神）。
 *
 * 依赖注入说明：存储实例 [prefs] 由构造参数注入——Hilt 用 [ApplicationContext] 打开
 * 加密存储（见 [createEncryptedPreferences]，失败返回 null 即降级纯内存）；单元测试
 * 用内部构造传 null，从而无需 Android Context 即可构造（纯内存）。
 *
 * 线程安全：读写均基于 [ConcurrentHashMap] 与 `SharedPreferences`，可在多协程并发下使用。
 *
 * @param prefs 加密存储；null 表示降级为纯内存。
 */
@Singleton
class CookieStore internal constructor(
    private val prefs: SharedPreferences?
) {

    /**
     * Hilt 注入入口：用应用上下文打开加密存储。
     *
     * @param context 应用上下文。
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(createEncryptedPreferences(context))

    /** 域名后缀 -> 该域可用的 Cookie 串（形如 `__puus=xxx`）。 */
    private val byDomainSuffix = ConcurrentHashMap<String, String>()

    init {
        // 启动时把已加密落盘的 Cookie 载回内存，保证重启后登录态可用。
        prefs?.all?.forEach { (key, value) ->
            if (value is String && value.isNotBlank()) {
                byDomainSuffix[key] = value
            }
        }
    }

    /**
     * 保存某个域名后缀下的 Cookie（同域**合并**后加密落盘）。
     *
     * 行为：与既有 Cookie 合并——同名键以后者为准，不同名键保留（[mergeCookie]），
     * 避免后续响应（如下载下发的 `__pugs`）冲掉先前的 `__puus` / `__pus`
     * （《解析Bug分析.md》P1-1）。
     *
     * @param domainSuffix 域名后缀（例如 `quark.cn`，可同时覆盖 pan.quark.cn 与
     *   drive-pc.quark.cn）。
     * @param cookie 原始 Cookie 串（形如 `__puus=xxx`）；为空时忽略，避免写入脏值。
     */
    fun save(domainSuffix: String, cookie: String) {
        if (domainSuffix.isBlank() || cookie.isBlank()) return
        // 同域**合并回写**而非覆盖：避免后续响应（如下载下发的 __pugs）冲掉先前的
        // __puus / __pus（《解析Bug分析.md》P1-1）。
        val merged = mergeCookie(byDomainSuffix[domainSuffix], cookie)
        byDomainSuffix[domainSuffix] = merged
        prefs?.edit()?.putString(domainSuffix, merged)?.apply()
    }

    /**
     * 取出可作用于指定 host 的 Cookie。
     *
     * 匹配规则：host 等于后缀本身，或以 `.后缀` 结尾即命中
     * （`drive-pc.quark.cn` 命中 `quark.cn`，`notquark.cn` 不命中）。
     *
     * @param host 请求目标主机名。
     * @return 命中的 Cookie 串；无命中返回 null。
     */
    fun findForHost(host: String): String? =
        byDomainSuffix.entries
            .firstOrNull { (suffix, _) -> host == suffix || host.endsWith(".$suffix") }
            ?.value

    /** 清空全部 Cookie（内存 + 加密存储）。 */
    fun clear() {
        byDomainSuffix.clear()
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * 合并两段 Cookie 串：同名键以后者为准，不同名键保留。
     *
     * 例如既有 `__puus=a; __pus=b`，新来 `__pugs=c` → `__puus=a; __pus=b; __pugs=c`；
     * 新来 `__puus=new` → `__pus=b; __puus=new`。
     *
     * @param existing 现有 Cookie 串；可为 null。
     * @param incoming 新到的 Cookie 串。
     * @return 合并后的 Cookie 串。
     */
    private fun mergeCookie(existing: String?, incoming: String): String {
        val pairs = LinkedHashMap<String, String>()
        fun absorb(raw: String) {
            raw.split(';').forEach { segment ->
                val trimmed = segment.trim()
                if (trimmed.isEmpty()) return@forEach
                val equalIndex = trimmed.indexOf('=')
                if (equalIndex <= 0) return@forEach
                val name = trimmed.substring(0, equalIndex).trim()
                val value = trimmed.substring(equalIndex + 1).trim()
                if (name.isNotEmpty()) pairs[name] = value
            }
        }
        existing?.let { absorb(it) }
        absorb(incoming)
        return pairs.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }
}

/** 加密存储文件名。 */
private const val PREFS_NAME = "jieyundu_cookie_store"

/**
 * 打开加密存储；失败时记录日志并返回 null（降级纯内存）。
 *
 * @param context 应用上下文。
 * @return 加密 [SharedPreferences]；不可用时 null。
 */
private fun createEncryptedPreferences(context: Context): SharedPreferences? = try {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} catch (exception: Exception) {
    Timber.e(exception, "CookieStore encrypted storage unavailable, fallback to in-memory")
    null
}