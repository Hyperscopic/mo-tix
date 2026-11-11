package com.hyperscopic.motix.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyperscopic.motix.MoTixViewModel
import com.hyperscopic.motix.R
import com.hyperscopic.motix.data.Film
import com.hyperscopic.motix.data.SearchCredit

/**
 * The official trailer in the YouTube embedded player (its IFrame Player API, the stock player, nothing changed), on
 * the big screen with sound. Parked only: the view model takes this screen off the stack the moment the car leaves
 * Park, which destroys the player, so the video stops and cannot resume by itself.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerScreen(vm: MoTixViewModel, film: Film) {
    val fmt = LocalFmt.current
    val id = film.trailer?.videoId?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    var view by remember { mutableStateOf<WebView?>(null) }
    // Out of sight (another app takes the window): the player pauses.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> view?.onPause()
                Lifecycle.Event.ON_START -> view?.onResume()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().background(MT.Bg)) { Header(vm, stringResource(R.string.back), onBack = { vm.back() }) }
        if (id == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NoteLine(stringResource(R.string.no_trailer)) }
            return@Column
        }
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    // An exact size: a wrap-content WebView lays the page out 0 px tall (the player plays, unseen).
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webChromeClient = WebChromeClient()
                    // The player stays the player: links out of it (the YouTube logo, end cards) go nowhere in the car.
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame
                    }
                    val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#player{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
                        </head><body><div id="player"></div><script>
                        function onYouTubeIframeAPIReady() {
                          new YT.Player('player', {
                            videoId: '$id', width: '100%', height: '100%',
                            playerVars: {autoplay: 1, controls: 1, rel: 0, playsinline: 1, iv_load_policy: 3, hl: '${if (fmt.korean) "ko" else "en"}'},
                            events: { onReady: function (e) { e.target.playVideo(); } }
                          });
                        }
                        </script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""
                    // The page sits on an https origin so the player gets a referrer.
                    loadDataWithBaseURL("https://mo-guide.com/", html, "text/html", "utf-8", null)
                    view = this
                }
            },
            onRelease = {
                view = null
                it.loadUrl("about:blank")
                it.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Google Search suggestions for the film list (Grounding with Google Search): the chip Google returns
 * (`searchEntryPoint.renderedContent`), rendered as provided. Tapping a suggestion opens Google's results page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchSuggestions(vm: MoTixViewModel, credit: SearchCredit, modifier: Modifier = Modifier) {
    val html = credit.entryPointHtml ?: return
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        vm.openSearch(request.url.toString())
                        return true
                    }
                }
                tag = ""
            }
        },
        update = { v ->
            if (v.tag != html) {
                v.tag = html
                v.loadDataWithBaseURL("https://www.google.com/", "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.5\"><meta name=\"color-scheme\" content=\"dark\"><style>body{margin:0;background:transparent}</style></head><body>$html</body></html>", "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier.height(92.dp),
    )
}

/** Google's results page for a tapped suggestion. It shows that page and no other site; parked only, like the trailer. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchPage(vm: MoTixViewModel, url: String) {
    Column(Modifier.fillMaxSize().background(MT.Bg)) {
        Header(vm, stringResource(R.string.back), onBack = { vm.back() })
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.saveFormData = false // a results page, not a browser: nothing typed here is kept
                    webViewClient = object : WebViewClient() {
                        // Google Search stays; every other site is left for the phone.
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = !isGoogleSearch(request.url)
                    }
                    if (isGoogleSearch(Uri.parse(url))) loadUrl(url)
                }
            },
            onRelease = { it.loadUrl("about:blank"); it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Google's results page (and the redirect a Search suggestion goes through); never sign-in or any other Google page. */
internal fun isGoogleSearch(uri: Uri): Boolean {
    if (uri.scheme != "https") return false
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty()
    return when (host) {
        "vertexaisearch.cloud.google.com" -> true
        "www.google.com", "google.com", "www.google.co.kr" -> path == "/search" || path.startsWith("/search/") || path == "/url"
        else -> false
    }
}

/** Where everything comes from. */
@Composable
fun CreditsPanel(vm: MoTixViewModel) {
    val compact = LocalCompact.current
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).tap(androidx.compose.ui.graphics.RectangleShape) { vm.creditsOpen = false }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(if (compact) 0.92f else 0.6f).glassCard(28.dp).tap(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {}.padding(horizontal = 44.dp, vertical = 36.dp),
        ) {
            Text(stringResource(R.string.sources), style = Type.TitleL, color = MT.Text)
            Box(Modifier.height(18.dp))
            for (line in listOf(R.string.credit_films, R.string.credit_theaters, R.string.credit_art)) {
                Text(stringResource(line), style = Type.BodyS, color = MT.TextSoft, modifier = Modifier.padding(vertical = 5.dp))
            }
            Box(Modifier.padding(vertical = 10.dp)) { TmdbLogo() }
            Text(stringResource(R.string.credit_tmdb_notice), style = Type.LabelM, color = MT.TextSoft, modifier = Modifier.padding(bottom = 10.dp))
            for (line in listOf(R.string.credit_trailers, R.string.credit_route, R.string.credit_font)) {
                Text(stringResource(line), style = Type.BodyS, color = MT.TextSoft, modifier = Modifier.padding(vertical = 5.dp))
            }
            Box(Modifier.height(26.dp))
            SecondaryButton(stringResource(R.string.close), Modifier.align(Alignment.End)) { vm.creditsOpen = false }
        }
    }
}

/** The TMDB logo, as TMDB provides it for attribution. */
@Composable
private fun TmdbLogo() {
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_tmdb), "TMDB", Modifier.height(30.dp))
}
