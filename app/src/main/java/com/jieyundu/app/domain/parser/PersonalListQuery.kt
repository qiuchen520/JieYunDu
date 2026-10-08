// 文件：PersonalListQuery.kt
// 职责：个人网盘目录列表查询参数的单一构造器（夸克 / UC 同构，仅 pr/fr 不同）
// 依赖：无（纯常量与 Map 构造）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser

/**
 * 个人网盘列表查询参数构造器（【修订 JYD-DEBT8-2026-10-07】去重）。
 *
 * 存在理由：`QuarkParser`、`UcParser`、`TempFolderManager` 三处各写了一份**字段完全相同**的
 * 查询参数（仅 `pr` / `fr` 两家取值不同），协议参数一旦调整就要改三处、且极易漂移。
 * 现收敛为单一实现，字段集依《抓包事实.md》§10.2（个人网盘文件 / 目录列表）。
 *
 * 说明：`pr` / `fr` 常量也收敛到此处（原先夸克侧在 Parser 与 TempFolderManager 各写一份）。
 */
object PersonalListQuery {

    /** 夸克 PC 平台固定参数。 */
    const val QUARK_PR = "ucpro"

    /** 夸克来源标识。 */
    const val QUARK_FR = "pc"

    /** UC PC 平台固定参数。 */
    const val UC_PR = "UCBrowser"

    /** UC 来源标识。 */
    const val UC_FR = "pc"

    /**
     * 构造个人网盘目录列表查询参数。
     *
     * @param pr 平台标识（[QUARK_PR] / [UC_PR]）。
     * @param fr 来源标识（两家均为 `pc`）。
     * @param pdirFid 目标目录 fid（根为 `0`）。
     * @return 查询参数键值对。
     */
    fun build(pr: String, fr: String, pdirFid: String): Map<String, String> = linkedMapOf(
        KEY_PR to pr,
        KEY_FR to fr,
        KEY_PDIR_FID to pdirFid,
        KEY_PAGE to FIRST_PAGE,
        KEY_SIZE to PAGE_SIZE,
        KEY_FETCH_TOTAL to ONE_VALUE,
        KEY_FETCH_SUB_DIRS to ZERO_VALUE,
        KEY_SORT to SORT
    )

    /** 首页页码。 */
    private const val FIRST_PAGE = "1"

    /** 每页条数（抓包为 100）。 */
    private const val PAGE_SIZE = "100"

    /** 布尔 / 计数占位值。 */
    private const val ONE_VALUE = "1"
    private const val ZERO_VALUE = "0"

    /** 排序（《抓包事实.md》§10.2）。 */
    private const val SORT = "file_type:asc,updated_at:desc"

    /** 查询参数名。 */
    private const val KEY_PR = "pr"
    private const val KEY_FR = "fr"
    private const val KEY_PDIR_FID = "pdir_fid"
    private const val KEY_PAGE = "_page"
    private const val KEY_SIZE = "_size"
    private const val KEY_FETCH_TOTAL = "_fetch_total"
    private const val KEY_FETCH_SUB_DIRS = "_fetch_sub_dirs"
    private const val KEY_SORT = "_sort"
}
