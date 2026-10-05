// 文件：BaiduErrnoRulesTest.kt
// 职责：百度两阶段 errno 判定的单元测试（JYD-BAIDU-ERRNO-2026-10-05）
// 依赖：JUnit4、BaiduErrnoRules
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.baidu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 百度 `errno` 判定规则测试。
 *
 * 重点锁死三件最容易踩混的事：
 * 1. **-12 才是「提取码错误」**（`share/verify` 阶段）；
 * 2. **「需要提取码」是组合判定**（无 `sekey` 且列分享失败），**没有专属 errno**；
 *    `-6` 要单独提示「需要提取码**或**登录」，否则用户会白找密码；
 * 3. **`errno = 2` 不是提取码问题**（子目录缺 `BDCLND`），绝不能被归到「需要提取码」。
 */
class BaiduErrnoRulesTest {

    // ---- 阶段一：share/verify ----

    @Test
    fun `verify errno minus twelve is wrong password`() {
        assertEquals(
            BaiduErrnoRules.CODE_WRONG_PASSWORD,
            BaiduErrnoRules.classifyVerify(BaiduErrnoRules.WRONG_PASSWORD)
        )
    }

    @Test
    fun `verify errno zero means success`() {
        assertNull(BaiduErrnoRules.classifyVerify(BaiduErrnoRules.OK))
    }

    @Test
    fun `verify other errno is verify failure not wrong password`() {
        assertEquals(
            BaiduErrnoRules.CODE_VERIFY_FAILED,
            BaiduErrnoRules.classifyVerify(-9)
        )
    }

    @Test
    fun `verify expired share is reported as expired`() {
        assertEquals(
            BaiduErrnoRules.CODE_SHARE_EXPIRED,
            BaiduErrnoRules.classifyVerify(BaiduErrnoRules.SHARE_EXPIRED)
        )
    }

    // ---- 阶段二：xpan/share?method=list（组合判定）----

    @Test
    fun `list without sekey and not logged in needs password or login`() {
        // -6：未登录 / 身份验证失败 —— 文案必须并列提到登录，避免用户白找提取码。
        assertEquals(
            BaiduErrnoRules.CODE_NEED_PASSWORD_OR_LOGIN,
            BaiduErrnoRules.classifyList(
                errno = BaiduErrnoRules.NOT_LOGGED_IN,
                hasSekey = false,
                isSubDirectory = false
            )
        )
    }

    @Test
    fun `list without sekey and other errno needs password`() {
        assertEquals(
            BaiduErrnoRules.CODE_NEED_PASSWORD,
            BaiduErrnoRules.classifyList(errno = -9, hasSekey = false, isSubDirectory = false)
        )
    }

    @Test
    fun `list with sekey and not logged in is detail failure not need password`() {
        // 已带 sekey 仍失败：不能再提示「需要提取码」。
        assertEquals(
            BaiduErrnoRules.CODE_DETAIL_FAILED,
            BaiduErrnoRules.classifyList(
                errno = BaiduErrnoRules.NOT_LOGGED_IN,
                hasSekey = true,
                isSubDirectory = false
            )
        )
    }

    @Test
    fun `sub directory errno two is auth failure not need password`() {
        // 关键回归：errno=2 是缺 BDCLND / 子目录认证失败，不能被当成提取码问题。
        assertEquals(
            BaiduErrnoRules.CODE_SUB_DIR_AUTH_FAILED,
            BaiduErrnoRules.classifyList(
                errno = BaiduErrnoRules.MISSING_BDCLND,
                hasSekey = true,
                isSubDirectory = true
            )
        )
        assertEquals(
            BaiduErrnoRules.CODE_SUB_DIR_AUTH_FAILED,
            BaiduErrnoRules.classifyList(
                errno = BaiduErrnoRules.MISSING_BDCLND,
                hasSekey = false,
                isSubDirectory = true
            )
        )
    }

    @Test
    fun `expired share is reported at list stage`() {
        assertEquals(
            BaiduErrnoRules.CODE_SHARE_EXPIRED,
            BaiduErrnoRules.classifyList(errno = 403, hasSekey = true, isSubDirectory = false)
        )
    }

    @Test
    fun `missing file is reported at list stage`() {
        assertEquals(
            BaiduErrnoRules.CODE_FILE_NOT_FOUND,
            BaiduErrnoRules.classifyList(errno = 31066, hasSekey = false, isSubDirectory = false)
        )
    }

    @Test
    fun `list errno zero means success`() {
        assertNull(
            BaiduErrnoRules.classifyList(
                errno = BaiduErrnoRules.OK,
                hasSekey = false,
                isSubDirectory = false
            )
        )
    }

    @Test
    fun `public share without sekey succeeds`() {
        // 公共分享（无提取码）跳过 verify，直接列分享成功 —— 抓包实证 errno=0。
        assertNull(
            BaiduErrnoRules.classifyList(
                errno = BaiduErrnoRules.OK,
                hasSekey = false,
                isSubDirectory = true
            )
        )
    }
}
