package com.penonton.ui.activity

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.Keep
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.penonton.R
import com.penonton.data.local.StorageManager
import com.penonton.databinding.ActivityPlayerBinding
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(UnstableApi::class)
@SuppressLint("SetTextI18n")
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var storage: StorageManager

    private var movieId: Int = 0
    private var animeTitle: String = ""
    private var episodeName: String = ""
    private var animePoster: String = ""
    private var currentSid: Int = 1
    private var currentNid: Int = 1
    private var currentPlayUrl: String = ""
    private var currentMovieUrl: String = ""

    private var isScreenLocked = false
    private var isControlsVisible = true
    private var currentAspectRatioIdx = 0
    private val aspectRatios = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom 16:9",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
    )

    private val speedList = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIdx = 2

    // Smooth Gesture control variables
    private lateinit var audioManager: AudioManager
    private var maxVolume = 15
    private var initialVolume = 0
    private var initialBrightness = 0.5f
    private var touchSlop = 0

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isLeftSwipe = false
    private var isHorizontalSwipe = false
    private var isVerticalSwipe = false
    private var isScaling = false
    private var currentScaleFactor = 1.0f
    private var initialPositionMs = 0L
    private var targetSeekPositionMs = 0L
    private var videoDurationMs = 0L
    private var gestureDetector: GestureDetector? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null

    // 2X Speed Press & Hold variables
    private var isFastForwarding = false
    private var previousPlaybackSpeed = 1.0f

    private var currentRawEmbedUrl: String? = null
    private var hasInterceptedM3u8 = false

    private var initialResumePositionMs = 0L
    private var hasAppliedResumePosition = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            saveProgress()
            progressHandler.postDelayed(this, 2500)
        }
    }

    private val hudHandler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable { binding.layoutHud.isVisible = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = StorageManager.getInstance(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        movieId = intent.getIntExtra("MEDIA_ID", 0)
        animeTitle = intent.getStringExtra("MEDIA_TITLE") ?: ""
        episodeName = intent.getStringExtra("EPISODE_NAME") ?: ""
        animePoster = intent.getStringExtra("MEDIA_POSTER") ?: ""
        currentSid = intent.getIntExtra("SID", 1)
        currentNid = intent.getIntExtra("NID", 1)
        currentPlayUrl = intent.getStringExtra("PLAY_URL") ?: intent.getStringExtra("MEDIA_URL") ?: ""
        currentMovieUrl = intent.getStringExtra("MOVIE_URL") ?: intent.getStringExtra("PAGE_URL") ?: ""

        val intentPos = intent.getLongExtra("START_POSITION", -1L)
        initialResumePositionMs = if (intentPos > 0L) {
            intentPos
        } else {
            storage.getEpisodePosition(movieId, currentNid).takeIf { it > 0L }
                ?: storage.getLastWatched(movieId)?.takeIf { it.nid == currentNid }?.positionMs
                ?: 0L
        }

        binding.tvPlayerTitle.text = "$animeTitle - $episodeName"
        binding.btnPlayerBack.setOnClickListener { finish() }

        setupPlayerControls()
        setupGestures()
        loadStream()
    }

    private fun setupPlayerControls() {
        binding.playerView.controllerShowTimeoutMs = 7000

        // Memperbaiki overload resolution ambiguity & type inference error
        binding.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            isControlsVisible = (visibility == View.VISIBLE)
            if (isScreenLocked) return@ControllerVisibilityListener
            if (visibility == View.VISIBLE) {
                binding.playerHeader.animate().cancel()
                binding.playerHeader.isVisible = true
                binding.playerHeader.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(250)
                    .start()
            } else {
                binding.playerHeader.animate().cancel()
                binding.playerHeader.animate()
                    .alpha(0f)
                    .translationY(-binding.playerHeader.height.toFloat().coerceAtLeast(60f))
                    .setDuration(250)
                    .withEndAction {
                        binding.playerHeader.isVisible = false
                    }
                    .start()
            }
        })

        binding.btnAspectRatio.setOnClickListener {
            currentAspectRatioIdx = (currentAspectRatioIdx + 1) % aspectRatios.size
            val (mode, label) = aspectRatios[currentAspectRatioIdx]
            currentScaleFactor = 1.0f
            binding.playerView.scaleX = 1.0f
            binding.playerView.scaleY = 1.0f
            binding.playerView.resizeMode = mode
            showHud(R.drawable.ic_aspect_ratio, label)
        }

        binding.btnSpeed.setOnClickListener {
            val speedNames = speedList.map { "${it}x" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Pilih Kecepatan Putar")
                .setSingleChoiceItems(speedNames, currentSpeedIdx) { dialog, which ->
                    currentSpeedIdx = which
                    val selectedSpeed = speedList[which]
                    previousPlaybackSpeed = selectedSpeed
                    exoPlayer?.playbackParameters = PlaybackParameters(selectedSpeed)
                    binding.btnSpeed.text = "${selectedSpeed}x"
                    dialog.dismiss()
                }
                .show()
        }

        binding.btnPip.setOnClickListener {
            enterPipMode()
        }

        binding.btnLock.setOnClickListener {
            isScreenLocked = true
            isControlsVisible = false
            binding.playerHeader.isVisible = false
            binding.playerView.hideController()
            binding.playerView.useController = false
            binding.btnUnlock.isVisible = true
            showHud(R.drawable.ic_lock, "Layar Terkunci")
        }

        binding.btnUnlock.setOnClickListener {
            isScreenLocked = false
            isControlsVisible = true
            binding.playerView.useController = true
            binding.playerView.showController()
            binding.btnUnlock.isVisible = false
            showHud(R.drawable.ic_lock_open, "Layar Terbuka")
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "Fitur PiP membutuhkan Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        handlePipChange(isInPictureInPictureMode)
    }

    @Deprecated("Deprecated in API 31+", ReplaceWith("onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)"))
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            handlePipChange(isInPictureInPictureMode)
        }
    }

    private fun handlePipChange(isInPip: Boolean) {
        if (isInPip) {
            binding.playerHeader.isVisible = false
            binding.playerView.useController = false
        } else {
            binding.playerHeader.isVisible = !isScreenLocked
            binding.playerView.useController = !isScreenLocked
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScreenLocked) return false
                if (isControlsVisible) {
                    binding.playerView.hideController()
                } else {
                    binding.playerView.showController()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return false
                val screenWidth = binding.playerRoot.width.toFloat().coerceAtLeast(1f)
                if (e.x < screenWidth / 2f) {
                    exoPlayer?.let {
                        val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                        it.seekTo(newPos)
                        showHud(R.drawable.ic_skip_previous, "-10s")
                    }
                } else {
                    exoPlayer?.let {
                        val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                        it.seekTo(newPos)
                        showHud(R.drawable.ic_skip_next, "+10s")
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isScreenLocked || isHorizontalSwipe || isVerticalSwipe || isScaling) return
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        isFastForwarding = true
                        previousPlaybackSpeed = player.playbackParameters.speed
                        player.playbackParameters = PlaybackParameters(2.0f)
                        binding.playerRoot.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        showHud(R.drawable.ic_speed, "2X Speed ⏩\n(Tahan Layar)", autoHide = false)
                    }
                }
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                isHorizontalSwipe = false
                isVerticalSwipe = false

                binding.playerView.pivotX = binding.playerView.width / 2f
                binding.playerView.pivotY = binding.playerView.height / 2f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isScreenLocked) return false
                val factor = detector.scaleFactor
                currentScaleFactor = (currentScaleFactor * factor).coerceIn(0.70f, 3.50f)
                binding.playerView.scaleX = currentScaleFactor
                binding.playerView.scaleY = currentScaleFactor

                val percent = (currentScaleFactor * 100).toInt()
                showHud(R.drawable.ic_aspect_ratio, "Zoom: ${percent}%", autoHide = false)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                hudHandler.removeCallbacks(hideHudRunnable)
                hudHandler.postDelayed(hideHudRunnable, 1000)
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isScreenLocked) {
            val unlockView = binding.btnUnlock
            if (unlockView.isVisible) {
                val rect = Rect()
                unlockView.getGlobalVisibleRect(rect)
                if (rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    return super.dispatchTouchEvent(ev)
                }
            }
            return true
        }

        if (binding.playerHeader.isVisible) {
            val headerRect = Rect()
            binding.playerHeader.getGlobalVisibleRect(headerRect)
            if (headerRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                return super.dispatchTouchEvent(ev)
            }
        }

        if (ev.pointerCount >= 2) {
            isHorizontalSwipe = false
            isVerticalSwipe = false
            scaleGestureDetector?.onTouchEvent(ev)
            return true
        }

        val screenWidth = binding.playerRoot.width.toFloat().coerceAtLeast(1f)
        val screenHeight = binding.playerRoot.height.toFloat().coerceAtLeast(1f)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isLeftSwipe = ev.rawX < (screenWidth / 2f)
                isHorizontalSwipe = false
                isVerticalSwipe = false
                isScaling = false
                initialPositionMs = exoPlayer?.currentPosition ?: 0L
                videoDurationMs = exoPlayer?.duration?.coerceAtLeast(1L) ?: 1L
                targetSeekPositionMs = initialPositionMs

                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val lp = window.attributes
                initialBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isFastForwarding && !isScaling) {
                    val deltaX = ev.rawX - initialTouchX
                    val deltaY = ev.rawY - initialTouchY

                    if (!isVerticalSwipe && abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY) * 1.2f) {
                        isHorizontalSwipe = true
                        val seekDeltaMs = ((deltaX / screenWidth) * 90000).toLong()
                        targetSeekPositionMs = (initialPositionMs + seekDeltaMs).coerceIn(0L, videoDurationMs)

                        val diffSeconds = (targetSeekPositionMs - initialPositionMs) / 1000
                        val sign = if (diffSeconds >= 0) "+${diffSeconds}s" else "${diffSeconds}s"
                        val timeStr = "${formatTime(targetSeekPositionMs)} / ${formatTime(videoDurationMs)}"
                        val iconRes = if (diffSeconds >= 0) R.drawable.ic_skip_next else R.drawable.ic_skip_previous

                        showHud(iconRes, "$sign\n[$timeStr]")
                        return true
                    } else if (!isHorizontalSwipe && abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX) * 1.2f) {
                        isVerticalSwipe = true
                        val deltaPercent = -deltaY / screenHeight

                        if (isLeftSwipe) {
                            val lp = window.attributes
                            val newBrightness = (initialBrightness + deltaPercent * 1.2f).coerceIn(0.01f, 1.0f)
                            lp.screenBrightness = newBrightness
                            window.attributes = lp
                            showHud(R.drawable.ic_brightness, "Kecerahan: ${(newBrightness * 100).toInt()}%")
                        } else {
                            val volDelta = (deltaPercent * maxVolume * 1.2f).toInt()
                            val newVol = (initialVolume + volDelta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            val volPercent = ((newVol.toFloat() / maxVolume) * 100).toInt()
                            showHud(R.drawable.ic_volume, "Volume: $volPercent%")
                        }
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScaling = false
                if (isFastForwarding) {
                    exoPlayer?.playbackParameters = PlaybackParameters(previousPlaybackSpeed)
                    isFastForwarding = false
                    showHud(R.drawable.ic_speed, "${previousPlaybackSpeed}x Normal", autoHide = true)
                }

                if (isHorizontalSwipe) {
                    exoPlayer?.seekTo(targetSeekPositionMs)
                    hudHandler.removeCallbacks(hideHudRunnable)
                    hudHandler.postDelayed(hideHudRunnable, 600)
                    isHorizontalSwipe = false
                    return true
                }

                if (isVerticalSwipe) {
                    isVerticalSwipe = false
                    hudHandler.removeCallbacks(hideHudRunnable)
                    hudHandler.postDelayed(hideHudRunnable, 600)
                    return true
                }
            }
        }

        gestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun showHud(iconRes: Int, text: String, autoHide: Boolean = true) {
        binding.ivHudIcon.setImageResource(iconRes)
        binding.tvHudText.text = text
        binding.layoutHud.isVisible = true

        hudHandler.removeCallbacks(hideHudRunnable)
        if (autoHide) {
            hudHandler.postDelayed(hideHudRunnable, 1200)
        }
    }

    private fun loadStream() {
        binding.playerProgressBar.isVisible = true
        hasInterceptedM3u8 = false
        val playUrl = intent.getStringExtra("PLAY_URL")
            ?: intent.getStringExtra("MEDIA_URL")
            ?: currentPlayUrl.takeIf { it.isNotEmpty() }
            ?: currentMovieUrl.takeIf { it.isNotEmpty() }

        lifecycleScope.launch {
            try {
                val stream = com.penonton.data.parser.Lk21Parser.getStream(
                    movieId = movieId,
                    sid = currentSid,
                    nid = currentNid,
                    fallbackUrl = playUrl,
                    title = animeTitle
                )
                binding.playerProgressBar.isVisible = false
                currentRawEmbedUrl = stream.embedUrl
                if (currentPlayUrl.isEmpty() && stream.rawUrl.isNotEmpty()) {
                    currentPlayUrl = stream.rawUrl
                }

                if (!stream.m3u8Url.isNullOrEmpty()) {
                    playHlsStream(stream.m3u8Url)
                } else if (!stream.embedUrl.isNullOrEmpty()) {
                    playEmbedStream(stream.embedUrl)
                } else {
                    Toast.makeText(this@PlayerActivity, "Stream tidak dapat dimuat", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.playerProgressBar.isVisible = false
                Toast.makeText(this@PlayerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun playHlsStream(m3u8Url: String) {
        binding.webViewPlayer.isVisible = false
        binding.playerView.isVisible = true

        val referer = when {
            m3u8Url.contains("playcdn.de") -> "https://playcdn.de/"
            m3u8Url.contains("videonode.de") -> "https://videonode.de/"
            m3u8Url.contains("lk21") -> "https://tv12.lk21official.cc/"
            m3u8Url.contains("nontondrama") -> "https://tv9.nontondrama.my/"
            m3u8Url.contains("rumble.com") -> "https://rumble.com/"
            m3u8Url.contains("cdn.rumble.cloud") -> "https://rumble.com/"
            m3u8Url.contains("doubanio.com") -> "https://movie.douban.com/"
            else -> "https://playcdn.de/"
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to referer,
                "Origin" to referer.trimEnd('/')
            ))
            .setAllowCrossProtocolRedirects(true)

        val mediaItem = MediaItem.Builder()
            .setUri(m3u8Url.toUri())
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        hasAppliedResumePosition = false
        val targetPos = initialResumePositionMs

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaSource(mediaSource)
            if (targetPos > 1000L) {
                seekTo(targetPos)
            }
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> binding.playerProgressBar.isVisible = true
                        Player.STATE_READY -> {
                            binding.playerProgressBar.isVisible = false
                            if (!hasAppliedResumePosition && targetPos > 1000L) {
                                hasAppliedResumePosition = true
                                if (abs(currentPosition - targetPos) > 2000L) {
                                    seekTo(targetPos)
                                }
                                val mins = targetPos / 60000
                                val secs = (targetPos % 60000) / 1000
                                showHud(R.drawable.ic_skip_next, "Melanjutkan dari %02d:%02d".format(mins, secs))
                            }
                        }
                        Player.STATE_ENDED -> {
                            binding.playerProgressBar.isVisible = false
                            Toast.makeText(this@PlayerActivity, "Episode selesai", Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        startProgressSaver()
                    } else {
                        stopProgressSaver()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    binding.playerProgressBar.isVisible = false
                    val fallback = currentRawEmbedUrl ?: m3u8Url
                    if (fallback.isNotEmpty()) {
                        playEmbedStream(fallback)
                    }
                }
            })
        }

        binding.playerView.player = exoPlayer
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun playEmbedStream(embedUrl: String) {
        binding.playerView.isVisible = false
        binding.webViewPlayer.isVisible = true

        val targetPos = initialResumePositionMs
        val startSec = if (targetPos > 1000L) targetPos / 1000.0 else 0.0

        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webViewPlayer, true)
        }

        binding.webViewPlayer.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        binding.webViewPlayer.addJavascriptInterface(object {
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onTimeUpdate(currentTimeSec: Float, durationSec: Float) {
                val posMs = (currentTimeSec * 1000).toLong()
                val durMs = (durationSec * 1000).toLong()
                if (posMs > 1000L) {
                    storage.saveHistory(
                        movieId = movieId,
                        title = animeTitle,
                        poster = animePoster,
                        episodeName = episodeName,
                        sid = currentSid,
                        nid = currentNid,
                        positionMs = posMs,
                        durationMs = durMs,
                        playUrl = currentPlayUrl,
                        movieUrl = currentMovieUrl
                    )
                }
            }
        }, "AndroidBridge")

        binding.webViewPlayer.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                return false
            }
        }

        binding.webViewPlayer.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                val url = uri.toString()

                val allowedHosts = listOf("videonode.de", "playcdn.de", "lk21official.cc", "nontondrama.my", "gudangvape.com", "dailymotion.com", "geo.dailymotion.com", "dmcdn.net", "rumble.com", "rumble.cloud")
                val isHostAllowed = allowedHosts.any { uri.host?.contains(it) == true }

                return !isHostAllowed || url.startsWith("intent:") || url.startsWith("market:") || url.startsWith("whatsapp:") || url.startsWith("tg:")
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                val uri = request?.url ?: return null
                val url = uri.toString()

                if ((url.contains(".m3u8") || url.contains("master.txt")) && !hasInterceptedM3u8) {
                    hasInterceptedM3u8 = true
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            try {
                                binding.webViewPlayer.stopLoading()
                                binding.webViewPlayer.loadUrl("about:blank")
                            } catch (_: Exception) {}
                            playHlsStream(url)
                        }
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.playerProgressBar.isVisible = false

                val adBlockJs = """
                    javascript:(function() {
                        window.open = function() { return null; };
                        window.alert = function() {};
                        window.confirm = function() { return false; };
                        window.prompt = function() { return null; };
                        setInterval(function() {
                            var overlays = document.querySelectorAll('div[style*="z-index: 2147483647"], div[id*="pop"], div[class*="pop"], div[id*="ad"], div[class*="ad"]');
                            for (var i = 0; i < overlays.length; i++) {
                                if (overlays[i].tagName !== 'VIDEO' && overlays[i].tagName !== 'IFRAME') {
                                    overlays[i].remove();
                                }
                            }
                        }, 1000);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(adBlockJs, null)
            }
        }

        val hlsJs = try {
            assets.open("hls.min.js").bufferedReader().use { it.readText() }
        } catch (_: Exception) { "" }

        val baseUrl = if (currentPlayUrl.contains("nontondrama") || embedUrl.contains("nontondrama")) {
            "https://tv9.nontondrama.my/"
        } else {
            "https://tv12.lk21official.cc/"
        }

        val htmlContent = if (embedUrl.contains(".m3u8")) {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>
                <style>
                    body, html { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                    video { width:100%; height:100%; object-fit:contain; }
                </style>
                <script>$hlsJs</script>
            </head>
            <body>
                <video id="video" controls autoplay playsinline webkit-playsinline></video>
                <script>
                    var video = document.getElementById('video');
                    var videoSrc = '$embedUrl';
                    var startSec = $startSec;

                    function applyResume() {
                        if (startSec > 1 && Math.abs(video.currentTime - startSec) > 2) {
                            try { video.currentTime = startSec; } catch(e){}
                        }
                    }

                    if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                        var hls = new Hls({
                            enableWorker: true,
                            lowLatencyMode: false
                        });
                        hls.loadSource(videoSrc);
                        hls.attachMedia(video);
                        hls.on(Hls.Events.MANIFEST_PARSED, function() {
                            applyResume();
                            video.play().catch(function(e) {
                                console.log('Autoplay error:', e);
                            });
                        });
                    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                        video.src = videoSrc;
                        video.addEventListener('loadedmetadata', function() {
                            applyResume();
                            video.play();
                        });
                    }

                    video.addEventListener('canplay', applyResume);
                    video.addEventListener('timeupdate', function() {
                        if (window.AndroidBridge && video.currentTime > 1) {
                            window.AndroidBridge.onTimeUpdate(video.currentTime, video.duration || 0);
                        }
                    });
                </script>
            </body>
            </html>
            """.trimIndent()
        } else {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>
                <style>
                    body, html { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                    iframe { border:none; width:100%; height:100%; display:block; }
                </style>
            </head>
            <body>
                <iframe src='$embedUrl' allowfullscreen='true' allow='autoplay; encrypted-media; fullscreen; picture-in-picture'></iframe>
            </body>
            </html>
            """.trimIndent()
        }

        binding.webViewPlayer.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
    }

    private fun startProgressSaver() {
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.postDelayed(progressRunnable, 2500)
    }

    private fun stopProgressSaver() {
        progressHandler.removeCallbacks(progressRunnable)
        saveProgress()
    }

    private fun saveProgress() {
        exoPlayer?.let { player ->
            val pos = player.currentPosition
            val dur = player.duration.takeIf { it > 0L } ?: 0L
            if (pos > 1000L) {
                storage.saveHistory(
                    movieId = movieId,
                    title = animeTitle,
                    poster = animePoster,
                    episodeName = episodeName,
                    sid = currentSid,
                    nid = currentNid,
                    positionMs = pos,
                    durationMs = dur,
                    playUrl = currentPlayUrl,
                    movieUrl = currentMovieUrl
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopProgressSaver()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressSaver()
        hudHandler.removeCallbacks(hideHudRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }
}