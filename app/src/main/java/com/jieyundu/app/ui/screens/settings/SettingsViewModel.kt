// 文件：SettingsViewModel.kt
// 职责：设置页数据——已支持网盘、默认并发分片数、应用版本
// 依赖：ParserRegistry、DownloadTask、BuildConfig、Hilt
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.jieyundu.app.BuildConfig
import com.jieyundu.app.domain.downloader.DownloadTask
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.parser.ParserRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 设置页 ViewModel。
 *
 * 说明：当前阶段只提供只读信息（支持范围、默认并发、版本）；
 * 可交互的设置项（并发数调整、清空历史等）待后续阶段接入 Repository。 *
 * @param parserRegistry 解析器注册表，用于读取已接入的网盘类型。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    parserRegistry: ParserRegistry
) : ViewModel() {

    /** 已接入的网盘类型（《要求.md》4.1 支持范围）。 */
    val supportedTypes: List<NetdiskType> = parserRegistry.registeredTypes()

    /** 应用版本名。 */
    val versionName: String = BuildConfig.VERSION_NAME

    /** 默认并发分片数（4.2：默认 8）。 */
    val defaultChunkCount: Int = DownloadTask.DEFAULT_CHUNK_COUNT

    /** 并发分片上限（4.2：上限 32）。 */
    val maxChunkCount: Int = DownloadTask.MAX_CHUNK_COUNT
}
