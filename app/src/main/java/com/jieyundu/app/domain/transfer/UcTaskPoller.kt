// 文件：UcTaskPoller.kt
// 职责：轮询 UC 转存任务，直至完成并返回转存后的新 fid 列表（照夸克 TaskPoller 同构）
// 依赖：UcApi、kotlinx.coroutines、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.domain.transfer

import com.jieyundu.app.domain.parser.uc.UcApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * UC 转存任务轮询器。
 *
 * 转存（`sharepage/save`）是**异步任务**：调用后仅返回 `task_id`，需轮询
 * `1/clouddrive/task` 直至任务完成，再取 `save_as.save_as_top_fids` 作为本账号新 fid。
 *
 * 完成判定（《抓包事实.md》§6.1④，三选一）：`finished_at > 0`、`status == 2`、
 * `task_status == 2`。
 *
 * @param api UC 接口。
 */
@Singleton
class UcTaskPoller @Inject constructor(
    private val api: UcApi
) {

    /**
     * 轮询直到任务完成，返回转存后的新 fid 列表。
     *
     * @param taskId 转存任务 ID。
     * @return 新 fid 列表；超时或失败返回空列表（由调用方判定为转存失败）。
     */
    suspend fun awaitSavedFids(taskId: String): List<String> {
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = api.getTask(buildTaskParams(taskId))
            if (response.code == SUCCESS_CODE) {
                val data = response.data
                if (data != null) {
                    val finished = data.finished_at > 0L ||
                        data.status == STATUS_FINISHED ||
                        data.task_status == STATUS_FINISHED
                    if (finished) {
                        return data.save_as?.save_as_top_fids.orEmpty()
                    }
                }
            }
            Timber.d("UcTaskPoller waiting task=%s attempt=%d", taskId, attempt + 1)
            delay(POLL_INTERVAL_MILLIS)
        }
        Timber.w("UcTaskPoller timed out for task %s", taskId)
        return emptyList()
    }

    /**
     * 构造 task 接口查询参数。
     *
     * @param taskId 转存任务 ID。
     * @return 查询参数键值对。
     */
    private fun buildTaskParams(taskId: String): Map<String, String> = mapOf(
        KEY_PR to UC_PR,
        KEY_FR to UC_FR,
        KEY_TASK_ID to taskId
    )

    private companion object {
        /** 成功状态码（实测为 0）。 */
        const val SUCCESS_CODE = 0

        /** 任务完成状态码。 */
        const val STATUS_FINISHED = 2

        /** 最大轮询次数。 */
        const val MAX_ATTEMPTS = 30

        /** 轮询间隔（毫秒）。 */
        const val POLL_INTERVAL_MILLIS = 1000L

        /** UC PC 平台固定查询参数。 */
        const val UC_PR = "UCBrowser"
        const val UC_FR = "pc"

        /** 查询参数名。 */
        const val KEY_PR = "pr"
        const val KEY_FR = "fr"
        const val KEY_TASK_ID = "task_id"
    }
}