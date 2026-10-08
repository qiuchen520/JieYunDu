// 文件：BaiduLinkTest.kt
// 职责：百度分享链接解析（短码归一化 / 提取码）的单元测试（B3-1 · JYD-BAIDU-2026-10-05）
// 依赖：JUnit4、LinkExtractor
// 协议：AGPL-3.0

package com.jieyundu.app.domain.util

import com.jieyundu.app.domain.model.NetdiskType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 百度分享链接的提取规则单元测试。
 *
 * 关键规则（《抓包事实.md》§1）：百度分享短链形如 `/s/1xxxx`，
 * 其**实际分享短码不含前导 `1`**（`share/verify` 的 `surl` 与会话列表的 `shorturl` 都用它）。
 * 这条规则错一位，后续所有接口都会 `errno != 0`，因此单测锁死。
 */
class BaiduLinkTest {

    @Test
    fun `baidu share id strips leading one`() {
        assertEquals(
            "aBcDeF123",
            LinkExtractor.extractShareId("https://pan.baidu.com/s/1aBcDeF123")
        )
    }

    @Test
    fun `baidu share id keeps underscore and hyphen`() {
        assertEquals(
            "x_Y-z",
            LinkExtractor.extractShareId("https://pan.baidu.com/s/1x_Y-z")
        )
    }

    @Test
    fun `baidu link is detected as baidu`() {
        assertEquals(
            NetdiskType.BAIDU,
            LinkExtractor.detectType("https://pan.baidu.com/s/1aBcDeF123")
        )
    }

    @Test
    fun `baidu link with pwd query yields password`() {
        val link = LinkExtractor.extract("https://pan.baidu.com/s/1aBcDeF123?pwd=ab12")
        assertEquals("ab12", link?.password)
        assertEquals("aBcDeF123", link?.shareId)
    }

    @Test
    fun `baidu password from chinese keyword is extracted`() {
        // 关键字与全角冒号在源码里以转义书写（C5），此处运行期是正常中文。
        val text = "https://pan.baidu.com/s/1aBcDeF123 " + "\u63D0\u53D6\u7801\uFF1A" + "cd34"
        assertEquals("cd34", LinkExtractor.extract(text)?.password)
    }

    @Test
    fun `non baidu share id is not normalized`() {
        assertEquals(
            "1aBcDeF",
            LinkExtractor.extractShareId("https://pan.quark.cn/s/1aBcDeF")
        )
    }

    @Test
    fun `blank text yields no link`() {
        assertNull(LinkExtractor.extract("   "))
    }

    /**
     * 链接尾部紧跟中文句读时，链接本体与提取码都要正确（【JYD-DEBT5-2026-10-07】）。
     *
     * 背景：`LinkExtractor` 曾有一段「裁剪尾部标点」的防御式兜底，实为正则字符类不可能匹配
     * 到标点、恒不可达，已删除。本用例把真实行为钉住：标点由**正则本身**自然截断，
     * 既不进链接、也不影响后续关键字的提取码解析。
     */
    @Test
    fun `trailing punctuation is not part of the link`() {
        // 前置中文同样以转义书写（C5：.kt 源码不出现中文字面量）。
        val text = "\u770B\u8FD9\u4E2A https://pan.baidu.com/s/1aBcD_ef" + "\u3002" + " " +
            "\u63D0\u53D6\u7801\uFF1A" + "ab12"
        val link = LinkExtractor.extract(text)
        assertEquals("aBcD_ef", link?.shareId)
        assertFalse(
            link?.rawUrl?.endsWith("\u3002") ?: true,
            "trailing Chinese full stop must not be part of rawUrl"
        )
        assertEquals("ab12", link?.password)
    }

    /** 全角括号 / 逗号紧跟链接时同样只取链接本体。 */
    @Test
    fun `fullwidth bracket and comma are excluded`() {
        assertEquals(
            "https://pan.baidu.com/s/1aBcD_ef",
            LinkExtractor.extract("(https://pan.baidu.com/s/1aBcD_ef\uFF09\uFF0C").let { link ->
                link?.rawUrl
            }
        )
    }
}
