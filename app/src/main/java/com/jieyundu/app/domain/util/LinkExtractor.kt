// 文件：LinkExtractor.kt
// 职责：从用户粘贴的自由文本中提取网盘分享链接与提取码
// 依赖：NetdiskType、ShareLink
// 协议：AGPL-3.0

package com.jieyundu.app.domain.util

import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ShareLink

/**
 * 分享链接与提取码提取器。
 *
 * 设计说明：
 * - 纯函数式工具，不持有 Context、不发起网络请求，便于单元测试。
 * - 中文关键字（提取码 / 密码 / 访问码）与全角冒号以 `\uXXXX` 转义书写，
 *   以符合编码风格 C5（.kt 源码不得出现中文字面量）；运行期仍是正常中文正则。
 * - 允许的链接形态见 [URL_PATTERNS]，四家网盘各一条主域名规则。
 */
object LinkExtractor {

    /** 分享链接中的分享 ID 分隔标记。 */
    private const val SHARE_ID_MARKER = "/s/"

    /** 提取码长度（四家网盘统一为 4 位）。 */
    private const val PASSWORD_LENGTH = 4

    /**
     * 各家网盘的分享链接正则。
     * 说明：百度为 `1` 开头且允许下划线与连字符；夸克与 UC 为字母数字；
     * 迅雷允许下划线与连字符。
     */
    private val URL_PATTERNS: List<Pair<NetdiskType, Regex>> = listOf(
        NetdiskType.BAIDU to Regex("https?://pan\\.baidu\\.com/s/1[A-Za-z0-9_\\-]+"),
        NetdiskType.QUARK to Regex("https?://pan\\.quark\\.cn/s/[A-Za-z0-9]+"),
        NetdiskType.UC to Regex("https?://drive\\.uc\\.cn/s/[A-Za-z0-9]+"),
        NetdiskType.XUNLEI to Regex("https?://pan\\.xunlei\\.com/s/[A-Za-z0-9_\\-]+")
    )

    /**
     * 提取码查询串：支持 `?pwd=abcd`、`&pwd=abcd`、`#pwd=abcd`。
     */
    private val PASSWORD_QUERY_REGEX =
        Regex("[?&#](?:pwd|password)=([A-Za-z0-9]{$PASSWORD_LENGTH})", RegexOption.IGNORE_CASE)

    /**
     * 正文中的提取码关键字。
     * 关键字部分依次为：\u63D0\u53D6\u7801、\u5BC6\u7801、\u8BBF\u95EE\u7801、pwd、password；
     * 分隔符支持半角冒号、等号与 \uFF1A（全角冒号）。
     */
    private val PASSWORD_KEYWORD_REGEX = Regex(
        "(?:\u63D0\u53D6\u7801|\u5BC6\u7801|\u8BBF\u95EE\u7801|pwd|password)" +
            "\\s*[:=\uFF1A]?\\s*([A-Za-z0-9]{$PASSWORD_LENGTH})",
        RegexOption.IGNORE_CASE
    )

    /**
     * 提取文本中的第一个分享链接。
     *
     * @param text 用户粘贴的原始文本。
     * @return 解析出的分享链接；文本中不含受支持链接时返回 null。
     */
    fun extract(text: String): ShareLink? = extractAll(text).firstOrNull()

    /**
     * 提取文本中的全部分享链接（同一段文本可能包含多条）。
     *
     * @param text 用户粘贴的原始文本。
     * @return 分享链接列表，可能为空；提取码取自文本正文，四条链接共用。
     */
    fun extractAll(text: String): List<ShareLink> {
        if (text.isBlank()) return emptyList()
        val password = extractPassword(text)
        val result = mutableListOf<ShareLink>()
        for ((type, regex) in URL_PATTERNS) {
            for (match in regex.findAll(text)) {
                val rawUrl = match.value
                result += ShareLink(
                    type = type,
                    rawUrl = rawUrl,
                    shareId = extractShareId(rawUrl),
                    password = password
                )
            }
        }
        return result
    }

    /**
     * 判断链接属于哪家网盘。
     *
     * @param url 分享链接。
     * @return 匹配到的网盘类型；不支持时返回 null。
     */
    fun detectType(url: String): NetdiskType? =
        URL_PATTERNS.firstOrNull { (_, regex) -> regex.containsMatchIn(url) }?.first

    /**
     * 从链接中截取分享 ID（`/s/` 之后、`?`/`#`/`/` 之前的部分）。
     *
     * @param url 分享链接。
     * @return 分享 ID；链接不含 `/s/` 时返回空串。
     */
    fun extractShareId(url: String): String {
        val startIndex = url.indexOf(SHARE_ID_MARKER)
        if (startIndex < 0) return ""
        val from = startIndex + SHARE_ID_MARKER.length
        var end = url.length
        for (separator in charArrayOf('?', '#', '/')) {
            val index = url.indexOf(separator, from)
            if (index in from until end) end = index
        }
        return url.substring(from, end)
    }

    /**
     * 从文本中提取提取码：优先取链接查询串（`?pwd=`），其次取正文关键字。
     *
     * @param text 用户粘贴的原始文本。
     * @return 4 位提取码；未找到时返回 null。
     */
    fun extractPassword(text: String): String? {
        val fromQuery = PASSWORD_QUERY_REGEX.find(text)?.groupValues?.getOrNull(1)
        if (!fromQuery.isNullOrBlank()) return fromQuery
        return PASSWORD_KEYWORD_REGEX.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }
}
