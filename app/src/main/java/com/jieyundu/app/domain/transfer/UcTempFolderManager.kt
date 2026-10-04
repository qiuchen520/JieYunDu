// 文件：UcTempFolderManager.kt
// 职责：管理 UC 本账号临时目录 `.极云渡临时`——查找 / 创建 / 登记转存 fid / 下载后清理 / 空目录删除
// 依赖：UcApi、javax.inject、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import com.jieyundu.app.domain.parser.uc.UcApi
import com.jieyundu.app.domain.parser.uc.UcCreateFolderRequest
import com.jieyundu.app.domain.parser.uc.UcDeleteRequest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import com.jieyundu.app.domain.model.NetdiskType
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UC 临时转存目录管理器（照夸克 [TempFolderManager] 同构，接口域名/参数对齐《抓包事实.md》§2 / §9 / §10）。
 *
 * 职责：
 * 1. **查**：解析前查根目录下是否已有 `.极云渡临时`，命中则缓存其 fid；
 * 2. **建**：没有则调用「创建目录 API」，拿到新目录 fid 并缓存复用；
 * 3. **登记**：记录每次转存产生的本账号文件 fid（供清理）；
 * 4. **清理**：下载完成后删除该文件的转存副本；临时目录空了则一并删除；
 * 5. **兜底**：[cleanupAll] 供设置页「手动清理」（不新增目录）。
 *
 * 线程安全：fid 缓存为 [Volatile]（可被置空），待清理集合为并发集合。
 *
 * @param api UC 接口（查询 / 建目录 / 删除）。
 * @param transferRecords 转存副本登记端口（【JYD-P1-2026-10-04】P1-3：重启后仍能识别临时副本）。
 *
 * 注意：`pendingCleanup` 是本进程内的**待清理集合**（供「手动清理」使用），
 * 其持久化副本存于 [transferRecords]；进程重启后由 Application 启动时重新水合。
 */
