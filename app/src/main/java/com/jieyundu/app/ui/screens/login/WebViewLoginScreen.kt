// 文件：WebViewLoginScreen.kt
// 职责：内嵌网页登录页——打开网盘官网，两级校验登录，含手动兜底（保存 / 粘贴 Cookie）
// 依赖：WebView、CookieManager、CookieExtractor、NetdiskLoginViewModel、Glass 组件、strings、Dimens
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.jieyundu.app.R
import com.jieyundu.app.domain.login.CookieExtractor
import com.jieyundu.app.domain.model.NetdiskType
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.glass.GlassCard
import com.jieyundu.app.ui.glass.GlassPanel
import com.jieyundu.app.ui.screens.home.uiLabelRes
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 登录检测轮询间隔（毫秒，《实践》§5：1500ms）。 */
private const val LOGIN_POLL_INTERVAL_MILLIS = 1_500L

/** WebView 请求头中的 Cookie 名。 */
private const val HEADER_COOKIE = "Cookie"

/**
 * 内嵌网页登录页（阶段 11，依据《WebView登录与Cookie提取实践》）。
 *
 * 流程：WebView（桌面网页 UA + 缩放视口）打开网盘官网 → 用户扫码 / 账密登录 →
 * 每 [LOGIN_POLL_INTERVAL_MILLIS] 轮询读取 Cookie（方案 A：`CookieManager.getCookie`；
 * 兜底：拦截 `shouldInterceptRequest` 的 Cookie 请求头）→ 廉价预检命中后交 ViewModel
 * 做**网络校验** → 校验通过才落库并关闭页面。
 *
 * 手动兜底（《实践》§6）：①「保存登录态」按钮；②「粘贴 Cookie」弹窗。
 *
 * @param viewModel 登录状态 ViewModel。
 * @param type 要登录的网盘。
 * @param modifier 外部修饰符。
 */
@Composable
fun WebViewLoginScreen(
    viewModel: NetdiskLoginViewModel,
    type: NetdiskType,
    modifier: Modifier = Modifier
) {
    val interceptedCookie = remember { AtomicReference<String?>(null) }
    val validating by viewModel.validating.collectAsState()
    val pasteDialogVisible by viewModel.pasteDialogVisible.collectAsState()
    // 弹窗打开期间暂停轮询（每次循环重新读取最新值，《实践》§5）。
    val pausePolling by rememberUpdatedState(pasteDialogVisible)

    val readCookie: () -> String = {
        val primary = CookieExtractor.extract(
            CookieManager.getInstance(),
            CookieExtractor.cookieUrlsOf(type)
        )
        CookieExtractor.merge(primary, interceptedCookie.get())
    }

    LaunchedEffect(type) {
        while (isActive) {
            delay(LOGIN_POLL_INTERVAL_MILLIS)
            if (pausePolling) continue
            val merged = readCookie()
            if (CookieExtractor.isLoggedIn(type, merged)) {
                CookieManager.getInstance().flush()
                viewModel.submitAuto(type, merged)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JieYunDuColors.Background)
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        LoginHeader(type = type, onClose = viewModel::closeLogin)
        Text(
            text = stringResource(R.string.webview_login_tutorial),
            style = MaterialTheme.typography.labelSmall,
            color = JieYunDuColors.TextTertiary
        )
        LoginActions(
            validating = validating,
            onSave = { viewModel.submitManual(type, readCookie()) },
            onPaste = viewModel::openPasteDialog
        )
        AndroidView(
            factory = { context ->
                createLoginWebView(
                    context,
                    type,
                    viewModel.webUserAgentFor(type),
                    interceptedCookie
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onRelease = { webView -> webView.destroy() }
        )
    }

    if (pasteDialogVisible) {
        CookiePasteDialog(
            onConfirm = { raw -> viewModel.submitPasted(type, raw) },
            onDismiss = viewModel::dismissPasteDialog
        )
    }
}

/**
 * 登录页顶部标题栏：标题 + 关闭按钮。
 *
 * @param type 网盘类型。
 * @param onClose 关闭回调。
 */
@Composable
private fun LoginHeader(type: NetdiskType, onClose: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Dimens.CardCorner,
        contentPadding = Dimens.SpaceLg
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.webview_login_title,
                        stringResource(type.uiLabelRes())
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = JieYunDuColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.webview_login_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = JieYunDuColors.TextTertiary
                )
            }
            GlassButton(
                text = stringResource(R.string.webview_login_close),
                onClick = onClose,
                height = Dimens.ButtonHeightCompact,
                cornerRadius = Dimens.ButtonCornerCompact,
                fillColor = JieYunDuColors.GlassFillStrong,
                borderColor = JieYunDuColors.GlassBorder,
                contentColor = JieYunDuColors.TextPrimary
            )
        }
    }
}

/**
 * 手动兜底操作行：保存登录态 / 粘贴 Cookie；校验在途时显示提示并禁用「保存」。
 *
 * @param validating 网络校验是否在途。
 * @param onSave 保存登录态回调。
 * @param onPaste 打开粘贴弹窗回调。
 */
