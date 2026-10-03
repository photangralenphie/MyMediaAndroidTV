package com.photangralenphie.mymedia.androidtv

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
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
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
import com.photangralenphie.mymedia.androidtv.data.ApiClient
import com.photangralenphie.mymedia.androidtv.data.AppPreferences
import com.photangralenphie.mymedia.androidtv.data.DownloadStore
import com.photangralenphie.mymedia.androidtv.data.MediaUpdate
import kotlinx.coroutines.launch

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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setControllerAutoShow(true)
            setControllerShowTimeoutMs(CONTROLS_TIMEOUT_MS)
            setControllerAnimationEnabled(true)
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

        val accentColor = runCatching {
            Color.parseColor(AppPreferences(this).load().appearance.accentHex)
        }.getOrDefault(DEFAULT_ACCENT_COLOR)
        stylePlayerControls(accentColor)

        titleView = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE)
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = roundedBackground(
                color = Color.argb(205, 17, 21, 28),
                radiusDp = 16f,
                strokeColor = Color.argb(38, 255, 255, 255),
            )
            elevation = dp(10).toFloat()
        }
        root.addView(
            titleView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(32), dp(28), dp(32), 0)
            },
        )
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            updateTitleVisibility(visibility == View.VISIBLE)
        })
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
                        Player.STATE_READY -> Unit
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

    private fun updateTitleVisibility(controlsVisible: Boolean) {
        if (titleView.text == getString(R.string.video_unavailable)) return
        titleView.animate().cancel()
        titleView.animate()
            .alpha(if (controlsVisible) 1f else 0f)
            .setDuration(220)
            .start()
    }

    private fun stylePlayerControls(accentColor: Int) {
        playerView.findViewById<View>(Media3UiR.id.exo_controls_background)?.background =
            android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    Color.argb(232, 4, 6, 10),
                    Color.argb(142, 4, 6, 10),
                    Color.TRANSPARENT,
                ),
            )

        playerView.findViewById<FrameLayout>(Media3UiR.id.exo_bottom_bar)?.apply {
            background = roundedBackground(
                color = Color.argb(224, 17, 21, 28),
                radiusDp = 18f,
                strokeColor = Color.argb(35, 255, 255, 255),
            )
            elevation = dp(12).toFloat()
            (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = dp(68)
                params.setMargins(dp(30), params.topMargin, dp(30), dp(22))
                layoutParams = params
            }
        }

        playerView.findViewById<LinearLayout>(Media3UiR.id.exo_center_controls)?.apply {
            background = roundedBackground(
                color = Color.argb(205, 17, 21, 28),
                radiusDp = 40f,
                strokeColor = Color.argb(38, 255, 255, 255),
            )
            setPadding(dp(12), dp(8), dp(12), dp(8))
            elevation = dp(14).toFloat()
        }

        playerView.findViewById<LinearLayout>(Media3UiR.id.exo_time)?.apply {
            background = roundedBackground(Color.argb(110, 255, 255, 255), 24f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }

        playerView.findViewById<DefaultTimeBar>(Media3UiR.id.exo_progress)?.apply {
            setPlayedColor(accentColor)
            setScrubberColor(accentColor)
            setBufferedColor(Color.argb(150, 255, 255, 255))
            setUnplayedColor(Color.argb(70, 255, 255, 255))
            setAdMarkerColor(Color.WHITE)
            (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.setMargins(dp(48), params.topMargin, dp(48), dp(82))
                layoutParams = params
            }
        }

        val roundButtonIds = intArrayOf(
            Media3UiR.id.exo_play_pause,
            Media3UiR.id.exo_subtitle,
            Media3UiR.id.exo_settings,
            Media3UiR.id.exo_fullscreen,
            Media3UiR.id.exo_minimal_fullscreen,
            Media3UiR.id.exo_overflow_show,
            Media3UiR.id.exo_overflow_hide,
        )
        roundButtonIds.forEach { id ->
            playerView.findViewById<ImageButton>(id)?.styleRoundButton(accentColor)
        }

        intArrayOf(Media3UiR.id.exo_rew_with_amount, Media3UiR.id.exo_ffwd_with_amount).forEach { id ->
            playerView.findViewById<Button>(id)?.apply {
                backgroundTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(accentColor, Color.WHITE),
                )
                installFocusAnimation()
            }
        }
    }

    private fun ImageButton.styleRoundButton(accentColor: Int) {
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                roundedBackground(accentColor, 40f),
            )
            addState(
                intArrayOf(),
                roundedBackground(Color.argb(76, 255, 255, 255), 40f),
            )
        }
        imageTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(Color.BLACK, Color.WHITE),
        )
        installFocusAnimation()
    }

    private fun View.installFocusAnimation() {
        setOnFocusChangeListener { view, hasFocus ->
            view.animate().cancel()
            view.animate()
                .scaleX(if (hasFocus) 1.13f else 1f)
                .scaleY(if (hasFocus) 1.13f else 1f)
                .setDuration(140)
                .start()
            view.elevation = if (hasFocus) dp(18).toFloat() else 0f
        }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
    ) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

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
            runCatching {
                ApiClient(base).updateMedia(
                    kind,
                    id,
                    MediaUpdate(progressMinutes = minutes, isWatched = watched),
                )
            }.onFailure { error ->
                Log.w(TAG, "Could not sync playback progress", error)
            }
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
        private const val DEFAULT_ACCENT_COLOR = 0xFFFF981F.toInt()
        private const val TAG = "VideoActivity"
    }
}
