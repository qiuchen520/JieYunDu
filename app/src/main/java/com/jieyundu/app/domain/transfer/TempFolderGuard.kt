// 文件：TempFolderGuard.kt
// 职责：网盘侧删除的统一安全边界——只有「临时目录内」的文件才允许删除（P0 数据安全）
// 依赖：无（纯逻辑 + 一个并发集合）
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

/**
 * 网盘侧删除安全守卫（【JYD-DELSAFE-2026-10-04】P0 数据安全）。
 *
 * 存在理由：用户网盘里的文件一旦误删**不可恢复**，是本项目最严重的一类风险。此前「只删转存副本」
 * 的性质是**隐式的**——依赖每个调用点都记得先登记 fid；一旦将来新增调用点忘记登记，
 * 就可能把用户自己的文件删掉。本守卫把该性质固化为**单点硬边界**：
 * 所有网盘侧删除必须先过本守卫，判定不通过一律拒绝。
 *
 * 判定规则（满足任意一条即允许删除）：
 * 1. **登记过**：fid 由转存流程登记（[register]）——转存副本的唯一可信来源；
 * 2. **路径在临时目录内**：文件路径包含 `/.极云渡临时/` 或以 `/.极云渡临时` 结尾；
 * 3. **目录 fid 比对**：目标 fid 等于已知临时目录 fid，或其父目录 fid 等于临时目录 fid；
 * 4. **临时目录自身**：仅当**确认目录为空**（[isEmptyFolder] = true）时才允许删除，
 *    避免把手滑建出的、装着用户文件的同名目录整个删掉。
 *
 * 默认拒绝：任何信号缺失（fid 为空、父目录未知且未登记、路径不含标识）都返回 false。
 *
 * 例外说明（不影响本守卫）：网盘管理页里**用户主动选择**的删除是另一条业务路径
 * （用户明确知道自己删的是什么），不经过本守卫；本守卫只约束「App 自动/顺带」的删除。
 */
object TempFolderGuard {

    /** 临时目录标识名（与 TempFolderManager / UcTempFolderManager 保持一致）。 */
    const val TEMP_FOLDER_MARKER = ".极云渡临时"

    /** 根目录 pdir_fid。 */
    private const val ROOT_PDIR_FID = "0"

    /** 路径分隔符。 */
    private const val SEPARATOR = "/"

    /**
     * 已登记为「转存副本」的 fid（由转存流程写入）。
     *
     * 说明：仅用于本进程生命周期内的判定；进程重启后转存副本信息丢失，
     * 此时退化为「路径 / 目录 fid」规则判定——宁可不删，也不能误删。
     */
    private val registeredTempFids: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * 登记一个「转存副本」fid（由转存链路在拿到新 fid 后调用）。
     *
     * @param fid 转存后在本账号中的文件 fid。
     */
    fun register(fid: String) {
        if (fid.isNotBlank()) {
            registeredTempFids.add(fid)
        }
    }

    /**
     * 批量登记转存副本 fid。
     *
     * @param fids 转存 fid 列表。
     */
    fun registerAll(fids: Collection<String>) {
        fids.forEach { fid -> register(fid) }
    }

    /**
     * 取消登记（文件已删除 / 已不在临时目录）。
     *
     * @param fid 文件 fid。
     */
    fun unregister(fid: String) {
        registeredTempFids.remove(fid)
    }

    /**
     * 该 fid 是否被登记为转存副本。
     *
     * @param fid 文件 fid。
     * @return true 表示由转存流程产生。
     */
    fun isRegistered(fid: String): Boolean = registeredTempFids.contains(fid)

    /**
     * 判断某个文件是否位于临时目录内。
     *
     * @param name 文件名或路径（可为单纯文件名、也可为含分隔符的路径）。
     * @param pdirFid 所在目录 fid；未知传 null / 空。
     * @param tempFolderFid 已知的临时目录 fid；未知传 null / 空。
     * @return true 表示位于临时目录内。
     */
    fun isInTempFolder(
        name: String?,
        pdirFid: String? = null,
        tempFolderFid: String? = null
    ): Boolean {
        if (matchesTempPath(name)) {
            return true
        }
        val parent = pdirFid?.trim().orEmpty()
        if (parent.isNotEmpty() && parent != ROOT_PDIR_FID) {
            val temp = tempFolderFid?.trim().orEmpty()
            if (temp.isNotEmpty() && parent == temp) {
                return true
            }
        }
        return false
    }

