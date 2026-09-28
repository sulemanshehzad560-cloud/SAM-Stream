package com.samstream.app.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.samstream.app.data.PlayRequest
import com.samstream.app.domain.PlaybackKind
import com.samstream.app.ui.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: AppViewModel, nav: NavHostController) {
    val req by vm.nowPlaying.collectAsStateWithLifecycle()
    val r = req
    if (r == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    Immersive()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (r.kind) {
            PlaybackKind.DIRECT_VIDEO -> DirectVideo(r, vm)
            PlaybackKind.YOUTUBE_EMBED -> YouTubeEmbed(r, vm)
            PlaybackKind.EXTERNAL_APP -> Unit
        }
        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.background(Color(0x88000000), CircleShape)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
            }
        }
        // Attribution stays visible: where the stream comes from and why we may play it.
        Text(
            "${r.title} · ${r.providerName}",
            color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color(0x66000000)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DirectVideo(r: PlayRequest, vm: AppViewModel) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember(r.sourceId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(r.url))
            prepare()
            if (r.startMs > 0) seekTo(r.startMs)
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = "Playback failed (${e.errorCodeName}). The source may be temporarily unavailable." }
        }
        player.addListener(listener)
        onDispose {
            vm.saveProgress(r, player.currentPosition, player.duration.coerceAtLeast(0))
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(15_000)
            if (player.isPlaying) vm.saveProgress(r, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; keepScreenOn = true } },
        modifier = Modifier.fillMaxSize(),
    )
    error?.let {
        Text(it, color = Color.White, modifier = Modifier.fillMaxSize().padding(48.dp))
    }
}

/**
 * YouTube's own IFrame player inside a WebView, so the rights holder keeps their ads and analytics.
 * The page is loaded with base URL https://<package> so YouTube sees the app's identity (required for embeds in
 * apps since 2025; without it videos fail with error 152/153). Nothing ever navigates out of the app: links such as
 * "Watch on YouTube" are blocked, and player errors are explained on screen instead.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun YouTubeEmbed(r: PlayRequest, vm: AppViewModel) {
    var error by remember(r.sourceId) { mutableStateOf<String?>(null) }
    var loading by remember(r.sourceId) { mutableStateOf(true) }
    val main = remember { Handler(Looper.getMainLooper()) }
    var lastPos by remember(r.sourceId) { mutableStateOf(r.startMs to 0L) }

    DisposableEffect(r.sourceId) {
        onDispose { vm.saveProgress(r, lastPos.first, lastPos.second) }
    }

    val bridge = remember(r.sourceId) {
        YouTubeBridge(
            errorCb = { code -> main.post { loading = false; error = youtubeErrorText(code) } },
            playingCb = { main.post { loading = false; error = null } },
            timeCb = { sec, dur -> main.post { lastPos = (sec * 1000).toLong() to (dur * 1000).toLong() } },
        )
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.setSupportMultipleWindows(false)
                keepScreenOn = true
                setBackgroundColor(android.graphics.Color.BLACK)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    // Stay inside SAM Stream: never open YouTube, the browser or any other site.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                addJavascriptInterface(bridge, "SAM")
                val origin = "https://${ctx.packageName}"
                loadDataWithBaseURL(origin, youtubeHtml(r.url, (r.startMs / 1000).toInt(), origin), "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )

    if (loading && error == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFFFC83D)) }
    error?.let { msg ->
        Column(
            Modifier.fillMaxSize().background(Color(0xF0000000)).padding(48.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Can't play this video here", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(msg, color = Color(0xFFCFC8D9), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Called from the YouTube page's JavaScript (on a background thread). */
class YouTubeBridge(
    private val errorCb: (Int) -> Unit,
    private val playingCb: () -> Unit,
    private val timeCb: (Double, Double) -> Unit,
) {
    @JavascriptInterface fun onError(code: Int) = errorCb(code)
    @JavascriptInterface fun onPlaying() = playingCb()
    @JavascriptInterface fun onTime(seconds: Double, duration: Double) = timeCb(seconds, duration)
}

private fun youtubeErrorText(code: Int): String = when (code) {
    100 -> "This video was removed or made private. It will be hidden after the next refresh."
    101, 150 -> "The owner doesn't allow this video to play inside other apps. Go back and pick another title."
    152, 153 -> "YouTube refused the in-app player for this video. Go back and pick another title."
    2 -> "YouTube rejected the video link."
    5 -> "This video can't play in the in-app player on this phone."
    else -> "YouTube error $code."
}

private fun youtubeHtml(videoId: String, start: Int, origin: String): String {
    val id = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;top:0;left:0;width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script>
var player;
function onYouTubeIframeAPIReady(){
  player = new YT.Player('p', {
    width:'100%', height:'100%', videoId:'$id',
    playerVars:{autoplay:1, playsinline:1, rel:0, fs:0, iv_load_policy:3, start:$start, origin:'$origin', widget_referrer:'$origin'},
    events:{
      onReady:function(e){ e.target.playVideo(); },
      onError:function(e){ SAM.onError(e.data); },
      onStateChange:function(e){ if(e.data===1){ SAM.onPlaying(); } }
    }
  });
  setInterval(function(){ try{ if(player && player.getCurrentTime){ SAM.onTime(player.getCurrentTime(), player.getDuration()); } }catch(x){} }, 5000);
}
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body></html>"""
}

/** Landscape, full-screen while the player is open; restores the previous state afterwards. */
@Composable
private fun Immersive() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