@Singleton
class UcTempFolderManager @Inject constructor(
    private val api: UcApi,
    private val transferRecords: TransferRecordPort
) {

    /** 持久化写入用的作用域（登记落库不阻塞下载主链路）。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 待清理的转存文件 fid 集合。 */
    private val pendingCleanup: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 已解析到的临时目录 fid 缓存；未解析或已被删除时为 null。 */
    @Volatile
    private var cachedTempFid: String? = null

    /**
     * 确保临时目录存在，返回其 fid。
     *
     * @return 临时目录 fid；查询与创建均失败时返回 null（调用方应回退到根目录 `0`）。
     */
    suspend fun ensureTempFolderFid(): String? {
        cachedTempFid?.let { fid -> return fid }
        findTempFolderFid()?.let { fid -> return fid }

        val created = runCatching {
            api.createFolder(
                UcCreateFolderRequest(
                    pdir_fid = ROOT_PDIR_FID,
                    file_name = TEMP_FOLDER_NAME
                )
            )
        }.getOrElse { error ->
            Timber.w(error, "UcTempFolderManager create folder failed")
            return null
        }
        val fid = created.data?.fid?.takeIf { created.code == SUCCESS_CODE && it.isNotBlank() }
        if (fid == null) {
            Timber.w("UcTempFolderManager create folder returned no fid, code=%d", created.code)
            return null
        }
        cachedTempFid = fid
        // 【JYD-DELSAFE-2026-10-04】登记临时目录自身，供删除守卫做目录 fid 比对。
        TempFolderGuard.register(fid)
        Timber.i("UcTempFolderManager created temp folder fid=%s", fid)
        return fid
    }

    /**
     * 仅**查找**临时目录（不创建），用于清理场景避免误建。
     *
     * @return 临时目录 fid；不存在时返回 null。
     */
    suspend fun findTempFolderFid(): String? {
        val listed = runCatching { api.listFiles(buildListParams(ROOT_PDIR_FID)) }
            .getOrElse { error ->
                Timber.w(error, "UcTempFolderManager list root failed")
                return null
            }
        if (listed.code != SUCCESS_CODE) {
            Timber.w("UcTempFolderManager list root code=%d", listed.code)
            return null
        }
        val folder = listed.data?.list.orEmpty().firstOrNull { entry ->
            entry.dir && entry.file_name == TEMP_FOLDER_NAME
        }
        val fid = folder?.fid
        if (!fid.isNullOrBlank()) {
            cachedTempFid = fid
            // 【JYD-DELSAFE-2026-10-04】命中已存在的临时目录时同样登记，供删除守卫比对。
            TempFolderGuard.register(fid)
        }
        return fid
    }

    /**
     * 登记一批待清理的转存文件 fid。
     *
     * @param fids 转存后得到的本账号文件 fid 列表；空白项忽略。
     */
    fun recordPendingCleanup(fids: List<String>) {
        fids.filter { fid -> fid.isNotBlank() }.forEach { fid ->
            if (pendingCleanup.add(fid)) {
                // 【JYD-DELSAFE-2026-10-04】同步登记到删除守卫：转存副本是唯一可信的
                // 「可删」来源，守卫据此放行；未登记的 fid 一律拒绝删除。
                TempFolderGuard.register(fid)
                // 【JYD-P1-2026-10-04】P1-3：登记落库，供重启后恢复（重启后内存集合为空）。
                scope.launch {
                    runCatching {
                        transferRecords.upsert(
                            TransferRecord(
                                fid = fid,
                                dirFid = cachedTempFid,
                                netdiskType = NetdiskType.UC.name
                            )
                        )
                    }.onFailure { error ->
                        // 落库失败不阻断下载：仅本次进程内有效，并记日志（D15）。
                        Timber.w(error, "UcTempFolderManager persist transfer record failed fid=%s", fid)
                    }
                }
                Timber.i("UcTempFolderManager pending cleanup fid=%s", fid)
            }
        }
    }

    /**
     * 取出当前全部待清理 fid 的快照。
     *
     * @return 待清理 fid 列表。
     */
    fun pendingCleanupFids(): List<String> = pendingCleanup.toList()

    /**
     * 移除已清理的 fid。
     *
     * @param fids 已完成清理的 fid 集合。
     */
    fun remove(fids: Collection<String>) {
        fids.forEach { fid -> pendingCleanup.remove(fid) }
    }

    /**
     * 删除临时目录中的某个转存文件，并在目录变空时删除该目录本身。
     *
     * 【JYD-DELSAFE-2026-10-04】P0 数据安全：删除前**必须**过 [TempFolderGuard]，
     * 未登记且不在临时目录内的 fid 一律拒绝，绝不触碰用户自己的文件。
     *
     * @param fid 转存文件在本账号中的 fid。
     * @return true 表示删除请求已被接受（或文件此前已不存在）。
     */
    suspend fun deleteFromTemp(fid: String): Boolean {
        if (fid.isBlank()) return false
        val tempFid = cachedTempFid ?: findTempFolderFid()
        if (!TempFolderGuard.mayDeleteFromTemp(fid = fid, tempFolderFid = tempFid)) {
            Timber.w("UcTempFolderManager delete refused by guard: fid=%s", fid)
            return false
        }
        val deleted = deleteFids(listOf(fid))
        if (!deleted) return false
        pendingCleanup.remove(fid)
        TempFolderGuard.unregister(fid)
        // 【JYD-P1-2026-10-04】P1-3：同步移除持久化登记（已删副本不必再被清理）。
        runCatching { transferRecords.delete(fid) }
            .onFailure { error -> Timber.w(error, "UcTempFolderManager delete transfer record failed fid=%s", fid) }
        if (!tempFid.isNullOrBlank()) {
            deleteTempFolderIfEmpty(tempFid)
        }
        return true
    }

    /**
     * 手动清理：删除当前登记的全部待清理文件，并删除空的临时目录。
     *
     * 说明：
     * - 本方法**不会创建**临时目录（避免"清理"反而新建）；
     * - 【JYD-DELSAFE-2026-10-04】逐条过 [TempFolderGuard]：即便集合里混入非转存 fid，
     *   也只会被拒绝而不会误删用户文件。
     *
     * @return 成功删除的文件数量。
     */
    suspend fun cleanupAll(): Int {
        val allFids = pendingCleanupFids()
        val tempFid = cachedTempFid ?: findTempFolderFid()
        val fids = allFids.filter { fid ->
            val allowed = TempFolderGuard.mayDeleteFromTemp(fid = fid, tempFolderFid = tempFid)
            if (!allowed) {
                Timber.w("UcTempFolderManager cleanupAll skipped fid=%s (guard refused)", fid)
            }
            allowed
        }
        val accepted = if (fids.isEmpty()) {
            true
        } else {
            deleteFids(fids)
        }
        if (accepted) {
            remove(fids)
            fids.forEach { fid -> TempFolderGuard.unregister(fid) }
            // 【JYD-P1-2026-10-04】P1-3：同步清理持久化登记。
            fids.forEach { fid ->
                runCatching { transferRecords.delete(fid) }
                    .onFailure { error ->
                        Timber.w(error, "UcTempFolderManager delete transfer record failed")
                    }
            }
        }
        if (!tempFid.isNullOrBlank()) {
            deleteTempFolderIfEmpty(tempFid)
        }
        Timber.i("UcTempFolderManager cleanupAll deleted=%d", fids.size)
        return fids.size
    }

    /**
     * 若临时目录为空则删除它，并清空 fid 缓存。
     *
     * @param tempFid 临时目录 fid。
     */
    private suspend fun deleteTempFolderIfEmpty(tempFid: String) {
        val listed = runCatching { api.listFiles(buildListParams(tempFid)) }
            .getOrElse { error ->
                Timber.w(error, "UcTempFolderManager list temp folder failed")
                return
            }
        if (listed.code != SUCCESS_CODE || listed.data?.list.orEmpty().isNotEmpty()) {
            return
        }
        // 【JYD-DELSAFE-2026-10-04】临时目录自身也过守卫：只有「确认空目录」才允许删除。
        val allowed = TempFolderGuard.mayDeleteFromTemp(
            fid = tempFid,
            name = TEMP_FOLDER_NAME,
            tempFolderFid = tempFid,
            isDirectory = true,
            isEmptyFolder = true
        )
        if (!allowed) {
            Timber.w("UcTempFolderManager delete temp folder refused by guard: fid=%s", tempFid)
            return
        }
        if (deleteFids(listOf(tempFid))) {
            cachedTempFid = null
            TempFolderGuard.unregister(tempFid)
            Timber.i("UcTempFolderManager removed empty temp folder fid=%s", tempFid)
        }
    }

    /**
     * 调用删除接口。
     *
     * @param fids 待删除 fid 列表。
     * @return true 表示服务端接受（`code == 0`）。
     */
    private suspend fun deleteFids(fids: List<String>): Boolean {
        if (fids.isEmpty()) return true
        val response = runCatching { api.deleteFiles(UcDeleteRequest(filelist = fids)) }
            .getOrElse { error ->
                Timber.w(error, "UcTempFolderManager delete failed")
                return false
            }
        if (response.code != SUCCESS_CODE) {
            Timber.w("UcTempFolderManager delete code=%d", response.code)
            return false
        }
        return true
    }

    /**
     * 构造个人网盘列表查询参数（根目录 / 指定目录通用）。
     *
     * @param pdirFid 目标目录 fid。
     * @return 查询参数键值对。
     */
    private fun buildListParams(pdirFid: String): Map<String, String> = mapOf(
        KEY_PR to UC_PR,
        KEY_FR to UC_FR,
        KEY_PDIR_FID to pdirFid,
        KEY_PAGE to FIRST_PAGE,
        KEY_SIZE to PAGE_SIZE,
        KEY_FETCH_TOTAL to ONE,
        KEY_FETCH_SUB_DIRS to ZERO,
        KEY_SORT to LIST_SORT
    )

    private companion object {
        /** 临时目录名（项目自定义，避免与用户目录撞名）。 */
        const val TEMP_FOLDER_NAME = ".极云渡临时"

        /** 根目录 pdir_fid。 */
        const val ROOT_PDIR_FID = "0"

        /** 成功状态码。 */
        const val SUCCESS_CODE = 0

        /** UC PC 平台固定查询参数。 */
        const val UC_PR = "UCBrowser"
        const val UC_FR = "pc"

        /** 列表分页与排序固定参数（《抓包事实.md》§10.2）。 */
        const val FIRST_PAGE = "1"
        const val PAGE_SIZE = "100"
        const val ONE = "1"
        const val ZERO = "0"
        const val LIST_SORT = "file_type:asc,updated_at:desc"

        /** 查询参数名。 */
        const val KEY_PR = "pr"
        const val KEY_FR = "fr"
        const val KEY_PDIR_FID = "pdir_fid"
        const val KEY_PAGE = "_page"
        const val KEY_SIZE = "_size"
        const val KEY_FETCH_TOTAL = "_fetch_total"
        const val KEY_FETCH_SUB_DIRS = "_fetch_sub_dirs"
        const val KEY_SORT = "_sort"
    }
}