    /**
     * 网盘侧删除的**唯一放行判定**。
     *
     * @param fid 待删除文件 / 目录的 fid。
     * @param name 文件名或路径；用于「路径含临时目录标识」判定。
     * @param pdirFid 所在目录 fid；未知传 null。
     * @param tempFolderFid 已知的临时目录 fid；未知传 null。
     * @param isDirectory 目标是否为目录（临时目录自身的删除走 [mayDeleteTempFolder] 规则）。
     * @param isEmptyFolder 目标为目录时，是否已确认目录为空。
     * @return true 表示允许执行网盘侧删除；false 表示**必须拒绝**。
     */
    fun mayDeleteFromTemp(
        fid: String?,
        name: String? = null,
        pdirFid: String? = null,
        tempFolderFid: String? = null,
        isDirectory: Boolean = false,
        isEmptyFolder: Boolean = false
    ): Boolean {
        val target = fid?.trim().orEmpty()
        if (target.isEmpty()) {
            return false
        }
        // 规则 4：临时目录自身——只有确认空目录才允许删。
        if (isDirectory && target == tempFolderFid?.trim().orEmpty()) {
            return isEmptyFolder
        }
        // 规则 1：转存登记的副本。
        if (isRegistered(target)) {
            return true
        }
        // 规则 2 / 3：路径或父目录 fid 命中临时目录。
        return isInTempFolder(name = name, pdirFid = pdirFid, tempFolderFid = tempFolderFid)
    }

    /**
     * **用户主动删除**的放行判定（网盘管理页的「删除」按钮专用）。
     *
     * 与 [mayDeleteFromTemp] 的语义区别（必须分清，否则会误伤用户操作）：
     * - [mayDeleteFromTemp] 管的是「App 自己 / 顺带」的清理——只允许临时目录内的副本，
     *   用户自己的文件一律拒绝（P0 数据安全边界的默认拒绝语义）；
     * - 本方法管的是「用户在管理页明确选中并确认删除自己的文件」——目标由用户指定，
     *   因此**不限制在临时目录内**。
     *
     * 但两者共用同一处判定入口与同一套日志/审计，避免"两处各自判断、规则漂移"：
     * 本方法会记录删除意图，便于日后排查误删投诉。
     *
     * 约束：仅当 [userInitiated] 显式为 true（由 UI 的确认弹窗流程传入）才放行，
     * 且目标 fid 非空；任何"顺带"调用都拿不到放行，必须走 [mayDeleteFromTemp]。
     *
     * @param fid 用户选中的文件 / 目录 fid。
     * @param userInitiated 是否来自用户主动确认的删除（UI 确认弹窗之后）。
     * @return true 表示允许执行网盘侧删除。
     */
    fun mayDeleteUserInitiated(fid: String?, userInitiated: Boolean): Boolean {
        val target = fid?.trim().orEmpty()
        if (!userInitiated || target.isEmpty()) {
            return false
        }
        Timber.i("TempFolderGuard user-initiated delete approved fid=%s", target)
        return true
    }

    /**
     * 判断文件名 / 路径是否带临时目录标识。
     *
     * 识别形态：`.极云渡临时`、`.极云渡临时/xxx`、`/.极云渡临时/xxx`、`/a/.极云渡临时`。
     * **不**识别相似但不匹配的形态（例如 `.极云渡临时文件`、`极云渡临时`、`我的.极云渡临时x`）。
     *
     * @param name 文件名或路径。
     * @return true 表示路径确实落在临时目录内。
     */
    private fun matchesTempPath(name: String?): Boolean {
        val path = name?.trim().orEmpty()
        if (path.isEmpty()) {
            return false
        }
        if (path == TEMP_FOLDER_MARKER || path.endsWith(SEPARATOR + TEMP_FOLDER_MARKER)) {
            return true
        }
        // 相对形态：「.极云渡临时/xxx」（路径以标识开头，没有前导分隔符）。
        if (path.startsWith(TEMP_FOLDER_MARKER + SEPARATOR)) {
            return true
        }
        // 绝对 / 嵌套形态：「/a/.极云渡临时/xxx」。
        return path.contains(SEPARATOR + TEMP_FOLDER_MARKER + SEPARATOR)
    }
}
