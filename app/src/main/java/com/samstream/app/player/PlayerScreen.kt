package com.samstream.app.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
 * YouTube's own embedded player (IFrame embed) in a WebView, so the rights holder keeps their ads and analytics.
 * YouTube requires embeds in apps to send a Referer identifying the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbed(r: PlayRequest, vm: AppViewModel) {
    DisposableEffect(r.sourceId) {
        vm.saveProgress(r, 0, 0)
        onDispose { }
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                keepScreenOn = true
                setBackgroundColor(android.graphics.Color.BLACK)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val host = request.url.host.orEmpty()
                        if (host.endsWith("youtube.com") && request.url.path.orEmpty().startsWith("/embed")) return false
                        // Links out of the player (channel, "Watch on YouTube") open in the YouTube app/browser.
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                }
                val start = (r.startMs / 1000).toInt()
                loadUrl(
                    "https://www.youtube.com/embed/${Uri.encode(r.url)}?autoplay=1&playsinline=1&rel=0&fs=0&start=$start",
                    mapOf("Referer" to "https://${ctx.packageName}"),
                )
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
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
