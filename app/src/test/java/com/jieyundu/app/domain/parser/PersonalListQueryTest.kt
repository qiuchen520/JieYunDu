// 文件：PersonalListQueryTest.kt
// 职责：锁定个人网盘列表查询参数的字段集、取值与两家平台常量（防止再退化回「各写一份」）
// 依赖：JUnit4、PersonalListQuery
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PersonalListQuery] 的回归测试（【JYD-DEBT8-2026-10-07】去重后的保护网）。
 *
 * 背景：该参数集原在 `QuarkParser` / `UcParser` / `TempFolderManager` / `TaskPoller` 四处
 * 各写一份，只靠「记得同步」维持一致。收敛为一处后，本测试把字段集与取值**钉死**：
 * 任何人改动共享构造器都会立刻看到断言失败，而不是等到某家网盘的目录列表静默返回空。
 *
 * 事实来源：《抓包事实.md》§10.2（个人网盘文件 / 目录列表）。
 */
class PersonalListQueryTest {

    /** 夸克：平台常量与字段取值须与《抓包事实.md》§10.2 一致。 */
    @Test
    fun quarkQueryMatchesPacketFacts() {
        val params = PersonalListQuery.build(
            pr = PersonalListQuery.QUARK_PR,
            fr = PersonalListQuery.QUARK_FR,
            pdirFid = "0"
        )
        assertEquals(EXPECTED_KEYS, params.keys.toList())
        assertEquals("ucpro", params["pr"])
        assertEquals("pc", params["fr"])
        assertEquals("0", params["pdir_fid"])
        assertEquals("1", params["_page"])
        assertEquals("100", params["_size"])
        assertEquals("1", params["_fetch_total"])
        assertEquals("0", params["_fetch_sub_dirs"])
        assertEquals("file_type:asc,updated_at:desc", params["_sort"])
    }

    /** UC：与夸克同构，**只有 `pr` 不同**（这条断言就是「同构」这一事实本身）。 */
    @Test
    fun ucQueryDiffersFromQuarkOnlyInPr() {
        val quark = PersonalListQuery.build(
            pr = PersonalListQuery.QUARK_PR,
            fr = PersonalListQuery.QUARK_FR,
            pdirFid = "root-fid"
        )
        val uc = PersonalListQuery.build(
            pr = PersonalListQuery.UC_PR,
            fr = PersonalListQuery.UC_FR,
            pdirFid = "root-fid"
        )
        assertEquals("UCBrowser", uc["pr"])
        assertEquals("pc", uc["fr"])
        assertEquals(setOf("pr"), quark.keys.filter { key -> quark[key] != uc[key] }.toSet())
        assertEquals(quark.keys.toList(), uc.keys.toList())
    }

    /** 子目录 fid 必须原样透传（目录浏览靠它，写错会退回根目录）。 */
    @Test
    fun pdirFidIsPassedThroughVerbatim() {
        val params = PersonalListQuery.build(
            pr = PersonalListQuery.QUARK_PR,
            fr = PersonalListQuery.QUARK_FR,
            pdirFid = "fid-abc-123"
        )
        assertEquals("fid-abc-123", params["pdir_fid"])
    }

    private companion object {
        /** 字段顺序即请求参数顺序（与抓包一致，便于逐字比对）。 */
        val EXPECTED_KEYS = listOf(
            "pr",
            "fr",
            "pdir_fid",
            "_page",
            "_size",
            "_fetch_total",
            "_fetch_sub_dirs",
            "_sort"
        )
    }
}
