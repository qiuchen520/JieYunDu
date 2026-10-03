// 文件：HomeViewModel.kt
// 职责：首页解析 / 分享目录浏览（展开、返回上一级）/ 把选中的文件转存取链并投递下载
// 依赖：ParserRegistry、ShareBrowser、ShareDownloadPreparer、LinkExtractor、DownloadEngine、
//       DownloadSessionRegistry、CookieStore、UserAgentProvider、CookieExtractor、Hilt、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.home

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jieyundu.app.R
import com.jieyundu.app.data.remote.CookieStore
import com.jieyundu.app.data.remote.UserAgentProvider
import com.jieyundu.app.data.settings.AppSettingsStore
import com.jieyundu.app.data.settings.DownloadDirectoryMode
import com.jieyundu.app.data.storage.PublicDownloadsPublisher
import com.jieyundu.app.domain.downloader.DownloadEngine
import com.jieyundu.app.domain.downloader.DownloadState
import com.jieyundu.app.domain.downloader.DownloadTask
import com.jieyundu.app.domain.login.CookieExtractor
import com.jieyundu.app.domain.model.FileInfo
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.domain.model.ParseResult
import com.jieyundu.app.domain.model.ShareLink
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.NetdiskServiceRouter
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import timber.log.Timber

/**
 * 首页 ViewModel。
 *
 * 职责（《要求.md》4.1 +【修订 JYD-BROWSE-2026-10-03】）：
 * 1. 承接输入框文本，调用 [LinkExtractor] 提取分享链接与提取码；
 * 2. 经 [ParserRegistry] 路由到对应解析器执行解析（得到分享根目录列表）；
 * 3. **浏览**：点文件夹经 [ShareBrowser] 逐级展开，维护路径栈以支持「返回上一级」；
 * 4. **下载**：把选中文件经 [ShareDownloadPreparer] 转存到临时目录 → 轮询取新 fid →
 *   换直链 → 交 [DownloadEngine] 分片下载；下载完成后清理临时文件。
 *
 * 线程约束（C9）：耗时工作显式调度到 [Dispatchers.IO]；[CancellationException] 一律原样抛出（C3）。
 *
 * @param parserRegistry 解析器注册表。
 * @param netdiskRouter 网盘能力路由器（B2：按 type 取分享浏览器 / 转存器）。
 * @param downloadEngine 分片下载引擎。
 * @param downloadSessionRegistry 会话内「任务 ID → 文件名」登记表（下载列表展示用）。
 * @param cookieStore 登录态 Cookie 仓库；下载时按网盘域取 Cookie 注入请求头。
 * @param userAgentProvider 四家网盘 Referer 常量提供者。
 * @param appSettingsStore 应用设置（下载并发数、下载目录模式）。
 * @param publicDownloadsPublisher 成品发布器（默认 A3：把私有目录成品发布到公共下载目录）。
 * @param appContext 应用上下文，仅用于推导默认落盘目录。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val parserRegistry: ParserRegistry,
    private val netdiskRouter: NetdiskServiceRouter,
    private val downloadEngine: DownloadEngine,
    private val downloadSessionRegistry: DownloadSessionRegistry,
    private val cookieStore: CookieStore,
    private val userAgentProvider: UserAgentProvider,
    private val appSettingsStore: AppSettingsStore,
    private val publicDownloadsPublisher: PublicDownloadsPublisher,
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
            errorRes = null,
            shareContext = null,
            stack = emptyList(),
            isLoadingDir = false,
            dirErrorRes = null,
            isPreparingDownload = false,
            downloadErrorRes = null
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
            } catch (exception: Exception) {
                // 兜底：Retrofit 的 HttpException 等运行时异常此前会逃逸导致崩溃，统一转成解析错误。
                Timber.e(exception, "HomeViewModel parse failed: unexpected error")
                ParseResult.Error(link.type, CODE_UNKNOWN, CODE_UNKNOWN)
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
                // 解析成功 → 建立分享上下文与路径栈（根目录）
                result is ParseResult.Success -> _uiState.value.copy(
                    isParsing = false,
                    result = result,
                    passwordPrompt = false,
                    passwordErrorRes = null,
                    shareContext = ShareContext(
                        netdiskType = result.netdiskType,
                        title = result.shareTitle,
                        pwdId = result.pwdId,
                        stoken = result.stoken
                    ),
                    stack = listOf(
                        BrowseLevel(
                            pdirFid = ROOT_PDIR_FID,
                            name = result.shareTitle,
                            files = result.files
                        )
                    ),
                    isLoadingDir = false,
                    dirErrorRes = null
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
     * 进入某个文件夹（展开子目录并压入路径栈）。
     *
     * @param folder 被点击的文件夹条目。
     */
    fun openFolder(folder: FileInfo) {
        if (!folder.isDirectory) return
        val context = _uiState.value.shareContext ?: return
        // 按网盘类型取对应的分享浏览器（B2：多网盘路由，替代单例硬绑）。
        val browser = netdiskRouter.shareBrowserFor(context.netdiskType)
        if (browser == null) {
            _uiState.value = _uiState.value.copy(dirErrorRes = R.string.parse_dir_load_failed)
            return
        }
        _uiState.value = _uiState.value.copy(isLoadingDir = true, dirErrorRes = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val children = browser.listChildren(
                    pwdId = context.pwdId,
                    stoken = context.stoken,
                    pdirFid = folder.fid
                )
                _uiState.value = _uiState.value.copy(
                    isLoadingDir = false,
                    stack = _uiState.value.stack + BrowseLevel(
                        pdirFid = folder.fid,
                        name = folder.fileName,
                        files = children
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (io: IOException) {
                Timber.e(io, "HomeViewModel openFolder failed: network error")
                _uiState.value = _uiState.value.copy(
                    isLoadingDir = false,
                    dirErrorRes = R.string.parse_dir_load_failed
                )
            } catch (serialization: SerializationException) {
                Timber.e(serialization, "HomeViewModel openFolder failed: unexpected body")
                _uiState.value = _uiState.value.copy(
                    isLoadingDir = false,
                    dirErrorRes = R.string.parse_dir_load_failed
                )
            } catch (exception: Exception) {
                // 兜底：HttpException 等运行时异常不得逃逸（否则崩溃）。
                Timber.e(exception, "HomeViewModel openFolder failed: unexpected error")
                _uiState.value = _uiState.value.copy(
                    isLoadingDir = false,
                    dirErrorRes = R.string.parse_dir_load_failed
                )
            }
        }
    }

    /** 返回上一级目录（弹栈；已在根目录时不动作）。 */
    fun navigateUp() {
        val stack = _uiState.value.stack
        if (stack.size <= 1) return
        _uiState.value = _uiState.value.copy(
            stack = stack.dropLast(1),
            dirErrorRes = null
        )
    }

    /**
     * 下载选中的文件：先转存到临时目录并换取直链，再投递到下载引擎；完成后清理临时文件。
     *
     * 说明：文件夹不会被下载（由 UI 侧路由为进入目录）；无分享上下文且无直链时跳过。
     * 逐个文件串行准备（转存 / 取链），任一文件失败只记错误、不影响其余文件；
     * 全部结束后把「是否有失败」汇总到 [HomeUiState.downloadErrorRes]。
     *
     * 崩溃修复（本轮）：此前只捕获 [IOException]，而 Retrofit 在非 2xx 时抛
     * `HttpException`、响应体异常时抛 [SerializationException]，均属未捕获的运行时异常，
     * 会直接崩溃 App。现统一以 [Exception] 兜底（[CancellationException] 仍原样抛出，C3）。
     *
     * 提示区分（B1 装机反馈）：捕获 [HttpException] 并单独识别 401——此时问题是**登录态**
     * （缺 Cookie / 已过期），提示用户重新登录比笼统的「下载启动失败」可操作。
     *
     * @param files 用户勾选待下载的文件条目列表（内部会过滤掉文件夹）。
     */
    fun download(files: List<FileInfo>) {
        val targets = files.filterNot { file -> file.isDirectory }
        if (targets.isEmpty()) {
            return
        }
        _uiState.value = _uiState.value.copy(
            isPreparingDownload = true,
            downloadErrorRes = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            var hasFailure = false
            var authFailure = false
            try {
                val context = _uiState.value.shareContext
                targets.forEach { file ->
                    try {
                        startDownload(file, context)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (http: HttpException) {
                        // 401：登录态缺失 / 失效（服务端拒绝写入类操作）。单独标记，UI 给出可操作提示。
                        hasFailure = true
                        if (http.code() == HTTP_UNAUTHORIZED) {
                            authFailure = true
                        }
                        Timber.e(http, "HomeViewModel failed to start download for %s", file.fileName)
                    } catch (exception: Exception) {
                        hasFailure = true
                        Timber.e(exception, "HomeViewModel failed to start download for %s", file.fileName)
                    }
                }
            } finally {
                _uiState.value = _uiState.value.copy(
                    isPreparingDownload = false,
                    downloadErrorRes = when {
                        authFailure -> R.string.parse_download_auth_failed
                        hasFailure -> R.string.parse_download_failed
                        else -> null
                    }
                )
            }
        }
    }

    /**
     * 下载主体：转存取链 → 投递引擎 → 完成后清理。
     *
     * @param file 目标文件。
     * @param context 分享上下文；为 null 时回退到文件自带直链（如个人网盘文件）。
     */
    private suspend fun startDownload(file: FileInfo, context: ShareContext?) {
        // 按网盘类型取转存器（B2：多网盘路由）。
        val preparer = context?.let { ctx -> netdiskRouter.shareDownloadPreparerFor(ctx.netdiskType) }
        val prepared = if (context != null && preparer != null) {
            preparer.prepare(context.pwdId, context.stoken, file)
        } else {
            null
        }
        val url = prepared?.url ?: file.downloadUrl
        if (url.isNullOrBlank()) {
            Timber.w("HomeViewModel download skipped: direct link not ready for %s", file.fileName)
            return
        }
        val directory = resolveDownloadDirectory()
        val taskId = UUID.randomUUID().toString()
        val targetFile = File(directory, file.fileName)
        val task = DownloadTask(
            taskId = taskId,
            url = url,
            fileName = file.fileName,
            fileSize = file.fileSize,
            savePath = targetFile.absolutePath,
            chunkCount = appSettingsStore.chunkCount.value,
            headers = buildDownloadHeaders(context?.netdiskType)
        )
        downloadSessionRegistry.remember(taskId, file.fileName, task.savePath)
        downloadEngine.start(task)
        val completed = awaitDownloadCompleted(taskId)
        if (completed) {
            publishIfNeeded(taskId, file.fileName, targetFile)
        }
        if (completed && prepared != null && preparer != null) {
            preparer.cleanupAfterDownload(prepared.newFid)
        }
    }

    /**
     * 计算本次下载的工作目录（B2 功能①）。
     *
     * 规则：
     * - CUSTOM 模式且已设置有效路径 → 直接写入用户目录（真实路径，需「所有文件访问」A1 权限）；
     * - 否则（默认 PUBLIC_DOWNLOADS）→ 先写应用私有下载目录，下载完成后再由 [publishIfNeeded]
     *   发布到公共 `Download/极云渡/`（A3，无需任何存储权限）。
     *
     * @return 工作目录；自定义目录不可用时回退到应用私有目录。
     */
    private fun resolveDownloadDirectory(): File {
        if (appSettingsStore.downloadDirectoryMode == DownloadDirectoryMode.CUSTOM) {
            val custom = appSettingsStore.customDirectoryPath
            if (!custom.isNullOrBlank()) {
                val dir = File(custom)
                if (dir.isDirectory || dir.mkdirs()) {
                    return dir
                }
                Timber.w("HomeViewModel: custom dir unavailable, fall back to private: %s", custom)
            }
        }
        return appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
    }

    /**
     * 默认（A3）模式下，把私有目录成品发布到公共下载目录，并删除私有副本。
     *
     * 说明：发布成功后更新会话登记为可访问位置（`content://` 或公共路径），供下载列表的
     * 分享 / 安装按钮与删除操作使用；发布失败则保留私有副本（至少文件仍在，避免丢文件）。
     *
     * @param taskId 任务 ID。
     * @param fileName 文件名。
     * @param workingFile 私有工作文件（发布后删除）。
     */
    private suspend fun publishIfNeeded(taskId: String, fileName: String, workingFile: File) {
        if (appSettingsStore.downloadDirectoryMode != DownloadDirectoryMode.PUBLIC_DOWNLOADS) {
            return
        }
        val published = publicDownloadsPublisher.publish(
            workingFile,
            appSettingsStore.publicFolderName()
        )
        if (published == null) {
            Timber.w("HomeViewModel: publish to public downloads failed for %s", fileName)
            return
        }
        downloadSessionRegistry.remember(taskId, fileName, published)
        if (workingFile.isFile && !workingFile.delete()) {
            Timber.e("HomeViewModel: failed to delete private copy: %s", workingFile.name)
        }
    }

    /**
     * 等待下载任务到达终态。
     *
     * @param taskId 任务 ID。
     * @return true 表示下载成功完成（据此决定是否清理临时文件；失败保留以便续传）。
     */
    private suspend fun awaitDownloadCompleted(taskId: String): Boolean {
        val finalState = downloadEngine.observe(taskId).firstOrNull { state ->
            state == DownloadState.COMPLETED ||
                state == DownloadState.FAILED ||
                state == DownloadState.CANCELED
        }
        return finalState == DownloadState.COMPLETED
    }

    /**
     * 组装下载请求所需的附加请求头（Referer + Cookie）。
     *
     * 背景（《解析Bug分析.md》P1-4）：夸克等网盘直链对来源与登录态敏感，缺少
     * `Referer` / Cookie 会被风控拒绝（412 / 403）。此处按网盘类型取对应 Referer，
     * 并从 [CookieStore] 取该域登录态 Cookie 注入；未识别类型时不附加任何头，
     * 未登录时不附加 Cookie（交由服务端判定，避免塞入空值）。
     *
     * @param type 本次解析所属网盘类型；未知时为 null。
     * @return 不可变的附加请求头映射；无可用头时返回空映射。
     */
    private fun buildDownloadHeaders(type: NetdiskType?): Map<String, String> {
        if (type == null) return emptyMap()
        val headers = linkedMapOf(HEADER_REFERER to refererOf(type))
        val cookie = cookieStore.findForHost(CookieExtractor.cookieDomainOf(type))
        if (!cookie.isNullOrBlank()) {
            headers[HEADER_COOKIE] = cookie
        }
        return headers
    }

    /**
     * 网盘类型 → 请求来源（Referer）映射。
     *
     * @param type 网盘类型。
     * @return 对应网盘的 Referer 常量。
     */
    private fun refererOf(type: NetdiskType): String = when (type) {
        NetdiskType.QUARK -> userAgentProvider.quarkReferer
        NetdiskType.BAIDU -> userAgentProvider.baiduReferer
        NetdiskType.UC -> userAgentProvider.ucReferer
        NetdiskType.XUNLEI -> userAgentProvider.xunleiReferer
    }

    private companion object {
        /** 分享根目录的 pdir_fid 取值。 */
        const val ROOT_PDIR_FID = "0"

        /** 本模块自有的网络错误码（与解析器错误码同一命名空间，由 UI 映射文案）。 */
        const val CODE_NETWORK = "APP_NETWORK_ERROR"

        /** 本模块自有的协议错误码。 */
        const val CODE_PROTOCOL = "APP_PROTOCOL_ERROR"

        /** 兜底错误码：未归类的运行时异常（UI 映射为「未知错误」）。 */
        const val CODE_UNKNOWN = "APP_UNKNOWN_ERROR"

        /** 解析器在提取码错误时返回的机器码（与 QuarkParser 对齐），UI 据此保留弹窗并提示重试。 */
        const val CODE_WRONG_PASSWORD = "QUARK_WRONG_PASSWORD"

        /** HTTP 来源请求头名。 */
        const val HEADER_REFERER = "Referer"

        /** HTTP Cookie 请求头名。 */
        const val HEADER_COOKIE = "Cookie"

        /** HTTP 401：未授权（登录态缺失 / 失效）。 */
        const val HTTP_UNAUTHORIZED = 401
    }
}