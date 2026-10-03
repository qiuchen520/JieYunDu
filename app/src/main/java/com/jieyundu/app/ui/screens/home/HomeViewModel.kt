// 文件：HomeViewModel.kt
// 职责：首页解析逻辑——提取链接、路由解析器、把文件投递给下载引擎
// 依赖：ParserRegistry、LinkExtractor、DownloadEngine、DownloadSessionRegistry、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.R
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadTask
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.model.ShareLink
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.ParserRegistry
import com.jieyundu.app.domain.util.LinkExtractor
import com.jieyundu.app.ui.screens.download.DownloadSessionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 首页 ViewModel。
 *
 * 职责（《要求.md》4.1）：
 * 1. 承接输入框文本，调用 [LinkExtractor] 提取分享链接与提取码；
 * 2. 经 [ParserRegistry] 路由到对应解析器执行解析；
 * 3. 把用户选中的文件交给 [DownloadEngine] 分片下载。
 *
 * 线程约束（C9）：耗时工作显式调度到 [Dispatchers.IO]；[CancellationException] 一律原样抛出（C3）。
 *
 * @param parserRegistry 解析器注册表。
 * @param downloadEngine 分片下载引擎。
 * @param downloadSessionRegistry 会话内「任务 ID → 文件名」登记表（下载列表展示用）。
 * @param appContext 应用上下文，仅用于推导下载落盘目录。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val parserRegistry: ParserRegistry,
    private val downloadEngine: DownloadEngine,
    private val downloadSessionRegistry: DownloadSessionRegistry,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    /** 首页 UI 状态流。 */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 最近一次待解析的链接；用于提取码弹窗提交后带着密码重新解析（阶段 8 整改）。 */
    private var pendingLink: ShareLink? = null

    /**
     * 更新输入框文本，并清除上一次的本地校验错误。
     *
     * @param text 最新的输入文本。
     */
    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputLink = text, errorRes = null)
    }

    /**
     * 更新单独填写的提取码（选填）。
     *
     * @param text 最新的提取码文本。
     */
    fun onCodeChange(text: String) {
        _uiState.value = _uiState.value.copy(inputCode = text)
    }

    /**
     * 执行解析（阶段 8 整改：把「需要提取码」转为弹窗，不再中断流程）。
     *
     * 说明：本方法非 suspend；内部协程捕获 [IOException] / [SerializationException] 并转成
     * [ParseResult.Error]，其余异常交回协程框架；[CancellationException] 不吞（C3）。
     */
    fun parse() {
        val link = LinkExtractor.extract(_uiState.value.inputLink)
        if (link == null) {
            _uiState.value = _uiState.value.copy(
                result = null,
                errorRes = R.string.parse_error_unrecognized,
                passwordPrompt = false,
                passwordErrorRes = null
            )
            return
        }
        val parser = parserRegistry.findParser(link.type)
        if (parser == null) {
            _uiState.value = _uiState.value.copy(
                result = null,
                errorRes = R.string.parse_error_unsupported,
                passwordPrompt = false,
                passwordErrorRes = null
            )
            return
        }
        startParse(
            link = link,
            parser = parser,
            password = link.password ?: _uiState.value.inputCode.ifBlank { null }
        )
    }

    /**
     * 启动一次解析。
     *
     * 说明（整改二）：链接未携带提取码时 `password` 为 null，照常请求；仅当服务器明确
     * 返回「需要提取码」时才置 [HomeUiState.passwordPrompt] 弹出输入框。
     *
     * @param link 待解析链接。
     * @param parser 路由到的解析器。
     * @param password 本次使用的提取码；可为 null。
     */
    private fun startParse(link: ShareLink, parser: NetdiskParser, password: String?) {
        pendingLink = link
        _uiState.value = _uiState.value.copy(
            isParsing = true,
            result = null,
            errorRes = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                parser.parse(link.rawUrl, password)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "HomeViewModel parse failed: network error")
                ParseResult.Error(link.type, CODE_NETWORK, CODE_NETWORK)
            } catch (serialization: SerializationException) {
                Timber.e(serialization, "HomeViewModel parse failed: unexpected response body")
                ParseResult.Error(link.type, CODE_PROTOCOL, CODE_PROTOCOL)
            }
            _uiState.value = when {
                // 服务器要求提取码 → 弹出输入框，流程不中断
                result is ParseResult.NeedPassword -> _uiState.value.copy(
                    isParsing = false,
                    result = null,
                    passwordPrompt = true,
                    passwordErrorRes = null
                )
                // 提取码错误 → 弹窗保留并提示重试
                result is ParseResult.Error && result.code == CODE_WRONG_PASSWORD ->
                    _uiState.value.copy(
                        isParsing = false,
                        result = null,
                        passwordPrompt = true,
                        passwordErrorRes = R.string.password_error_retry
                    )
                else -> _uiState.value.copy(
                    isParsing = false,
                    result = result,
                    passwordPrompt = false,
                    passwordErrorRes = null
                )
            }
        }
    }

    /**
     * 弹窗中提交提取码后，带着提取码重新发起解析（阶段 8 整改）。
     *
     * @param password 用户输入的提取码。
     */
    fun submitPassword(password: String) {
        val link = pendingLink ?: return
        val parser = parserRegistry.findParser(link.type) ?: return
        startParse(link = link, parser = parser, password = password)
    }

    /** 关闭提取码弹窗（用户取消）。 */
    fun dismissPasswordPrompt() {
        pendingLink = null
        _uiState.value = _uiState.value.copy(
            passwordPrompt = false,
            passwordErrorRes = null
        )
    }

    /**
     * 把解析出的单个文件投递到下载引擎。
     *
     * 说明：夸克解析器（2026-10-03 接口实测后）已能为真实文件回填 `downloadUrl`；
     * 未拿到直链（如文件夹或无直链的条目）时只记录日志并跳过，避免产生脏任务。
     *
     * @param file 用户点击下载的文件条目。
     */
    fun download(file: FileInfo) {
        val url = file.downloadUrl
        if (url.isNullOrBlank()) {
            Timber.w("HomeViewModel download skipped: direct link not ready for %s", file.fileName)
            return
        }
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(
            taskId = taskId,
            url = url,
            fileName = file.fileName,
            fileSize = file.fileSize,
            savePath = File(directory, file.fileName).absolutePath
        )
        downloadSessionRegistry.remember(taskId, file.fileName, task.savePath)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadEngine.start(task)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "HomeViewModel failed to start download for %s", file.fileName)
            }
        }
    }

    private companion object {
        /** 本模块自有的网络错误码（与解析器错误码同一命名空间，由 UI 映射文案）。 */
        const val CODE_NETWORK = "APP_NETWORK_ERROR"

        /** 本模块自有的协议错误码。 */
        const val CODE_PROTOCOL = "APP_PROTOCOL_ERROR"

        /** 解析器在提取码错误时返回的机器码（与 QuarkParser 对齐），UI 据此保留弹窗并提示重试。 */
        const val CODE_WRONG_PASSWORD = "QUARK_WRONG_PASSWORD"
    }
}