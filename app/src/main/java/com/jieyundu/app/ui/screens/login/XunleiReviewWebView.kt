// 文件：XunleiReviewWebView.kt
// 职责：迅雷登录风控（安全验证）内嵌页——按 §4⑤ 注入 XlCaptcha.init 与 JS 回调桥
// 依赖：AndroidView/WebView、XunleiConfig、GlassButton、Dimens、Timber
// 协议：AGPL-3.0

package com.jieyundu.app.ui.screens.login

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.jieyundu.app.R
import com.jieyundu.app.domain.parser.xunlei.XunleiConfig
import com.jieyundu.app.ui.glass.GlassButton
import com.jieyundu.app.ui.theme.Dimens
import com.jieyundu.app.ui.theme.JieYunDuColors
import timber.log.Timber

/**
 * 迅雷登录风控页（【JYD-XUNLEI-P1B2-2026-10-08】；依据《抓包事实.md》§4 ⑤）。
 *
 * 使用场景：账号密码 / 短信登录返回 `errorCode=1007` + `reviewurl` 时，
 * 账号被判定需要**安全验证**；此时把 `reviewurl` 放进内嵌 WebView，等页面验证完成后回到原生流程。
 *
 * 文档给出的三处实现要点（§4 ⑤）：
 * 1. 注入配置 `XlCaptcha.init({ appid, appName, clientVersion, deviceid, event:"login3",
 *    platformVersion:"10", IFRAME_BOX_ID:"captch-wrap", VERTIFYSUCCFUNC })`；
 * 2. JS 回调桥 `window.XLJSWebViewBridge.onVerifyResult(json)`；
 * 3. 回调协议自定义 scheme `xlaccsdk01://xunlei.com/callback`。
 *
 * 两个「已实测的坑」（§4 ⑤，本实现都规避）：
 * - `IFRAME_BOX_ID` **必须非空**，否则页面内部调用不存在的 `isMobileSDK()` 抛错 → 永久空白；
 * - 页面从 **URL 的 query** 读 `deviceid`（**不读** init 配置），故必须把 `deviceid` 拼进 URL。
 *
 * ⚠️ 未决（已登记 CHANGELOG）：文档未指明注入配置里的 `clientVersion` 该取 App 版本
 * （8.31.0.9726）还是登录接口版本（25.0.5.25）；本实现取 **App 版本**，待真机验证。
 *
 * @param reviewUrl 服务端下发的安全验证页地址。
 * @param deviceId 本机 deviceId（会拼进 URL query，且用于注入配置）。
 * @param onVerified 验证完成回调（原生侧据此重试登录）。
 * @param onClose 关闭回调。
 * @param modifier 外部修饰符。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XunleiReviewWebView(
    reviewUrl: String,
    deviceId: String,
    onVerified: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Text(
                text = stringResource(R.string.xunlei_login_review_title),
                style = MaterialTheme.typography.titleMedium,
                color = JieYunDuColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
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
        Text(
            text = stringResource(R.string.xunlei_login_review_hint),
            style = MaterialTheme.typography.labelSmall,
            color = JieYunDuColors.TextTertiary
        )
        AndroidView(
            factory = { context ->
                createReviewWebView(
                    context = context,
                    urlWithDeviceId = withDeviceIdQuery(reviewUrl, deviceId),
                    deviceId = deviceId,
                    onVerified = onVerified
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onRelease = { webView -> webView.destroy() }
        )
    }
}

/**
 * 把 `deviceid` 拼进验证页 URL 的 query（§4 ⑤：页面从 URL 读它，不读注入配置）。
 *
 * @param url 服务端下发的地址。
 * @param deviceId 本机 deviceId。
 * @return 带 `deviceid` 的地址；已存在同名参数时不重复追加。
 */
internal fun withDeviceIdQuery(url: String, deviceId: String): String {
    if (deviceId.isBlank() || url.contains("$QUERY_DEVICE_ID=")) {
        return url
    }
    val separator = if (url.contains('?')) '&' else '?'
    return "$url$separator$QUERY_DEVICE_ID=$deviceId"
}

