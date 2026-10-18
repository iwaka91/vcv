package id.tg.vcv.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Peramban internal (WebView) untuk uji cepat tanpa pindah ke Chrome. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    title: String,
    url: String,
    onClose: () -> Unit,
    onOpenChrome: (String) -> Unit,
) {
    var progress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(url) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    Column(Modifier.fillMaxSize().background(Vc.Bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).background(Vc.Surface).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { VIcon(Ic.CLOSE, Vc.Text, 22.dp) }
            Column(Modifier.weight(1f)) {
                Text(title, style = vcText(16.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    currentUrl.removePrefix("http://").replace("127.0.0.1", "localhost"),
                    style = TextStyle(fontFamily = VcFonts.mono, fontSize = 12.sp, color = Vc.Muted),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { webView?.reload() }, modifier = Modifier.size(48.dp)) { VIcon(Ic.RELOAD, Vc.Text, 22.dp) }
            IconButton(onClick = { onOpenChrome(currentUrl) }, modifier = Modifier.size(48.dp)) { VIcon(Ic.EXTERNAL, Vc.Accent, 22.dp) }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Vc.Surface2)) {
            if (progress in 1..99) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(3.dp).background(Vc.Accent))
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                            if (u != null) currentUrl = u
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            }
        )
    }
}
