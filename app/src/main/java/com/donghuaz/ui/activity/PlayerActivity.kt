package com.donghuaz.ui.activity

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.*
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.donghuaz.R
import com.donghuaz.data.local.StorageManager
import com.donghuaz.data.model.StreamResult
import com.donghuaz.data.parser.DonghuaParser
import com.donghuaz.databinding.ActivityPlayerBinding
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var storage: StorageManager

    private var animeId: Int = 0
    private var animeTitle: String = ""
    private var episodeName: String = ""
    private var animePoster: String = ""
    private var currentSid: Int = 1
    private var currentNid: Int = 1

    private var isScreenLocked = false
    private var currentAspectRatioIdx = 0
    private val aspectRatios = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom 16:9",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
    )

    private val speedList = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIdx = 2

    // Gesture control variables
    private lateinit var audioManager: AudioManager
    private var maxVolume = 15
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isLeftSwipe = false
    private var isHorizontalSwipe = false
    private var initialPositionMs = 0L
    private var targetSeekPositionMs = 0L
    private var videoDurationMs = 0L

    // 2X Speed Press & Hold variables
    private var isFastForwarding = false
    private var previousPlaybackSpeed = 1.0f

    private var isEmbedPlaying = false
    private var embedStartTimeMs = 0L

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
    private val hideHudRunnable = Runnable { binding.layoutHud.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = StorageManager.getInstance(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        animeId = intent.getIntExtra("ANIME_ID", 0)
        animeTitle = intent.getStringExtra("ANIME_TITLE") ?: ""
        episodeName = intent.getStringExtra("EPISODE_NAME") ?: ""
        animePoster = intent.getStringExtra("ANIME_POSTER") ?: ""
        currentSid = intent.getIntExtra("SID", 1)
        currentNid = intent.getIntExtra("NID", 1)

        val intentPos = intent.getLongExtra("START_POSITION", -1L)
        initialResumePositionMs = if (intentPos > 0L) {
            intentPos
        } else {
            storage.getEpisodePosition(animeId, currentNid).takeIf { it > 0L }
                ?: storage.getLastWatched(animeId)?.takeIf { it.nid == currentNid }?.positionMs
                ?: 0L
        }

        binding.tvPlayerTitle.text = "$animeTitle - $episodeName"
        binding.btnPlayerBack.setOnClickListener { finish() }

        setupPlayerControls()
        setupGestures()
        loadStream()
    }

    private fun setupPlayerControls() {
        // 1. Aspect Ratio Toggle
        binding.btnAspectRatio.setOnClickListener {
            currentAspectRatioIdx = (currentAspectRatioIdx + 1) % aspectRatios.size
            val (mode, label) = aspectRatios[currentAspectRatioIdx]
            binding.playerView.resizeMode = mode
            showHud(R.drawable.ic_aspect_ratio, label)
        }

        // 2. Playback Speed Selector Dialog
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

        // 3. PiP Mode Button
        binding.btnPip.setOnClickListener {
            enterPipMode()
        }

        // 4. Lock Screen Mode Toggle
        binding.btnLock.setOnClickListener {
            isScreenLocked = true
            binding.playerHeader.visibility = View.GONE
            binding.playerView.useController = false
            binding.btnUnlock.visibility = View.VISIBLE
            showHud(R.drawable.ic_lock, "Layar Terkunci")
        }

        binding.btnUnlock.setOnClickListener {
            isScreenLocked = false
            binding.playerHeader.visibility = View.VISIBLE
            binding.playerView.useController = true
            binding.btnUnlock.visibility = View.GONE
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

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            binding.playerHeader.visibility = View.GONE
            binding.playerView.useController = false
        } else {
            binding.playerHeader.visibility = if (!isScreenLocked) View.VISIBLE else View.GONE
            binding.playerView.useController = !isScreenLocked
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return false
                val screenWidth = binding.playerRoot.width
                if (e.x < screenWidth / 2) {
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
                if (isScreenLocked || isHorizontalSwipe) return
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

        binding.playerRoot.setOnTouchListener { _, event ->
            if (isScreenLocked) return@setOnTouchListener false
            gestureDetector.onTouchEvent(event)

            val screenWidth = binding.playerRoot.width
            val screenHeight = binding.playerRoot.height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.x
                    initialTouchY = event.y
                    isLeftSwipe = event.x < screenWidth / 2
                    isHorizontalSwipe = false
                    initialPositionMs = exoPlayer?.currentPosition ?: 0L
                    videoDurationMs = exoPlayer?.duration?.coerceAtLeast(1L) ?: 1L
                    targetSeekPositionMs = initialPositionMs
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isFastForwarding) return@setOnTouchListener false

                    val deltaX = event.x - initialTouchX
                    val deltaY = event.y - initialTouchY

                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 35) {
                        isHorizontalSwipe = true

                        val seekDeltaMs = ((deltaX / screenWidth) * 90000).toLong()
                        targetSeekPositionMs = (initialPositionMs + seekDeltaMs).coerceIn(0L, videoDurationMs)

                        val diffSeconds = (targetSeekPositionMs - initialPositionMs) / 1000
                        val sign = if (diffSeconds >= 0) "+${diffSeconds}s" else "${diffSeconds}s"
                        val timeStr = "${formatTime(targetSeekPositionMs)} / ${formatTime(videoDurationMs)}"
                        val iconRes = if (diffSeconds >= 0) R.drawable.ic_skip_next else R.drawable.ic_skip_previous

                        showHud(iconRes, "$sign\n[$timeStr]")
                    }
                    else if (!isHorizontalSwipe && abs(deltaY) > 30) {
                        val percentDelta = -deltaY / screenHeight.toFloat()
                        if (isLeftSwipe) {
                            val lp = window.attributes
                            val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                            val newBrightness = (currentBrightness + percentDelta * 0.1f).coerceIn(0.01f, 1.0f)
                            lp.screenBrightness = newBrightness
                            window.attributes = lp
                            showHud(R.drawable.ic_brightness, "Kecerahan: ${(newBrightness * 100).toInt()}%")
                        } else {
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val volChange = (percentDelta * maxVolume * 0.5f).toInt()
                            val newVol = (currentVol + volChange).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            val volPercent = ((newVol.toFloat() / maxVolume) * 100).toInt()
                            showHud(R.drawable.ic_volume, "Volume: $volPercent%")
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                    }
                }
            }
            false
        }
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
        binding.layoutHud.visibility = View.VISIBLE

        hudHandler.removeCallbacks(hideHudRunnable)
        if (autoHide) {
            hudHandler.postDelayed(hideHudRunnable, 1200)
        }
    }

    private fun loadStream() {
        binding.playerProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val stream = DonghuaParser.getStream(animeId, currentSid, currentNid)
                binding.playerProgressBar.visibility = View.GONE

                if (!stream.m3u8Url.isNullOrEmpty()) {
                    playHlsStream(stream.m3u8Url)
                } else if (!stream.embedUrl.isNullOrEmpty()) {
                    playEmbedStream(stream.embedUrl)
                } else {
                    Toast.makeText(this@PlayerActivity, "Stream tidak dapat dimuat", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.playerProgressBar.visibility = View.GONE
                Toast.makeText(this@PlayerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun playHlsStream(m3u8Url: String, provider: String = "") {
        binding.webViewPlayer.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE

        val referer = when {
            m3u8Url.contains("rumble.com") -> "https://rumble.com/"
            m3u8Url.contains("cdn.rumble.cloud") -> "https://rumble.com/"
            m3u8Url.contains("doubanio.com") -> "https://movie.douban.com/"
            m3u8Url.contains("donghuafun.com") -> "https://donghuafun.com/"
            else -> "https://donghuafun.com/"
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to referer,
                "Origin" to referer.trimEnd('/')
            ))
            .setAllowCrossProtocolRedirects(true)

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(m3u8Url))
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
                        Player.STATE_BUFFERING -> binding.playerProgressBar.visibility = View.VISIBLE
                        Player.STATE_READY -> {
                            binding.playerProgressBar.visibility = View.GONE
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
                            binding.playerProgressBar.visibility = View.GONE
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
                    binding.playerProgressBar.visibility = View.GONE
                    playEmbedStream(m3u8Url)
                }
            })
        }

        binding.playerView.player = exoPlayer
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun playEmbedStream(embedUrl: String) {
        binding.playerView.visibility = View.GONE
        binding.webViewPlayer.visibility = View.VISIBLE

        isEmbedPlaying = true
        embedStartTimeMs = System.currentTimeMillis()

        val targetPos = initialResumePositionMs
        val startSec = if (targetPos > 1000L) targetPos / 1000.0 else 0.0

        binding.webViewPlayer.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        binding.webViewPlayer.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onTimeUpdate(currentTimeSec: Float, durationSec: Float) {
                val posMs = (currentTimeSec * 1000).toLong()
                val durMs = (durationSec * 1000).toLong()
                if (posMs > 1000L) {
                    storage.saveHistory(
                        animeId = animeId,
                        title = animeTitle,
                        poster = animePoster,
                        episodeName = episodeName,
                        sid = currentSid,
                        nid = currentNid,
                        positionMs = posMs,
                        durationMs = durMs
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

                val allowedHosts = listOf("donghuafun.com", "dailymotion.com", "geo.dailymotion.com", "dmcdn.net", "ksrteam.org", "rumble.com", "rumble.cloud", "ganjingworld.com", "ganjing.com")
                val isHostAllowed = allowedHosts.any { uri.host?.contains(it) == true }

                if (!isHostAllowed || url.startsWith("intent:") || url.startsWith("market:") || url.startsWith("whatsapp:") || url.startsWith("tg:")) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.playerProgressBar.visibility = View.GONE

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

        binding.webViewPlayer.loadDataWithBaseURL("https://donghuafun.com", htmlContent, "text/html", "UTF-8", null)
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
                    animeId = animeId,
                    title = animeTitle,
                    poster = animePoster,
                    episodeName = episodeName,
                    sid = currentSid,
                    nid = currentNid,
                    positionMs = pos,
                    durationMs = dur
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