/**
 * 创建风控 WebView：开启 JS / DOM 存储，注入 `XlCaptcha.init` 与回调桥，并监听自定义 scheme。
 *
 * @param context 上下文。
 * @param urlWithDeviceId 已拼 `deviceid` 的验证页地址。
 * @param deviceId 本机 deviceId。
 * @param onVerified 验证完成回调。
 * @return 配置完成的 WebView。
 */
private fun createReviewWebView(
    context: Context,
    urlWithDeviceId: String,
    deviceId: String,
    onVerified: () -> Unit
): WebView {
    val webView = WebView(context)
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        // 风控页属 App 登录链路，使用 App 端 UA（与业务接口一致）。
        userAgentString = XunleiConfig.UA_APP
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }
    // JS 回调桥（§4 ⑤）：页面验证完成后调用该桥，原生据此重试登录。
    webView.addJavascriptInterface(
        ReviewBridge(onVerified = onVerified),
        BRIDGE_NAME
    )
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.evaluateJavascript(buildInitScript(deviceId), null)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            url: String?
        ): Boolean {
            val target = url ?: return false
            if (target.startsWith(CALLBACK_SCHEME)) {
                // 回调协议：xlaccsdk01://xunlei.com/callback（§4 ⑤）。
                Timber.i("XunleiReview callback received")
                onVerified()
                return true
            }
            return false
        }
    }
    webView.loadUrl(urlWithDeviceId)
    return webView
}

/**
 * 构造注入脚本：`XlCaptcha.init({...})`（§4 ⑤ 的配置，`IFRAME_BOX_ID` 必须非空）。
 *
 * @param deviceId 本机 deviceId。
 * @return 可直接 evaluate 的 JS。
 */
internal fun buildInitScript(deviceId: String): String =
    "javascript:(function(){" +
        "if(typeof XlCaptcha==='undefined'){return;}" +
        "XlCaptcha.init({" +
        "appid:'${XunleiConfig.APP_ID}'," +
        "appName:'${XunleiConfig.APP_NAME_FOR_CAPTCHA}'," +
        "clientVersion:'${XunleiConfig.APP_VERSION}'," +
        "deviceid:'$deviceId'," +
        "event:'$CAPTCHA_EVENT_LOGIN3'," +
        "platformVersion:'${XunleiConfig.CAPTCHA_PLATFORM_VERSION}'," +
        "IFRAME_BOX_ID:'$CAPTCHA_IFRAME_BOX_ID'," +
        "VERTIFYSUCCFUNC:function(result){" +
        "if(window.$BRIDGE_NAME&&window.$BRIDGE_NAME.onVerifyResult){" +
        "window.$BRIDGE_NAME.onVerifyResult(typeof result==='string'?result:JSON.stringify(result));" +
        "}}})})()"

/**
 * JS → 原生回调桥（§4 ⑤：`window.XLJSWebViewBridge.onVerifyResult(json)`）。
 *
 * @param onVerified 验证完成回调。
 */
private class ReviewBridge(private val onVerified: () -> Unit) {

    /**
     * 页面回调入口（名字必须与文档一致，由页面 JS 调用）。
     *
     * @param json 验证结果 JSON（内容不解析，只作为「验证完成」信号）。
     */
    @JavascriptInterface
    fun onVerifyResult(json: String) {
        Timber.i("XunleiReview onVerifyResult len=%d", json.length)
        onVerified()
    }
}

/** JS 桥名（§4 ⑤）。 */
private const val BRIDGE_NAME = "XLJSWebViewBridge"

/** 验证完成回调 scheme（§4 ⑤）。 */
private const val CALLBACK_SCHEME = "xlaccsdk01://xunlei.com/callback"

/** 注入配置：事件名（§4 ⑤）。 */
private const val CAPTCHA_EVENT_LOGIN3 = "login3"

/** 注入配置：iframe 容器 id（§4 ⑤，必须非空）。 */
private const val CAPTCHA_IFRAME_BOX_ID = "captch-wrap"

/** URL query 参数名（§4 ⑤）。 */
private const val QUERY_DEVICE_ID = "deviceid"