@Composable
private fun LoginActions(
    validating: Boolean,
    onSave: () -> Unit,
    onPaste: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassButton(
            text = stringResource(R.string.webview_login_save),
            onClick = { if (!validating) onSave() },
            height = Dimens.ButtonHeightCompact,
            cornerRadius = Dimens.ButtonCornerCompact,
            fillColor = if (validating) JieYunDuColors.GlassDivider else JieYunDuColors.ButtonFill,
            borderColor = JieYunDuColors.ButtonBorder,
            contentColor = JieYunDuColors.OnPrimary
        )
        GlassButton(
            text = stringResource(R.string.webview_login_paste),
            onClick = onPaste,
            height = Dimens.ButtonHeightCompact,
            cornerRadius = Dimens.ButtonCornerCompact,
            fillColor = JieYunDuColors.GlassFillStrong,
            borderColor = JieYunDuColors.GlassBorder,
            contentColor = JieYunDuColors.TextPrimary
        )
        if (validating) {
            Text(
                text = stringResource(R.string.webview_login_validating),
                style = MaterialTheme.typography.labelSmall,
                color = JieYunDuColors.TextTertiary
            )
        }
    }
}

/**
 * 手动粘贴 Cookie 弹窗（液态玻璃风，D3：不用 Material AlertDialog）。
 *
 * @param onConfirm 确认回调，参数为用户粘贴的 Cookie 串。
 * @param onDismiss 关闭回调。
 */
@Composable
private fun CookiePasteDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Dimens.CardCorner,
            contentPadding = Dimens.PanelPadding
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                Text(
                    text = stringResource(R.string.cookie_paste_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = JieYunDuColors.TextPrimary
                )
                val fieldShape = RoundedCornerShape(Dimens.InputCornerCompact)
                BasicTextField(
                    value = text,
                    onValueChange = { value -> text = value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.InputHeightCompact)
                        .clip(fieldShape)
                        .background(JieYunDuColors.InputFieldFill)
                        .padding(Dimens.SpaceMd),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = JieYunDuColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(JieYunDuColors.Primary)
                )
                Text(
                    text = stringResource(R.string.cookie_paste_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = JieYunDuColors.TextTertiary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        height = Dimens.ButtonHeightCompact,
                        cornerRadius = Dimens.ButtonCornerCompact,
                        fillColor = JieYunDuColors.GlassFillStrong,
                        borderColor = JieYunDuColors.GlassBorder,
                        contentColor = JieYunDuColors.TextPrimary
                    )
                    GlassButton(
                        text = stringResource(R.string.action_confirm),
                        onClick = { onConfirm(text) },
                        height = Dimens.ButtonHeightCompact,
                        cornerRadius = Dimens.ButtonCornerCompact,
                        fillColor = JieYunDuColors.ButtonFill,
                        borderColor = JieYunDuColors.ButtonBorder,
                        contentColor = JieYunDuColors.OnPrimary
                    )
                }
            }
        }
    }
}

/**
 * 创建承载登录页的 WebView。
 *
 * 说明（《实践》§2）：开启 JS / DOM 存储；显式设置**桌面网页 UA**；开启缩放与宽视口，
 * 使桌面版登录页在手机上可操作；注册 [WebViewClient] 做登录态兜底采集（读取请求头
 * Cookie，含 HttpOnly），随后加载登录首页。
 *
 * @param context 上下文。
 * @param type 网盘类型。
 * @param loginUserAgent 登录页应使用的网页 UA。
 * @param interceptedCookie 兜底采集到的 Cookie 请求头（线程安全引用容器）。
 * @return 配置好的 WebView。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createLoginWebView(
    context: Context,
    type: NetdiskType,
    loginUserAgent: String,
    interceptedCookie: AtomicReference<String?>
): WebView {
    val webView = WebView(context)
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        if (loginUserAgent.isNotBlank()) {
            userAgentString = loginUserAgent
        }
    }

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val domain = CookieExtractor.cookieDomainOf(type)
    webView.webViewClient = object : WebViewClient() {
        /**
         * 兜底采集：读取 WebView 实际发出的 Cookie 请求头（含 HttpOnly）。
         *
         * 说明：该回调在**工作线程**调用，故用线程安全的 [AtomicReference] 承载结果；
         * 始终返回 null，绝不阻断请求（《实践》§3.2）。
         *
         * @param view 发起请求的 WebView。
         * @param request 请求信息。
         * @return 始终返回 null，交由 WebView 正常处理请求。
         */
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            val target = request ?: return null
            val host = target.url.host ?: return null
            if (host == domain || host.endsWith(".$domain")) {
                target.requestHeaders[HEADER_COOKIE]?.let { header ->
                    interceptedCookie.set(header)
                }
            }
            return null
        }
    }
    webView.loadUrl(CookieExtractor.loginUrlOf(type))
    return webView
}