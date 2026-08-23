package com.jonas.mymedia.tv

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jonas.mymedia.tv.data.ApiClient
import com.jonas.mymedia.tv.data.DownloadStore
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(markerClass = [UnstableApi::class])
class VideoActivity : ComponentActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private var player: ExoPlayer? = null
    private var playbackPositionMs = 0L
    private var playWhenReady = true
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setControllerAutoShow(true)
            setControllerShowTimeoutMs(CONTROLS_TIMEOUT_MS)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setShowPreviousButton(false)
            setShowNextButton(false)
            // Media3's subtitle button opens its embedded-text track picker. The settings
            // button supplied by the same controller exposes the embedded audio track picker.
            setShowSubtitleButton(true)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        titleView = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE)
            setTextColor(Color.WHITE); textSize = 18f; setPadding(28, 20, 28, 20); setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        root.addView(titleView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) { finish(); return }
        playbackPositionMs = intent.getIntExtra(EXTRA_PROGRESS, 0).toLong() * 60_000L
        playerView.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
        playerView.onResume()
    }

    override fun onStop() {
        if (!completed) saveProgress(false)
        playerView.onPause()
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun initializePlayer() {
        if (player != null) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val mediaItem = MediaItem.Builder()
            .setUri(intent.getStringExtra(EXTRA_URL))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> hideTitle()
                        Player.STATE_ENDED -> {
                            if (!completed) {
                                completed = true
                                saveProgress(true)
                            }
                        }
                        Player.STATE_IDLE, Player.STATE_BUFFERING -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    titleView.animate().cancel()
                    titleView.alpha = 1f
                    titleView.setText(R.string.video_unavailable)
                    playerView.showController()
                }
            })
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.seekTo(playbackPositionMs)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = playWhenReady
            playerView.showController()
        }
    }

    private fun hideTitle() {
        if (titleView.alpha == 0f || titleView.text == getString(R.string.video_unavailable)) return
        titleView.animate().alpha(0f).setStartDelay(1_800).setDuration(500).start()
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            playWhenReady = exoPlayer.playWhenReady
            playerView.player = null
            exoPlayer.release()
        }
        player = null
    }

    private fun saveProgress(watched: Boolean) {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val declaredDuration = intent.getIntExtra(EXTRA_DURATION, 0).takeIf { it > 0 }
        val playerDuration = player?.duration
            ?.takeIf { it != C.TIME_UNSET && it > 0 }
            ?.let { ((it + 59_999L) / 60_000L).toInt() }
        val maximum = declaredDuration ?: playerDuration ?: Int.MAX_VALUE
        val currentMinutes = ((player?.currentPosition ?: playbackPositionMs) / 60_000L)
            .coerceIn(0L, maximum.toLong())
            .toInt()
        val minutes = if (watched) declaredDuration ?: playerDuration ?: currentMinutes else currentMinutes
        DownloadStore.get(this).updatePlayback(id, minutes, watched)
        val base = intent.getStringExtra(EXTRA_BASE_URL) ?: return
        lifecycleScope.launch {
            runCatching { ApiClient(base).updateMedia(kind, id, JSONObject().put("progressMinutes", minutes).put("isWatched", watched)) }
        }
    }

    companion object {
        const val EXTRA_URL = "video_url"
        const val EXTRA_TITLE = "video_title"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_KIND = "media_kind"
        const val EXTRA_ID = "media_id"
        const val EXTRA_PROGRESS = "progress_minutes"
        const val EXTRA_DURATION = "duration_minutes"

        private const val CONTROLS_TIMEOUT_MS = 5_000
    }
}
