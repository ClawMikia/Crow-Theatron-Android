package com.crowtheatron.app.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crowtheatron.app.R
import com.crowtheatron.app.data.ChapterMarker
import com.crowtheatron.app.data.EnhancementMode
import com.crowtheatron.app.data.VideoEntity
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityPlayerBinding
import com.crowtheatron.app.profile.PlaybackProfilesActivity
import com.crowtheatron.app.service.PlaybackService
import com.crowtheatron.app.ui.setContentWithCrowInsets
import com.crowtheatron.app.util.FormatUtils
import com.crowtheatron.app.util.VideoEnhancement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val repo by lazy { VideoRepository(this) }

    private var player: ExoPlayer? = null
    private lateinit var playlistIds: LongArray
    private var playlistIndex = 0
    private lateinit var working: VideoEntity

    private val handler = Handler(Looper.getMainLooper())
    private var userScrubbingPlayback = false
    private var playerReady = false
    private var isFullscreen = false
    private var isBindingUi = false
    private var chapters = listOf<ChapterMarker>()

    private var currentSpeed  = 1.0f
    private var currentVolume = 1.0f

    // ── Gesture state ─────────────────────────────────────────────────────────
    private var isGestureSeeking = false
    private var gestureSeekStartPos = 0L
    private var gestureBrightnessStart = 0f
    private var gestureVolumeStart = 0f
    private var currentBrightness = 0.5f // 0..1, affects window brightness

    // ── Scale (pinch zoom) ────────────────────────────────────────────────────
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentZoom = 1f

    // ── Service binding ───────────────────────────────────────────────────────
    private var playbackService: PlaybackService? = null
    private var serviceBound = false
    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as PlaybackService.LocalBinder
            val service = lb.getService()
            playbackService = service
            serviceBound = true
            
            val sharedPlayer = service.getPlayer()
            if (sharedPlayer != null) {
                sharedPlayer.removeListener(playerListener)
                player = sharedPlayer
                sharedPlayer.addListener(playerListener)
                binding.playerView.player = sharedPlayer
                
                val currentMedia = sharedPlayer.currentMediaItem
                val workingUri = working.contentUri.toString()
                
                if (currentMedia == null || currentMedia.localConfiguration?.uri?.toString() != workingUri) {
                    attachCurrentMedia(play = true)
                } else {
                    isBindingUi = true
                    try {
                        bindUiFromWorking()
                        updatePlayPauseIcon()
                        tickTimeline()
                    } finally {
                        isBindingUi = false
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
            playbackService = null
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!userScrubbingPlayback && playerReady) tickTimeline()
            handler.postDelayed(this, 450L)
        }
    }

    // ── ExoPlayer listener ────────────────────────────────────────────────────
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            playerReady = state == Player.STATE_READY || state == Player.STATE_BUFFERING
            
            if (state == Player.STATE_READY) {
                val contentDur = player?.contentDuration ?: C.TIME_UNSET
                if (contentDur != C.TIME_UNSET && contentDur > 0L) {
                    if (working.durationMs <= 0L || Math.abs(working.durationMs - contentDur) > 1000) {
                        working = working.copy(durationMs = contentDur)
                        persistPrefs()
                    }
                }
                
                isBindingUi = true
                try {
                    bindTrimSeekers()
                } finally {
                    isBindingUi = false
                }
                
                applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
                applyZoom(working.zoomLevel)
                tickTimeline()
            }
            
            if (state == Player.STATE_ENDED) {
                handleTrimEndReached()
            }
            updatePlayPauseIcon()
            updateServiceNotification()
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon()
            if (isPlaying) tickTimeline()
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@PlayerActivity,
                "Playback error: ${error.message ?: "code ${error.errorCode}"}",
                Toast.LENGTH_LONG).show()
            // If error is clipping related or source related, try to re-attach without clipping
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
                attachCurrentMedia(play = true)
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val startId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        var ids = intent.getLongArrayExtra(EXTRA_PLAYLIST_IDS)
        if ((ids == null || ids.isEmpty()) && startId > 0L) ids = longArrayOf(startId)
        playlistIds = ids ?: longArrayOf()
        if (playlistIds.isEmpty()) { showAndFinish("Missing video"); return }

        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0).coerceIn(0, playlistIds.lastIndex)
        if (startId > 0L) { val i = playlistIds.indexOf(startId); if (i >= 0) playlistIndex = i }

        val initial = repo.getById(playlistIds[playlistIndex])
        if (initial == null) { showAndFinish("Video not in library"); return }
        working = initial
        binding.playerView.useController = false

        currentSpeed  = working.playbackSpeed.coerceIn(0.5f, 2.0f)
        currentVolume = working.volumeLevel.coerceIn(0f, 1f)
        currentZoom   = working.zoomLevel.coerceIn(1f, 3f)
        currentBrightness = 0.5f

        chapters = repo.listChapters(working.id)

        setupGestures()
        setupEnhancementSpinner()
        bindUiFromWorking()
        setupControls()
        startAndBindService()
        initPlayerIfNeeded()
        handler.post(tickRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.BLACK
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        persistProgress()
    }

    override fun onStop() {
        persistProgress()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.removeListener(playerListener)
        if (!isInPictureInPictureMode) {
            player?.pause()
        }
        if (serviceBound) {
            unbindService(serviceConn)
            serviceBound = false
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPiP()
        }
    }

    override fun onPictureInPictureModeChanged(isInPiPMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPiPMode, newConfig)
        if (isInPiPMode) {
            binding.toolbar.visibility = View.GONE
        } else {
            binding.toolbar.visibility = View.VISIBLE
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // When the system rotates (e.g. user physically tilts phone), sync fullscreen state
        if (isFullscreen) {
            enforceFullscreenLayout()
        }
    }

    // ── Service ───────────────────────────────────────────────────────────────

    private fun startAndBindService() {
        val intent = Intent(this, PlaybackService::class.java)
        startService(intent)
        bindService(intent, serviceConn, BIND_AUTO_CREATE)
    }

    // ── Player setup ──────────────────────────────────────────────────────────

    private fun initPlayerIfNeeded() {
        // We now rely entirely on the service player to avoid decoder contention
        // and ensure background playback works correctly.
        if (player == null && serviceBound) {
            val sharedPlayer = playbackService?.getPlayer()
            if (sharedPlayer != null) {
                player = sharedPlayer
                sharedPlayer.addListener(playerListener)
                binding.playerView.player = sharedPlayer
                attachCurrentMedia(play = true)
            }
        }
    }

    private fun mediaItemFor(e: VideoEntity): MediaItem {
        // We completely remove ClippingConfiguration to prevent IllegalClippingException
        // which occurs on many MP4/progressive containers. Trimming is now handled
        // manually in tickTimeline and attachCurrentMedia.
        return MediaItem.Builder()
            .setUri(e.contentUri)
            .setMediaId(e.id.toString())
            .build()
    }

    private fun attachCurrentMedia(play: Boolean) {
        val exo = player ?: return
        
        val item = mediaItemFor(working)
        val currentUri = exo.currentMediaItem?.localConfiguration?.uri?.toString()
        val newUri = item.localConfiguration?.uri?.toString()
        
        // Only reset the media item if the URI has actually changed
        if (currentUri != newUri) {
            exo.stop()
            exo.clearMediaItems()
            exo.setMediaItem(item)
            exo.prepare()
            
            val startMs = working.trimStartMs.coerceAtLeast(0L)
            val resumePos = if (working.positionMs > startMs) working.positionMs else startMs
            if (resumePos > 0L) exo.seekTo(resumePos)
        }
        
        // Always apply current parameters
        applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
        exo.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.playWhenReady = play
        
        binding.playerView.postDelayed({ applyEnhancementMatrix() }, 150L)
        updatePlayPauseIcon()
        bindUiFromWorking()
        playbackService?.updateNotification(working.title, if (play) "Playing" else "Paused")
    }

    private fun applyPitchAndSpeed(semitones: Int, speed: Float) {
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        player?.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2.0f), pitch)
    }

    // ── Enhancement ───────────────────────────────────────────────────────────

    private fun applyEnhancementMatrix() {
        val matrix = VideoEnhancement.buildCombinedMatrix(
            mode       = working.enhancement,
            brightness = working.brightness,
            contrast   = working.contrast,
            saturation = working.saturation,
            hue        = working.hue,
        )

        val o = binding.enhancementOverlay
        when (working.enhancement) {
            EnhancementMode.NONE         -> o.visibility = View.GONE
            EnhancementMode.WARM_FILM    -> { o.setBackgroundColor(Color.argb(35,255,180,80)); o.visibility = View.VISIBLE }
            EnhancementMode.COOL_HDR_SIM -> { o.setBackgroundColor(Color.argb(30,80,140,255)); o.visibility = View.VISIBLE }
            EnhancementMode.NIGHT_MODE   -> { o.setBackgroundColor(Color.argb(40,255,80,0)); o.visibility = View.VISIBLE }
            EnhancementMode.EYE_COMFORT  -> { o.setBackgroundColor(Color.argb(30,255,200,50)); o.visibility = View.VISIBLE }
            else                         -> o.visibility = View.GONE
        }
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────

    private fun applyZoom(zoom: Float) {
        val z = zoom.coerceIn(1f, 3f)
        currentZoom = z
        binding.playerView.scaleX = z
        binding.playerView.scaleY = z
    }

    // ── Gestures ──────────────────────────────────────────────────────────────

    private fun setupGestures() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newZoom = (currentZoom * detector.scaleFactor).coerceIn(1f, 3f)
                applyZoom(newZoom)
                working = working.copy(zoomLevel = newZoom)
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val screenWidth = binding.playerView.width.toFloat()
                val tapX = e.x
                if (tapX < screenWidth / 3f) {
                    jumpBy(-10)
                    showGestureHint("⏪ -10s")
                } else if (tapX > screenWidth * 2f / 3f) {
                    jumpBy(+10)
                    showGestureHint("⏩ +10s")
                } else {
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float): Boolean {
                val e1nn = e1 ?: return false
                val screenWidth = binding.playerView.width.toFloat()
                val dx = abs(distX)
                val dy = abs(distY)

                if (dx > dy) {
                    val exo = player ?: return false
                    val dur = exo.duration
                    if (dur == C.TIME_UNSET || dur <= 0L) return false
                    val seekDeltaMs = (-distX / screenWidth * dur * 0.3f).toLong()
                    val newPos = (exo.currentPosition + seekDeltaMs).coerceIn(0L, dur)
                    exo.seekTo(newPos)
                    showGestureHint(FormatUtils.formatDuration(newPos))
                } else {
                    val swipeZone = e1nn.x / screenWidth
                    if (swipeZone < 0.5f) {
                        val delta = -distY / binding.playerView.height
                        currentBrightness = (currentBrightness + delta * 0.5f).coerceIn(0.01f, 1f)
                        val lp = window.attributes
                        lp.screenBrightness = currentBrightness
                        window.attributes = lp
                        showGestureHint("☀ ${(currentBrightness * 100).toInt()}%")
                    } else {
                        val delta = -distY / binding.playerView.height
                        currentVolume = (currentVolume + delta * 0.5f).coerceIn(0f, 1f)
                        player?.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
                        working = working.copy(volumeLevel = currentVolume)
                        binding.seekVolume.progress = (currentVolume * 100).toInt()
                        binding.tvVolumeValue.text = "${(currentVolume * 100).toInt()}%"
                        showGestureHint("🔊 ${(currentVolume * 100).toInt()}%")
                    }
                }
                return true
            }
        })

        binding.playerView.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            if (scaleGestureDetector?.isInProgress == false) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }
    }

    private fun showGestureHint(text: String) {
        binding.gestureHintText.text = text
        binding.gestureHintOverlay.visibility = View.VISIBLE
        binding.gestureHintOverlay.alpha = 1f
        handler.removeCallbacks(hideGestureHintRunnable)
        handler.postDelayed(hideGestureHintRunnable, 800L)
    }

    private val hideGestureHintRunnable = Runnable {
        binding.gestureHintOverlay.animate()
            .alpha(0f).setDuration(300L)
            .withEndAction { binding.gestureHintOverlay.visibility = View.GONE }
            .start()
    }

    // ── Duration helpers ──────────────────────────────────────────────────────

    private fun liveDurationMs(): Long {
        val d = player?.duration ?: C.TIME_UNSET
        return if (d != C.TIME_UNSET && d > 0L) d else working.durationMs.takeIf { it > 0L } ?: 1L
    }

    private fun metaDurationMs(): Long {
        val contentDur = player?.contentDuration ?: C.TIME_UNSET
        if (contentDur != C.TIME_UNSET && contentDur > 0L) return contentDur
        return working.durationMs.takeIf { it > 0L } ?: 1L
    }

    // ── Timeline ──────────────────────────────────────────────────────────────

    private fun tickTimeline() {
        val exo = player ?: return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val pos = exo.currentPosition
        
        // Manual Trim End Check
        if (working.trimEndMs > 0L && pos >= working.trimEndMs) {
            handleTrimEndReached()
            return
        }

        val progress = ((pos * 1000L) / dur).toInt().coerceIn(0, 1000)
        if (!userScrubbingPlayback) binding.seekPlayback.progress = progress
        updateTimeLabel(pos, dur)
        checkChapterTransitions(pos)
        updatePlayPauseIcon()
    }

    private fun handleTrimEndReached() {
        val exo = player ?: return
        if (binding.switchLoop.isChecked) {
            exo.seekTo(working.trimStartMs.coerceAtLeast(0L))
            exo.play()
        } else {
            exo.pause()
            if (binding.switchAutoNext.isChecked) {
                playAdjacent(1)
            }
        }
    }

    private fun updateTimeLabel(absPos: Long, dur: Long) {
        val label = "${FormatUtils.formatDuration(absPos)} / ${FormatUtils.formatDuration(dur)}"
        binding.timeLabel.text      = label
        binding.tvPlaybackTime.text = label
    }

    private fun checkChapterTransitions(posMs: Long) {
        // Highlight nearest chapter in timeline — future: show chapter name
    }

    private fun updatePlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (player?.isPlaying == true) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateServiceNotification() {
        val state = when {
            player?.isPlaying == true -> "Playing"
            player?.playbackState == Player.STATE_BUFFERING -> "Buffering…"
            else -> "Paused"
        }
        playbackService?.updateNotification(working.title, state)
    }

    // ── UI binding ────────────────────────────────────────────────────────────

    private fun bindUiFromWorking() {
        try {
            isBindingUi = true
            binding.toolbar.title   = working.title
            binding.videoTitle.text = working.title
            val semis = working.pitchSemitones.coerceIn(-6, 6)
            binding.seekPitch.progress    = semis + 6
            binding.pitchLabel.text       = pitchLabel(semis)
            binding.seekSpeed.progress    = speedToProgress(currentSpeed)
            binding.tvSpeedValue.text     = speedLabel(currentSpeed)
            binding.seekVolume.progress   = (currentVolume * 100).toInt()
            binding.tvVolumeValue.text    = "${(currentVolume * 100).toInt()}%"
            binding.switchAutoNext.isChecked = working.autoPlayNext
            binding.switchLoop.isChecked     = working.loopPlayback
            binding.btnFavorite.text = getString(
                if (working.favorite) R.string.favorite_off else R.string.favorite_on
            )
            val idx = EnhancementMode.entries.indexOf(working.enhancement).coerceAtLeast(0)
            binding.spinnerEnhancement.setSelection(idx)
            bindTrimSeekers()
            updateTimeLabel(working.positionMs.coerceAtLeast(0L), metaDurationMs())
        } finally {
            isBindingUi = false
        }
    }

    private fun bindTrimSeekers() {
        try {
            val full = max(metaDurationMs(), 1L)
            binding.seekTrimStart.progress =
                ((working.trimStartMs * 1000L) / full).toInt().coerceIn(0, 1000)
            binding.seekTrimEnd.progress =
                if (working.trimEndMs <= 0L) 1000
                else ((working.trimEndMs * 1000L) / full).toInt().coerceIn(0, 1000)
            binding.tvTrimStart.text = "Start: ${FormatUtils.formatDuration(working.trimStartMs)}"
            val endMs = if (working.trimEndMs <= 0L) full else working.trimEndMs
            binding.tvTrimEnd.text = "End: ${FormatUtils.formatDuration(endMs)}"
        } catch (_: Exception) {}
    }

    private fun setupEnhancementSpinner() {
        val labels = EnhancementMode.entries.map { it.displayName }
        val ad = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(pos: Int, convert: View?, parent: android.view.ViewGroup) =
                super.getView(pos, convert, parent).also {
                    (it as? android.widget.TextView)
                        ?.setTextColor(resources.getColor(R.color.crow_on_bg, theme))
                }
            override fun getDropDownView(pos: Int, convert: View?, parent: android.view.ViewGroup) =
                super.getDropDownView(pos, convert, parent).also {
                    (it as? android.widget.TextView)?.apply {
                        setTextColor(resources.getColor(R.color.crow_on_bg, theme))
                        setBackgroundColor(resources.getColor(R.color.crow_surface_elevated, theme))
                        setPadding(32, 24, 32, 24)
                    }
                }
        }
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerEnhancement.adapter = ad
        binding.spinnerEnhancement.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isBindingUi) return
                val mode = EnhancementMode.entries.getOrNull(position) ?: return
                working = working.copy(enhancement = mode)
                applyEnhancementMatrix()
                persistPrefs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Controls setup ────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            when {
                exo.playbackState == Player.STATE_ENDED -> {
                    exo.seekTo(working.trimStartMs.coerceAtLeast(0L))
                    exo.play()
                }
                exo.isPlaying -> exo.pause()
                else -> exo.play()
            }
        }

        binding.btnStop.setOnClickListener {
            player?.pause()
            val trimStart = working.trimStartMs.coerceAtLeast(0L)
            player?.seekTo(trimStart)
            binding.seekPlayback.progress = 0
            updateTimeLabel(trimStart, liveDurationMs())
            persistProgress(); tickTimeline()
        }

        binding.btnRewind.setOnClickListener  { jumpBy(-SEEK_JUMP_SECONDS) }
        binding.btnForward.setOnClickListener { jumpBy(SEEK_JUMP_SECONDS)  }
        binding.btnPrev.setOnClickListener    { playAdjacent(-1) }
        binding.btnNext.setOnClickListener    { playAdjacent(1) }
        binding.btnPip.setOnClickListener { enterPiP() }

        binding.btnProfiles.setOnClickListener {
            startActivity(Intent(this, PlaybackProfilesActivity::class.java)
                .putExtra(PlaybackProfilesActivity.EXTRA_VIDEO_ID, working.id))
        }

        binding.btnAddChapter.setOnClickListener {
            val pos = player?.currentPosition ?: 0L
            showAddChapterDialog(pos)
        }

        binding.seekPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isBindingUi) return
                val exo = player ?: return
                val dur = exo.duration
                if (dur == C.TIME_UNSET || dur <= 0L) return
                val seekTo = (dur * progress / 1000L)
                exo.seekTo(seekTo)
                updateTimeLabel(seekTo, dur)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userScrubbingPlayback = true }
            override fun onStopTrackingTouch(sb: SeekBar?)  {
                userScrubbingPlayback = false; persistProgress()
            }
        })

        binding.seekPitch.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, fromUser -> if (fromUser) applyPitchStep(p) },
            onStop   = { persistPrefs() }
        ))
        binding.btnPitchDown.setOnClickListener {
            val np = (binding.seekPitch.progress - 1).coerceAtLeast(0)
            binding.seekPitch.progress = np; applyPitchStep(np); persistPrefs()
        }
        binding.btnPitchUp.setOnClickListener {
            val np = (binding.seekPitch.progress + 1).coerceAtMost(12)
            binding.seekPitch.progress = np; applyPitchStep(np); persistPrefs()
        }
        binding.btnPitchReset.setOnClickListener {
            binding.seekPitch.progress = 6; applyPitchStep(6); persistPrefs()
        }

        binding.seekSpeed.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, fromUser -> if (fromUser) applySpeedStep(p) },
            onStop   = { persistPrefs() }
        ))
        binding.btnSpeedDown.setOnClickListener {
            val np = (binding.seekSpeed.progress - 1).coerceAtLeast(0)
            binding.seekSpeed.progress = np; applySpeedStep(np); persistPrefs()
        }
        binding.btnSpeedUp.setOnClickListener {
            val np = (binding.seekSpeed.progress + 1).coerceAtMost(30)
            binding.seekSpeed.progress = np; applySpeedStep(np); persistPrefs()
        }
        binding.btnSpeedReset.setOnClickListener {
            val np = speedToProgress(1.0f)
            binding.seekSpeed.progress = np; applySpeedStep(np); persistPrefs()
        }

        binding.seekVolume.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, fromUser -> if (fromUser) applyVolumeStep(p) },
            onStop   = { persistPrefs() }
        ))
        binding.btnVolumeDown.setOnClickListener {
            val np = ((currentVolume * 100).toInt() - 5).coerceIn(0, 100)
            binding.seekVolume.progress = np; applyVolumeStep(np); persistPrefs()
        }
        binding.btnVolumeUp.setOnClickListener {
            val np = ((currentVolume * 100).toInt() + 5).coerceIn(0, 100)
            binding.seekVolume.progress = np; applyVolumeStep(np); persistPrefs()
        }
        binding.btnVolumeReset.setOnClickListener {
            binding.seekVolume.progress = 100; applyVolumeStep(100); persistPrefs()
        }

        binding.seekTrimStart.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, _ ->
                val ms = p * metaDurationMs() / 1000L
                binding.tvTrimStart.text = "Start: ${FormatUtils.formatDuration(ms)}"
            },
            onStop = { 
                if (!isBindingUi) { 
                    readTrimFromSeekBars()
                    persistPrefs()
                    // Don't re-attach media, just sync the current position if it's now before the new start
                    val exo = player ?: return@seekBarListener
                    if (exo.currentPosition < working.trimStartMs) {
                        exo.seekTo(working.trimStartMs)
                    }
                } 
            }
        ))
        binding.seekTrimEnd.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, _ ->
                val ms = p * metaDurationMs() / 1000L
                binding.tvTrimEnd.text = "End: ${FormatUtils.formatDuration(ms)}"
            },
            onStop = { 
                if (!isBindingUi) { 
                    readTrimFromSeekBars()
                    persistPrefs()
                    // Don't re-attach media, tick will handle the end reach logic
                    tickTimeline()
                } 
            }
        ))

        binding.btnTrimStartMinus.setOnClickListener {
            val full = metaDurationMs()
            val cur = binding.seekTrimStart.progress * full / 1000L
            val newMs = (cur - 10_000L).coerceAtLeast(0L)
            binding.seekTrimStart.progress = (newMs * 1000L / full).toInt()
            binding.tvTrimStart.text = "Start: ${FormatUtils.formatDuration(newMs)}"
            readTrimFromSeekBars(); persistPrefs()
            if ((player?.currentPosition ?: 0L) < newMs) player?.seekTo(newMs)
        }
        binding.btnTrimStartPlus.setOnClickListener {
            val full = metaDurationMs()
            val cur = binding.seekTrimStart.progress * full / 1000L
            val newMs = (cur + 10_000L).coerceAtMost(full - 500L)
            binding.seekTrimStart.progress = (newMs * 1000L / full).toInt()
            binding.tvTrimStart.text = "Start: ${FormatUtils.formatDuration(newMs)}"
            readTrimFromSeekBars(); persistPrefs()
            if ((player?.currentPosition ?: 0L) < newMs) player?.seekTo(newMs)
        }
        binding.btnTrimEndMinus.setOnClickListener {
            val full = metaDurationMs()
            val cur = binding.seekTrimEnd.progress * full / 1000L
            val newMs = (cur - 10_000L).coerceAtLeast(working.trimStartMs + 500L)
            binding.seekTrimEnd.progress = (newMs * 1000L / full).toInt()
            binding.tvTrimEnd.text = "End: ${FormatUtils.formatDuration(newMs)}"
            readTrimFromSeekBars(); persistPrefs()
            tickTimeline()
        }
        binding.btnTrimEndPlus.setOnClickListener {
            val full = metaDurationMs()
            val cur = binding.seekTrimEnd.progress * full / 1000L
            val newMs = (cur + 10_000L).coerceAtMost(full)
            binding.seekTrimEnd.progress = (newMs * 1000L / full).toInt()
            binding.tvTrimEnd.text = "End: ${FormatUtils.formatDuration(newMs)}"
            readTrimFromSeekBars(); persistPrefs()
            tickTimeline()
        }
        binding.btnTrimReset.setOnClickListener {
            working = working.copy(trimStartMs = 0L, trimEndMs = 0L)
            isBindingUi = true; bindTrimSeekers(); isBindingUi = false
            binding.tvTrimStart.text = "Start: ${FormatUtils.formatDuration(0L)}"
            binding.tvTrimEnd.text = "End: ${FormatUtils.formatDuration(metaDurationMs())}"
            persistPrefs()
            tickTimeline()
        }

        binding.switchLoop.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            working = working.copy(loopPlayback = checked)
            player?.repeatMode = if (checked) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            persistPrefs()
        }
        binding.switchAutoNext.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            working = working.copy(autoPlayNext = checked); persistPrefs()
        }

        binding.btnFavorite.setOnClickListener {
            val next = !working.favorite
            working = working.copy(favorite = next)
            repo.setFavorite(working.id, next)
            binding.btnFavorite.text = getString(
                if (next) R.string.favorite_off else R.string.favorite_on
            )
        }

        binding.btnSavePrefs.setOnClickListener {
            readTrimFromSeekBars()
            working = working.copy(
                pitchSemitones = (binding.seekPitch.progress - 6).coerceIn(-6, 6),
                autoPlayNext   = binding.switchAutoNext.isChecked,
                loopPlayback   = binding.switchLoop.isChecked,
                enhancement    = EnhancementMode.entries[binding.spinnerEnhancement.selectedItemPosition],
                playbackSpeed  = currentSpeed,
                volumeLevel    = currentVolume,
            )
            persistPrefs()
            applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
            player?.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
            player?.repeatMode = if (working.loopPlayback) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            applyEnhancementMatrix()
            Toast.makeText(this, "Saved for this video", Toast.LENGTH_SHORT).show()
        }

        refreshNavButtons()
    }

    // ── Chapter dialog ────────────────────────────────────────────────────────

    private fun showAddChapterDialog(posMs: Long) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_chapter, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tvTimestamp).text =
            "at ${FormatUtils.formatDuration(posMs)}"
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etChapterName)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.widget.ImageButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<android.widget.ImageButton>(R.id.btnAdd).setOnClickListener {
            val label = etName.text.toString().trim().ifBlank { "Chapter" }
            repo.addChapter(working.id, posMs, label)
            chapters = repo.listChapters(working.id)
            Toast.makeText(this, "Chapter added: $label", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    // ── Apply helpers ─────────────────────────────────────────────────────────

    private fun applyPitchStep(progress: Int) {
        val semis = (progress - 6).coerceIn(-6, 6)
        binding.pitchLabel.text = pitchLabel(semis)
        working = working.copy(pitchSemitones = semis)
        applyPitchAndSpeed(semis, currentSpeed)
    }

    private fun applySpeedStep(progress: Int) {
        currentSpeed = progressToSpeed(progress)
        binding.tvSpeedValue.text = speedLabel(currentSpeed)
        working = working.copy(playbackSpeed = currentSpeed)
        applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
    }

    private fun applyVolumeStep(progress: Int) {
        currentVolume = progress / 100f
        binding.tvVolumeValue.text = "$progress%"
        working = working.copy(volumeLevel = currentVolume)
        player?.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun persistPrefs() {
        if (working.id <= 0L) return
        repo.savePreferences(working)
    }

    private fun persistProgress() {
        val exo = player ?: return
        if (working.id <= 0L) return
        repo.savePlaybackPosition(working.id, exo.currentPosition.coerceAtLeast(0L))
    }

    // ── Seek & navigation ─────────────────────────────────────────────────────

    private fun jumpBy(deltaSec: Int) {
        val exo = player ?: return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val start = working.trimStartMs.coerceAtLeast(0L)
        val end = if (working.trimEndMs > 0L) working.trimEndMs else dur
        val newPos = (exo.currentPosition + deltaSec * 1000L).coerceIn(start, end)
        exo.seekTo(newPos)
        persistProgress(); tickTimeline()
    }

    private fun playAdjacent(delta: Int) {
        persistProgress()
        val next = playlistIndex + delta
        if (next < 0 || next >= playlistIds.size) {
            Toast.makeText(this, "No adjacent video", Toast.LENGTH_SHORT).show(); return
        }
        playlistIndex = next
        val fresh = repo.getById(playlistIds[playlistIndex]) ?: run {
            Toast.makeText(this, "Missing video", Toast.LENGTH_SHORT).show(); return
        }
        // Zero out saved position so seekbar, time label, and ExoPlayer all start from 0
        working = fresh.copy(positionMs = 0L)
        currentSpeed  = working.playbackSpeed.coerceIn(0.5f, 2.0f)
        currentVolume = working.volumeLevel.coerceIn(0f, 1f)
        currentZoom   = working.zoomLevel.coerceIn(1f, 3f)
        chapters      = repo.listChapters(working.id)
        binding.seekPlayback.progress = 0
        bindUiFromWorking()
        attachCurrentMedia(play = true)
        refreshNavButtons()
    }

    private fun refreshNavButtons() {
        binding.btnPrev.isEnabled = playlistIndex > 0
        binding.btnNext.isEnabled = playlistIndex < playlistIds.lastIndex
    }

    private fun readTrimFromSeekBars() {
        val full  = max(metaDurationMs(), 1L)
        val start = binding.seekTrimStart.progress * full / 1000L
        var end   = binding.seekTrimEnd.progress   * full / 1000L
        if (end <= start + 500L) end = min(start + 500L, full)
        working = working.copy(
            trimStartMs = start,
            trimEndMs   = if (end >= full - 250L) 0L else end,
        )
    }

    /**
     * Expands playerSurface + playerView to fill the entire window and hides all
     * chrome (toolbar, controls panel).  Safe to call from onConfigurationChanged.
     */
    private fun enforceFullscreenLayout() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Hide system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        // Hide toolbar + controls so the video can fill the screen
        supportActionBar?.hide()
        binding.toolbar.visibility = View.GONE

        // Optionally hide your controls/scroll panel if you have one
        // binding.controlsPanel.visibility = View.GONE  // uncomment if you have this id

        // playerSurface wraps playerView — make both fill the parent
        binding.playerSurface.layoutParams = binding.playerSurface.layoutParams.also {
            it.width  = ViewGroup.LayoutParams.MATCH_PARENT
            it.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.playerView.layoutParams = binding.playerView.layoutParams.also {
            it.width  = ViewGroup.LayoutParams.MATCH_PARENT
            it.height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        binding.playerSurface.requestLayout()
        binding.playerView.requestLayout()
    }

    /**
     * Restores portrait layout with the fixed-height video surface and visible chrome.
     */
    private fun exitFullscreenLayout() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // Restore system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        // Restore toolbar + controls
        supportActionBar?.show()
        binding.toolbar.visibility = View.VISIBLE

        // Optionally restore your controls/scroll panel
        // binding.controlsPanel.visibility = View.VISIBLE  // uncomment if you have this id

        // Restore playerSurface to its fixed portrait height
        val portraitHeightPx = (220 * resources.displayMetrics.density).toInt()
        binding.playerSurface.layoutParams = binding.playerSurface.layoutParams.also {
            it.width  = ViewGroup.LayoutParams.MATCH_PARENT
            it.height = portraitHeightPx
        }
        // playerView fills playerSurface
        binding.playerView.layoutParams = binding.playerView.layoutParams.also {
            it.width  = ViewGroup.LayoutParams.MATCH_PARENT
            it.height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        binding.playerSurface.requestLayout()
        binding.playerView.requestLayout()
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val rational = Rational(16, 9)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .build()
        enterPictureInPictureMode(params)
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun showAndFinish(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); finish()
    }

    private fun seekBarListener(
        onChange: (Int, Boolean) -> Unit = { _, _ -> },
        onStop:   () -> Unit             = {},
    ) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = onChange(p, fromUser)
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) = onStop()
    }

    private fun progressToSpeed(p: Int)    = (0.5f + p * 0.05f).coerceIn(0.5f, 2.0f)
    private fun speedToProgress(s: Float)  = ((s - 0.5f) / 0.05f).toInt().coerceIn(0, 30)
    private fun speedLabel(s: Float)       = "${"%.2f".format(s).trimEnd('0').trimEnd('.')}x"
    private fun pitchLabel(semis: Int)     = "${if (semis > 0) "+$semis" else "$semis"} st"

    companion object {
        private const val SEEK_JUMP_SECONDS   = 10
        const val EXTRA_VIDEO_ID              = "video_id"
        const val EXTRA_PLAYLIST_IDS          = "playlist_ids"
        const val EXTRA_PLAYLIST_INDEX        = "playlist_index"
    }
}