package com.example.vision

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VisionViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = ShotEngine()
    private val detector = TFLiteDetector(application)
    private val poseEstimator = PoseEstimator(application)
    private val tacticalEngine = TacticalEngine()
    private val fastBallTracker = FastBallTracker()
    val savedVideoManager = SavedVideoManager(application)

    // High-speed 30 FPS background inference workers
    private val yoloExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val poseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val isYoloBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isPoseBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var latestMultiDet: TacticalDetectionFrame? = null
    @Volatile private var latestSkeleton: PoseSkeleton? = null
    @Volatile private var latestEffectiveBall: Det? = null
    @Volatile private var lastBallSeenTime: Long = 0L
    @Volatile private var lastBallNearHandTime: Long = 0L

    private val _uiState = MutableStateFlow(
        VisionState(
            modelState = if (detector.isModelLoaded) ModelState.LOADED else ModelState.ERROR,
            calibrationStep = CalibrationStep.POSITION_PHONE,
            lockedHoop = LockedHoop(nx = 0.5f, ny = 0.28f, isLocked = false),
            inputMode = InputMode.LIVE_CAMERA,
            savedVideos = savedVideoManager.getAllSaved()
        )
    )
    val uiState: StateFlow<VisionState> = _uiState.asStateFlow()

    private val frameTimestamps = ArrayDeque<Long>()
    private var isSimulating = false
    private var sessionStartTime = System.currentTimeMillis()
    private var recordingJob: Job? = null
    private var videoAnalysisJob: Job? = null
    private var tacticalSimJob: Job? = null
    private var lastDribbleHand: Int? = null // -1: Left, 1: Right
    private var lastCrossoverTime = 0L

    init {
        // Load persisted videos on startup
        refreshSavedVideos()

        // Periodic state refresh & session timer
        viewModelScope.launch {
            while (true) {
                delay(100)
                updateSnapshot()
            }
        }
    }

    fun refreshSavedVideos() {
        val list = savedVideoManager.getAllSaved()
        _uiState.update { it.copy(savedVideos = list) }
    }

    fun setSavedVideosDialogVisible(visible: Boolean) {
        if (visible) refreshSavedVideos()
        _uiState.update { it.copy(showSavedVideosDialog = visible) }
    }

    fun setAnalysisType(type: AnalysisType) {
        _uiState.update { it.copy(activeAnalysisType = type) }
    }

    fun setAnalysisSpeed(speed: AnalysisSpeed) {
        _uiState.update { it.copy(activeAnalysisSpeed = speed) }
    }

    fun openSavedVideo(saved: SavedVideoAnalysis) {
        val file = java.io.File(saved.videoFilePath)
        if (!file.exists()) {
            android.util.Log.e("VisionViewModel", "Saved video file does not exist: ${saved.videoFilePath}")
            return
        }
        val uri = Uri.fromFile(file)
        val report = if (saved.hasTacticalReport) savedVideoManager.loadTacticalReport(saved.id) else null
        val timeline = savedVideoManager.loadTimeline(saved.id)

        if (timeline.isNotEmpty()) {
            val detectedHoop = timeline.firstOrNull { it.hoop != null }?.hoop
            val lockedHoop = detectedHoop?.let {
                LockedHoop(nx = it.nx, ny = it.ny, nw = it.nw, nh = it.nh, isLocked = true)
            }

            _uiState.update {
                it.copy(
                    selectedVideoUri = uri,
                    videoTimeline = timeline,
                    inputMode = InputMode.VIDEO_FILE,
                    isVideoAnalyzing = false,
                    isVideoLoading = false,
                    isVideoAnalysisInBackground = false,
                    activeAnalysisType = saved.type,
                    tacticalReport = report,
                    showSavedVideosDialog = false,
                    showRecordedVideoBanner = false,
                    attempts = saved.attempts,
                    makes = saved.makes,
                    accuracy = saved.accuracy,
                    lockedHoop = lockedHoop ?: it.lockedHoop,
                    calibrationStep = CalibrationStep.COMPLETED,
                    isTacticalMode = saved.type == AnalysisType.TACTICAL_MATCH
                )
            }
        } else {
            // Legacy saved video without cached timeline: re-run analysis to compute and cache timeline
            _uiState.update { it.copy(showSavedVideosDialog = false) }
            selectVideoUri(
                uri = uri,
                type = saved.type,
                speed = AnalysisSpeed.TURBO,
                runInBackground = false
            )
        }
    }

    fun deleteSavedVideo(id: String) {
        savedVideoManager.deleteSaved(id)
        refreshSavedVideos()
    }

    private fun updateSnapshot() {
        val snapshot = engine.snapshot()
        val now = System.currentTimeMillis()
        while (frameTimestamps.isNotEmpty() && now - frameTimestamps.first() > 1000) {
            frameTimestamps.removeFirst()
        }
        val calculatedFps = frameTimestamps.size
        val elapsedSec = (now - sessionStartTime) / 1000

        _uiState.update { current ->
            // If analyzing video, do not overwrite state with live camera snapshot!
            if (current.isVideoAnalyzing) {
                current.copy(
                    fps = calculatedFps,
                    sessionDurationSec = elapsedSec
                )
            } else {
                snapshot.copy(
                    showSkeleton = current.showSkeleton,
                    showHoop = current.showHoop,
                    showBall = current.showBall,
                    showFps = current.showFps,
                    courtCalibration = current.courtCalibration,
                    showCourtPointSelector = current.showCourtPointSelector,
                    fps = calculatedFps,
                    modelState = if (detector.isModelLoaded) ModelState.LOADED else ModelState.ERROR,
                    cameraActive = current.cameraActive,
                    calibrationStep = current.calibrationStep,
                    lockedHoop = current.lockedHoop,
                    ballCalibrated = current.ballCalibrated,
                    ballCalibrationProgress = current.ballCalibrationProgress,
                    sessionDurationSec = elapsedSec,
                    isSimulating = isSimulating,
                    inputMode = current.inputMode,
                    selectedVideoUri = current.selectedVideoUri,
                    isRecordingLive = current.isRecordingLive,
                    recordingDurationSec = current.recordingDurationSec,
                    lastRecordedVideoUri = current.lastRecordedVideoUri,
                    showRecordedVideoBanner = current.showRecordedVideoBanner,
                    isVideoLoading = current.isVideoLoading,
                    isVideoAnalyzing = current.isVideoAnalyzing,
                    isVideoAnalysisInBackground = current.isVideoAnalysisInBackground,
                    videoAnalysisProgress = current.videoAnalysisProgress,
                    videoAnalyzedFrames = current.videoAnalyzedFrames,
                    videoTotalFrames = current.videoTotalFrames,
                    videoAnalysisStatus = current.videoAnalysisStatus,
                    videoTimeline = current.videoTimeline,
                    videoAnalysisCompletedNotice = current.videoAnalysisCompletedNotice,
                    hoopVisual = current.hoopVisual,
                    tacticalReport = current.tacticalReport,
                    tacticalTimeline = current.tacticalTimeline,
                    showTacticalReportDialog = current.showTacticalReportDialog,
                    currentTacticalAnalysis = current.currentTacticalAnalysis,
                    isTacticalMode = current.isTacticalMode,
                    isSimulatingTactical = current.isSimulatingTactical,
                    activeAnalysisType = current.activeAnalysisType,
                    activeAnalysisSpeed = current.activeAnalysisSpeed,
                    skeleton = if (current.isDribbleMode || current.isReactionPointsMode || current.calibrationStep == CalibrationStep.CALIBRATE_SKELETON || current.showSkeleton) {
                        latestSkeleton ?: snapshot.skeleton
                    } else {
                        snapshot.skeleton
                    },
                    isDribbleMode = current.isDribbleMode,
                    useFrontCamera = if (current.isDribbleMode || current.isReactionPointsMode) true else current.useFrontCamera,
                    frameWidth = current.frameWidth,
                    frameHeight = current.frameHeight,
                    skeletonCalibrationProgress = current.skeletonCalibrationProgress,
                    skeletonCalibrated = current.skeletonCalibrated,
                    skeletonCalibrationFeedback = current.skeletonCalibrationFeedback,
                    ballCalibrationFeedback = current.ballCalibrationFeedback,
                    calibratedBallColor = current.calibratedBallColor,
                    dribbleScore = current.dribbleScore,
                    dribbleCrossovers = current.dribbleCrossovers,
                    dribbleStreak = current.dribbleStreak,
                    dribblePopups = current.dribblePopups,
                    isReactionPointsMode = current.isReactionPointsMode,
                    reactionScore = current.reactionScore,
                    reactionTimerRemainingSec = current.reactionTimerRemainingSec,
                    isReactionTimerRunning = current.isReactionTimerRunning,
                    isReactionSessionFinished = current.isReactionSessionFinished,
                    activeReactionPoint = current.activeReactionPoint,
                    reactionPopups = current.reactionPopups,
                    savedVideos = current.savedVideos,
                    showSavedVideosDialog = current.showSavedVideosDialog
                )
            }
        }
    }

    fun toggleHoopVisual() {
        _uiState.update {
            it.copy(
                hoopVisual = if (it.hoopVisual == HoopVisual.REALISTIC_FRONT) HoopVisual.BOARD_ONLY else HoopVisual.REALISTIC_FRONT
            )
        }
    }

    fun toggleShowSkeleton() {
        _uiState.update { it.copy(showSkeleton = !it.showSkeleton) }
    }

    fun setShowSkeleton(show: Boolean) {
        _uiState.update { it.copy(showSkeleton = show) }
    }

    fun toggleShowHoop() {
        _uiState.update { it.copy(showHoop = !it.showHoop) }
    }

    fun setShowHoop(show: Boolean) {
        _uiState.update { it.copy(showHoop = show) }
    }

    fun toggleShowBall() {
        _uiState.update { it.copy(showBall = !it.showBall) }
    }

    fun setShowBall(show: Boolean) {
        _uiState.update { it.copy(showBall = show) }
    }

    fun setHoopPerspective(perspective: HoopPerspective) {
        val current = _uiState.value.lockedHoop ?: LockedHoop()
        val updated = current.copy(perspective = perspective)
        _uiState.update { it.copy(lockedHoop = updated) }
        engine.lockedHoop = updated
    }

    fun cycleHoopPerspective() {
        val current = _uiState.value.lockedHoop ?: LockedHoop()
        val nextPerspective = when (current.perspective) {
            HoopPerspective.AUTO -> HoopPerspective.FRONTAL
            HoopPerspective.FRONTAL -> HoopPerspective.SIDE_LEFT
            HoopPerspective.SIDE_LEFT -> HoopPerspective.SIDE_RIGHT
            HoopPerspective.SIDE_RIGHT -> HoopPerspective.AUTO
        }
        val updated = current.copy(perspective = nextPerspective)
        _uiState.update { it.copy(lockedHoop = updated) }
        engine.lockedHoop = updated
    }

    fun setCameraActive(active: Boolean) {
        _uiState.update { it.copy(cameraActive = active) }
    }

    fun setCalibrationStep(step: CalibrationStep) {
        _uiState.update { 
            it.copy(
                calibrationStep = step,
                useFrontCamera = if (it.isDribbleMode || it.isReactionPointsMode) true else it.useFrontCamera
            ) 
        }
        if (step == CalibrationStep.COMPLETED && _uiState.value.isReactionPointsMode) {
            startReactionSessionNow()
        }
        if (step == CalibrationStep.COMPLETED && _uiState.value.isDribbleMode) {
            startDribbleSessionNow()
        }
    }

    fun onManualHoopSelected(nx: Float, ny: Float) {
        val updated = (_uiState.value.lockedHoop ?: LockedHoop()).copy(
            nx = nx,
            ny = ny,
            isLocked = false
        )
        _uiState.update { it.copy(lockedHoop = updated) }
    }

    fun toggleShowFps() {
        _uiState.update { it.copy(showFps = !it.showFps) }
    }

    fun setShowFps(show: Boolean) {
        _uiState.update { it.copy(showFps = show) }
    }

    fun setCourtPoint(id: String, nx: Float, ny: Float) {
        _uiState.update {
            val updated = it.courtCalibration.updatePoint(id, nx, ny)
            it.copy(courtCalibration = updated)
        }
        engine.courtCalibration = _uiState.value.courtCalibration
    }

    fun applyCourtPreset(preset: CourtPreset) {
        _uiState.update {
            val points = CourtCalibration.createDefaultCourtPoints(preset)
            val adapted = it.lockedHoop?.let { hoop ->
                CourtCalibration.adaptToHoop(points, hoop.nx, hoop.ny)
            } ?: points
            val updated = it.courtCalibration.copy(points = adapted, preset = preset, isCalibrated = true)
            it.copy(courtCalibration = updated)
        }
        engine.courtCalibration = _uiState.value.courtCalibration
    }

    fun resetCourtPoints() {
        _uiState.update {
            val defaultPts = CourtCalibration.createDefaultCourtPoints(CourtPreset.FRONTAL)
            val adapted = it.lockedHoop?.let { hoop ->
                CourtCalibration.adaptToHoop(defaultPts, hoop.nx, hoop.ny)
            } ?: defaultPts
            it.copy(courtCalibration = CourtCalibration(points = adapted, isCalibrated = false, preset = CourtPreset.FRONTAL))
        }
        engine.courtCalibration = _uiState.value.courtCalibration
    }

    fun setShowCourtPointSelector(show: Boolean) {
        _uiState.update { it.copy(showCourtPointSelector = show) }
    }

    fun recalibrateCourt() {
        _uiState.update { it.copy(calibrationStep = CalibrationStep.COURT_ALIGNMENT, showCourtPointSelector = false) }
    }

    fun confirmLockHoop() {
        val current = _uiState.value.lockedHoop ?: LockedHoop(nx = 0.5f, ny = 0.28f)
        val locked = current.copy(isLocked = true)
        val adaptedCourt = CourtCalibration.adaptToHoop(
            _uiState.value.courtCalibration.points,
            locked.nx,
            locked.ny
        )
        _uiState.update {
            it.copy(
                lockedHoop = locked,
                courtCalibration = it.courtCalibration.copy(points = adaptedCourt),
                calibrationStep = if (it.calibrationStep == CalibrationStep.LOCK_HOOP) CalibrationStep.CHECK_BALL else it.calibrationStep
            )
        }
        engine.lockedHoop = locked
        engine.courtCalibration = _uiState.value.courtCalibration
    }

    // Video Mode Controls
    fun cancelVideoAnalysis() {
        videoAnalysisJob?.cancel()
        videoAnalysisJob = null
        _uiState.update {
            it.copy(
                isVideoAnalyzing = false,
                isVideoLoading = false,
                isVideoAnalysisInBackground = false,
                videoAnalysisProgress = 0f,
                videoAnalysisStatus = "",
                inputMode = InputMode.LIVE_CAMERA
            )
        }
    }

    fun setVideoAnalysisInBackground(inBackground: Boolean) {
        _uiState.update { current ->
            current.copy(
                isVideoAnalysisInBackground = inBackground,
                inputMode = if (inBackground) InputMode.LIVE_CAMERA else InputMode.VIDEO_FILE
            )
        }
    }

    fun dismissVideoAnalysisCompletedNotice() {
        _uiState.update { it.copy(videoAnalysisCompletedNotice = null) }
    }

    fun openCompletedVideoFromNotice() {
        _uiState.update {
            it.copy(
                inputMode = InputMode.VIDEO_FILE,
                isVideoAnalysisInBackground = false,
                videoAnalysisCompletedNotice = null
            )
        }
    }

    fun selectVideoUri(
        uri: Uri,
        type: AnalysisType = _uiState.value.activeAnalysisType,
        speed: AnalysisSpeed = _uiState.value.activeAnalysisSpeed,
        runInBackground: Boolean = false,
        enableSkeleton: Boolean = _uiState.value.showSkeleton
    ) {
        videoAnalysisJob?.cancel()
        reset()
        _uiState.update {
            it.copy(
                selectedVideoUri = null,
                isVideoLoading = true,
                isVideoAnalyzing = true,
                isVideoAnalysisInBackground = runInBackground,
                videoAnalysisProgress = 0f,
                videoAnalyzedFrames = 0,
                videoTotalFrames = 0,
                videoAnalysisStatus = "Optimizando archivo de vídeo para ${type.title}...",
                videoTimeline = emptyList(),
                videoAnalysisCompletedNotice = null,
                inputMode = if (runInBackground) InputMode.LIVE_CAMERA else InputMode.VIDEO_FILE,
                showRecordedVideoBanner = false,
                activeAnalysisType = type,
                activeAnalysisSpeed = speed,
                showSkeleton = enableSkeleton,
                isTacticalMode = type == AnalysisType.TACTICAL_MATCH
            )
        }

        videoAnalysisJob = viewModelScope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val localUri = try {
                val cacheFile = java.io.File(app.cacheDir, "cached_input_${System.currentTimeMillis()}.mp4")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(cacheFile)
            } catch (e: Exception) {
                android.util.Log.e("VisionViewModel", "Failed to cache video file: ${e.message}", e)
                uri
            }

            val retriever = MediaMetadataRetriever()
            var firstFrameThumb: Bitmap? = null
            try {
                retriever.setDataSource(app, localUri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 8000L
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rotation = rotationStr?.toIntOrNull() ?: 0

                // Temporal sampling based on chosen AnalysisSpeed
                val stepMs = speed.stepMs
                val totalSteps = maxOf(1, (durationMs / stepMs).toInt())

                _uiState.update {
                    it.copy(
                        isVideoLoading = false,
                        isVideoAnalyzing = true,
                        videoAnalysisProgress = 0f,
                        videoAnalyzedFrames = 0,
                        videoTotalFrames = totalSteps,
                        videoAnalysisStatus = "Iniciando análisis (${speed.label})..."
                    )
                }

                val timeline = ArrayList<VideoFrameAnalysis>(totalSteps)
                engine.reset()
                engine.lockedHoop = null
                tacticalEngine.reset()

                var currentStep = 0
                var t = 0L

                while (isActive && t <= durationMs) {
                    val timeUs = t * 1000L
                    val rawBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        try {
                            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 480, 480)
                        } catch (e: Exception) {
                            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let {
                                Bitmap.createScaledBitmap(it, 480, 480, true)
                            }
                        }
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let {
                            Bitmap.createScaledBitmap(it, 480, 480, true)
                        }
                    }

                    if (rawBitmap != null) {
                        val targetBitmap = if (rotation != 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                            val rotBm = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                            if (rotBm != rawBitmap) rawBitmap.recycle()
                            rotBm
                        } else {
                            rawBitmap
                        }

                        // Capture first frame for thumbnail
                        if (firstFrameThumb == null) {
                            try {
                                firstFrameThumb = Bitmap.createScaledBitmap(targetBitmap, 320, 240, true)
                            } catch (_: Exception) {}
                        }

                        val multiDet = detector.detectAll(targetBitmap)

                        // Selective AI processing according to analysis type and user toggle:
                        val skeleton = if (enableSkeleton && type == AnalysisType.SHOOTING) {
                            poseEstimator.estimate(targetBitmap)
                        } else null

                        // Push to shot engine
                        engine.push(
                            DetectionFrame(
                                ball = multiDet.ball,
                                hoop = multiDet.hoop,
                                player = multiDet.player,
                                skeleton = skeleton
                            )
                        )
                        val snap = engine.snapshot()

                        // Process tactical patterns only if tactical match mode
                        val tacticalAnalysis = if (type == AnalysisType.TACTICAL_MATCH) {
                            tacticalEngine.processFrame(t, targetBitmap, multiDet)
                        } else null

                        timeline.add(
                            VideoFrameAnalysis(
                                timestampMs = t,
                                ball = multiDet.ball,
                                hoop = multiDet.hoop,
                                player = multiDet.player,
                                skeleton = skeleton,
                                attempts = snap.attempts,
                                makes = snap.makes,
                                misses = snap.misses,
                                accuracy = snap.accuracy,
                                currentStreak = snap.currentStreak,
                                lastEvent = snap.lastEvent,
                                lastLocation = snap.lastLocation,
                                releaseAngle = snap.releaseAngle,
                                releaseTimeSec = snap.releaseTimeSec,
                                shots = snap.shots,
                                courtShots = snap.courtShots,
                                tacticalAnalysis = tacticalAnalysis
                            )
                        )

                        targetBitmap.recycle()
                    }

                    currentStep++
                    t += stepMs

                    if (currentStep % 4 == 0 || t > durationMs) {
                        val progress = (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
                        _uiState.update {
                            it.copy(
                                videoAnalysisProgress = progress,
                                videoAnalyzedFrames = currentStep,
                                videoTotalFrames = totalSteps,
                                videoAnalysisStatus = "Analizando fotograma $currentStep de $totalSteps (${(progress * 100).toInt()}%)"
                            )
                        }
                    }
                }

                retriever.release()

                val finalReport = if (type == AnalysisType.TACTICAL_MATCH) {
                    tacticalEngine.generateMatchReport(durationMs)
                } else null

                val snap = engine.snapshot()

                // Save analysis permanently to "Mis Vídeos" repository!
                val savedEntry = savedVideoManager.saveAnalysis(
                    sourceUri = localUri,
                    type = type,
                    durationMs = durationMs,
                    timeline = timeline,
                    report = finalReport,
                    makes = snap.makes,
                    attempts = snap.attempts,
                    firstFrameBitmap = firstFrameThumb
                )
                refreshSavedVideos()

                val summaryText = if (type == AnalysisType.SHOOTING) {
                    "Sesión completada • ${snap.makes}/${snap.attempts} canastas (${snap.accuracy}%) • Guardado en Mis Vídeos"
                } else {
                    "Partido analizado • ${finalReport?.totalPossessions ?: 0} posesiones • ${finalReport?.fastBreakCount ?: 0} contraataques • Guardado en Mis Vídeos"
                }

                val notice = VideoAnalysisCompletedNotice(
                    videoUri = Uri.fromFile(java.io.File(savedEntry.videoFilePath)),
                    totalFrames = timeline.size,
                    makes = snap.makes,
                    attempts = snap.attempts,
                    tacticalPlayCount = finalReport?.keyPlays?.size ?: 0,
                    summaryText = summaryText
                )

                // Trigger System Push Notification
                val app = getApplication<Application>()
                VideoAnalysisNotificationHelper.showAnalysisCompletedNotification(
                    context = app,
                    totalFrames = timeline.size,
                    makes = snap.makes,
                    attempts = snap.attempts,
                    tacticalSummary = if (type == AnalysisType.SHOOTING) {
                        "Tiros: ${snap.makes}/${snap.attempts} (${snap.accuracy}%). Toca para ver el análisis."
                    } else {
                        "Tácticas: ${finalReport?.fastBreakCount ?: 0} contraataques, ${finalReport?.pickAndRollCount ?: 0} PnR. Toca para ver informe."
                    }
                )

                _uiState.update { current ->
                    val wasInBackground = current.isVideoAnalysisInBackground
                    current.copy(
                        isVideoAnalyzing = false,
                        isVideoLoading = false,
                        isVideoAnalysisInBackground = false,
                        inputMode = if (wasInBackground) current.inputMode else InputMode.VIDEO_FILE,
                        selectedVideoUri = Uri.fromFile(java.io.File(savedEntry.videoFilePath)),
                        videoTimeline = timeline,
                        calibrationStep = CalibrationStep.COMPLETED,
                        showRecordedVideoBanner = false,
                        tacticalReport = finalReport,
                        currentTacticalAnalysis = timeline.lastOrNull()?.tacticalAnalysis,
                        videoAnalysisCompletedNotice = notice,
                        isTacticalMode = type == AnalysisType.TACTICAL_MATCH
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("VisionViewModel", "Video pre-analysis error: ${e.message}", e)
                try { retriever.release() } catch (_: Exception) {}
                _uiState.update {
                    it.copy(
                        isVideoAnalyzing = false,
                        isVideoLoading = false,
                        isVideoAnalysisInBackground = false,
                        inputMode = InputMode.VIDEO_FILE,
                        selectedVideoUri = localUri,
                        calibrationStep = CalibrationStep.COMPLETED
                    )
                }
            }
        }
    }

    fun switchToLiveCamera() {
        videoAnalysisJob?.cancel()
        videoAnalysisJob = null
        reset()
        _uiState.update {
            it.copy(
                inputMode = InputMode.LIVE_CAMERA,
                selectedVideoUri = null,
                isVideoAnalyzing = false,
                isVideoLoading = false,
                videoTimeline = emptyList(),
                showRecordedVideoBanner = false
            )
        }
    }

    // Live Recording Controls
    fun toggleLiveRecording() {
        val currentlyRecording = _uiState.value.isRecordingLive
        if (!currentlyRecording) {
            startLiveRecording()
        } else {
            stopLiveRecording()
        }
    }

    private fun startLiveRecording() {
        _uiState.update {
            it.copy(
                isRecordingLive = true,
                recordingDurationSec = 0,
                showRecordedVideoBanner = false
            )
        }
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingDurationSec = it.recordingDurationSec + 1) }
            }
        }
    }

    private fun stopLiveRecording() {
        recordingJob?.cancel()
        recordingJob = null
        _uiState.update { it.copy(isRecordingLive = false) }
    }

    fun onVideoRecorded(uri: Uri) {
        val durationMs = (_uiState.value.recordingDurationSec * 1000L).coerceAtLeast(1000L)
        val makes = _uiState.value.makes
        val attempts = _uiState.value.attempts
        val isTactical = _uiState.value.isTacticalMode
        stopLiveRecording()

        viewModelScope.launch(Dispatchers.IO) {
            var firstFrame: Bitmap? = null
            var actualDuration = durationMs
            try {
                val retriever = android.media.MediaMetadataRetriever()
                val context = getApplication<Application>()
                retriever.setDataSource(context, uri)
                firstFrame = retriever.getFrameAtTime(500000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    val parsed = durStr.toLongOrNull()
                    if (parsed != null && parsed > 0) actualDuration = parsed
                }
                retriever.release()
            } catch (e: Exception) {
                android.util.Log.e("VisionViewModel", "Error extracting thumbnail from recorded video: ${e.message}")
            }

            val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            val savedTitle = if (isTactical) {
                "Partido Grabado (${dateFormat.format(java.util.Date())})"
            } else {
                "Entrenamiento Grabado (${dateFormat.format(java.util.Date())})"
            }

            val saved = savedVideoManager.saveAnalysis(
                sourceUri = uri,
                type = if (isTactical) AnalysisType.TACTICAL_MATCH else AnalysisType.SHOOTING,
                customTitle = savedTitle,
                durationMs = actualDuration,
                timeline = emptyList(),
                report = null,
                makes = makes,
                attempts = attempts,
                firstFrameBitmap = firstFrame
            )

            refreshSavedVideos()

            _uiState.update {
                it.copy(
                    lastRecordedVideoUri = Uri.fromFile(java.io.File(saved.videoFilePath)),
                    showRecordedVideoBanner = true
                )
            }
        }
    }

    fun dismissRecordedVideoBanner() {
        _uiState.update { it.copy(showRecordedVideoBanner = false) }
    }

    fun exitToMainMenu() {
        stopLiveRecording()
        cancelVideoAnalysis()
        reactionTimerJob?.cancel()
        reactionTimerJob = null
        pointTimeoutJob?.cancel()
        pointTimeoutJob = null
        reset()
        _uiState.update {
            it.copy(
                isDribbleMode = false,
                isReactionPointsMode = false,
                isReactionTimerRunning = false,
                isReactionSessionFinished = false,
                reactionScore = 0,
                activeReactionPoint = null,
                reactionPopups = emptyList(),
                useFrontCamera = false,
                calibrationStep = CalibrationStep.POSITION_PHONE,
                lockedHoop = null,
                showRecordedVideoBanner = false,
                inputMode = InputMode.LIVE_CAMERA,
                selectedVideoUri = null
            )
        }
    }

    fun analyzeRecordedVideoNow() {
        val uri = _uiState.value.lastRecordedVideoUri ?: return
        selectVideoUri(uri)
    }

    fun onFrameAnalyzed(bitmap: Bitmap) {
        if (isSimulating || _uiState.value.isSimulatingTactical) return

        if (_uiState.value.frameWidth != bitmap.width || _uiState.value.frameHeight != bitmap.height) {
            _uiState.update { it.copy(frameWidth = bitmap.width, frameHeight = bitmap.height) }
        }

        val now = System.currentTimeMillis()
        frameTimestamps.add(now)

        // 1. Dispatch Asynchronous Deep YOLO detection if worker is free (never blocks camera thread)
        if (isYoloBusy.compareAndSet(false, true)) {
            val copy = try {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                null
            }
            if (copy != null) {
                yoloExecutor.execute {
                    try {
                        val det = detector.detectAll(copy)
                        latestMultiDet = det

                        // If in tactical mode, evaluate player positions and kit colors
                        if (_uiState.value.isTacticalMode) {
                            val tacticalAnalysis = tacticalEngine.processFrame(System.currentTimeMillis(), copy, det)
                            _uiState.update { it.copy(currentTacticalAnalysis = tacticalAnalysis) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("VisionViewModel", "Background YOLO error: ${e.message}")
                    } finally {
                        try { copy.recycle() } catch (_: Exception) {}
                        isYoloBusy.set(false)
                    }
                }
            } else {
                isYoloBusy.set(false)
            }
        }

        // 2. Dispatch Asynchronous Pose Estimation if requested and worker is free
        val needsPose = _uiState.value.showSkeleton ||
            _uiState.value.isDribbleMode ||
            _uiState.value.isReactionPointsMode ||
            _uiState.value.calibrationStep == CalibrationStep.CALIBRATE_SKELETON
        if (needsPose && isPoseBusy.compareAndSet(false, true)) {
            val copy = try {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                null
            }
            if (copy != null) {
                poseExecutor.execute {
                    try {
                        val skeleton = poseEstimator.estimate(copy)
                        latestSkeleton = skeleton
                        if (_uiState.value.isReactionPointsMode) {
                            checkReactionPointHit(skeleton, latestEffectiveBall)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("VisionViewModel", "Background Pose error: ${e.message}")
                    } finally {
                        try { copy.recycle() } catch (_: Exception) {}
                        isPoseBusy.set(false)
                    }
                }
            } else {
                isPoseBusy.set(false)
            }
        }

        // 3. Ultra-Fast Ball & Scoring Cylinder Tracking (Runs at 30 FPS in < 2ms)
        val yoloDet = latestMultiDet
        val locked = _uiState.value.lockedHoop
        val trackedBall = fastBallTracker.processFrame(bitmap, yoloDet?.ball, locked)
        val effectiveBall = trackedBall ?: yoloDet?.ball
        latestEffectiveBall = effectiveBall
        val skeleton = latestSkeleton

        if (effectiveBall != null) {
            lastBallSeenTime = now
            if (skeleton != null && skeleton.landmarks.size >= 17) {
                val lWrist = skeleton.landmarks.getOrNull(15)
                val rWrist = skeleton.landmarks.getOrNull(16)
                val distL: Float = if (lWrist != null && lWrist.visibility > 0.18f) kotlin.math.hypot(effectiveBall.nx - lWrist.x, effectiveBall.ny - lWrist.y) else 1.0f
                val distR: Float = if (rWrist != null && rWrist.visibility > 0.18f) kotlin.math.hypot(effectiveBall.nx - rWrist.x, effectiveBall.ny - rWrist.y) else 1.0f
                if (distL < 0.45f || distR < 0.45f) {
                    lastBallNearHandTime = now
                }
            }
        }

        // Resolve effective hoop (prioritize locked calibrated hoop for maximum stability)
        val effectiveHoop = if (locked != null && locked.isLocked) {
            Det(cx = locked.cx, cy = locked.cy, w = locked.w, h = locked.h, conf = 1.0f)
        } else {
            yoloDet?.hoop
        }

        // 4. Handle Dribble Mode: Track player & ball, detect hand switches / crossovers
        if (_uiState.value.isDribbleMode && _uiState.value.calibrationStep == CalibrationStep.COMPLETED) {
            if (effectiveBall != null && skeleton != null && skeleton.landmarks.size >= 17) {
                val lWrist = skeleton.landmarks.getOrNull(15)
                val rWrist = skeleton.landmarks.getOrNull(16)
                if (lWrist != null && rWrist != null && lWrist.visibility > 0.3f && rWrist.visibility > 0.3f) {
                    val distL = kotlin.math.hypot(effectiveBall.nx - lWrist.x, effectiveBall.ny - lWrist.y)
                    val distR = kotlin.math.hypot(effectiveBall.nx - rWrist.x, effectiveBall.ny - rWrist.y)

                    val currentHand = when {
                        distL < distR && distL < 0.40f -> -1 // Left hand
                        distR < distL && distR < 0.40f -> 1  // Right hand
                        else -> null
                    }

                    if (currentHand != null && lastDribbleHand != null && currentHand != lastDribbleHand) {
                        if (now - lastCrossoverTime > 450L) {
                            lastCrossoverTime = now
                            triggerDribbleCrossover(effectiveBall.nx, effectiveBall.ny)
                        }
                    }
                    if (currentHand != null) {
                        lastDribbleHand = currentHand
                    }
                }
            }
        }

        // 4b. Handle Reaction Points Mode: Verify that BOTH ball and hand touch the target together!
        if (_uiState.value.isReactionPointsMode && _uiState.value.isReactionTimerRunning && !_uiState.value.isReactionSessionFinished) {
            checkReactionPointHit(skeleton, effectiveBall)
        }

        // 5. Automatic Skeleton Calibration Step (in Dribble training mode)
        if (_uiState.value.calibrationStep == CalibrationStep.CALIBRATE_SKELETON) {
            processSkeletonCalibration(skeleton)
        }

        // 6. Real Ball Calibration (HSV & circularity verification for both shooting and dribble modes)
        if (_uiState.value.calibrationStep == CalibrationStep.CHECK_BALL) {
            processRealBallCalibration(bitmap, yoloDet?.ball)
        }

        // 6. If hoop is not locked yet and YOLO finds one, suggest its position
        if (!_uiState.value.isDribbleMode && _uiState.value.lockedHoop?.isLocked != true && yoloDet?.hoop != null) {
            _uiState.update { current ->
                if (current.lockedHoop?.isLocked != true) {
                    current.copy(
                        lockedHoop = LockedHoop(
                            nx = yoloDet.hoop.nx,
                            ny = yoloDet.hoop.ny,
                            nw = yoloDet.hoop.nw,
                            nh = yoloDet.hoop.nh,
                            isLocked = false
                        )
                    )
                } else current
            }
        }

        // 7. Feed to Shot Engine on EVERY 30 FPS frame! (25-30 frames per second of flight data)
        if (!_uiState.value.isDribbleMode) {
            val combined = DetectionFrame(
                ball = effectiveBall,
                hoop = effectiveHoop,
                player = yoloDet?.player,
                skeleton = skeleton
            )
            engine.push(combined)
        }

        // 8. Update UI snapshot immediately with high-framerate positions
        updateSnapshot()
    }

    // --- MÉTODOS DEL MODO BOTE (DRIBBLE COMBO AI & LV3) ---
    private var dribbleTimerJob: Job? = null
    private var dribbleCoolDownJob: Job? = null
    private var comboBannerJob: Job? = null

    fun triggerDribbleCrossover(manualX: Float? = null, manualY: Float? = null) {
        val current = _uiState.value
        if (!current.isDribbleMode) return

        // Si la sesión no ha iniciado el contador, arrancarlo
        if (!current.isDribbleTimerRunning && !current.isDribbleSessionFinished && current.calibrationStep == CalibrationStep.COMPLETED) {
            startDribbleSessionNow()
        }

        val curLevel = current.dribbleLevel
        val pointsToAdd = when (curLevel) {
            1 -> 3
            2 -> 5
            else -> 6 // LV3
        }

        val newStreak = current.dribbleStreak + 1
        val newCrossovers = current.dribbleCrossovers + 1
        val newScore = current.dribbleScore + pointsToAdd

        // Lógica de progreso del medidor y ascenso de nivel (LV1 -> LV2 -> LV3)
        var newProgress = current.dribbleGaugeProgress + 0.28f
        var newLevel = curLevel
        if (newProgress >= 1.0f) {
            if (newLevel < 3) {
                newLevel += 1
                newProgress = if (newLevel == 3) 0.90f else 0.45f
            } else {
                newProgress = 1.0f
            }
        }

        // Títulos de combos dinámicos
        val comboTitles = listOf("SLICK MOVES", "QUICK HANDS", "BETWEEN THE LEGS", "ANKLE BREAKER", "CROSSOVER")
        val chosenBanner = when {
            newLevel == 3 && curLevel < 3 -> "SLICK MOVES"
            newStreak >= 4 && newStreak % 2 == 0 -> "SLICK MOVES"
            newStreak % 3 == 0 -> comboTitles.random()
            newStreak >= 2 -> "CROSSOVER"
            else -> null
        }

        // Generación de nubes de humo estilo dibujo animado en los pies
        val lm = current.skeleton?.landmarks
        val smokeList = mutableListOf<DribbleSmokePuff>()
        val isLeft = (newCrossovers % 2 == 0)
        if (lm != null && lm.size >= 29) {
            val lAnkle = lm.getOrNull(27)
            val rAnkle = lm.getOrNull(28)
            val footX = if (isLeft) lAnkle?.x ?: 0.38f else rAnkle?.x ?: 0.62f
            val footY = (if (isLeft) lAnkle?.y ?: 0.86f else rAnkle?.y ?: 0.86f) + 0.04f
            smokeList.add(DribbleSmokePuff(nx = footX.coerceIn(0.12f, 0.88f), ny = footY.coerceIn(0.55f, 0.95f), isLeftFoot = isLeft))
        } else {
            smokeList.add(DribbleSmokePuff(nx = if (isLeft) 0.38f else 0.62f, ny = 0.88f, isLeftFoot = isLeft))
        }

        val popupX = manualX ?: (current.ball?.nx ?: 0.5f)
        val popupY = manualY ?: (current.ball?.ny ?: 0.55f)

        val newPopup = DribblePopup(
            id = System.currentTimeMillis() + (0..999).random(),
            nx = popupX,
            ny = popupY,
            points = pointsToAdd,
            text = "+$pointsToAdd",
            comboName = chosenBanner
        )

        val updatedPopups = (current.dribblePopups + newPopup).takeLast(6)
        val updatedSmoke = (current.dribbleSmokePuffs + smokeList).takeLast(6)

        _uiState.update {
            it.copy(
                dribbleScore = newScore,
                dribbleCrossovers = newCrossovers,
                dribbleStreak = newStreak,
                dribbleLevel = newLevel,
                dribbleGaugeProgress = newProgress.coerceIn(0.05f, 1.0f),
                dribbleComboBanner = chosenBanner ?: it.dribbleComboBanner,
                dribblePopups = updatedPopups,
                dribbleSmokePuffs = updatedSmoke
            )
        }

        if (chosenBanner != null) {
            comboBannerJob?.cancel()
            comboBannerJob = viewModelScope.launch {
                delay(1500L)
                _uiState.update { it.copy(dribbleComboBanner = null) }
            }
        }
    }

    fun dismissDribblePopup(id: Long) {
        _uiState.update {
            it.copy(dribblePopups = it.dribblePopups.filterNot { p -> p.id == id })
        }
    }

    fun dismissDribbleSmokePuff(id: Long) {
        _uiState.update {
            it.copy(dribbleSmokePuffs = it.dribbleSmokePuffs.filterNot { p -> p.id == id })
        }
    }

    private fun startDribbleSessionNow() {
        dribbleTimerJob?.cancel()
        dribbleCoolDownJob?.cancel()
        comboBannerJob?.cancel()
        _uiState.update {
            it.copy(
                isDribbleMode = true,
                isReactionPointsMode = false,
                isTacticalMode = false,
                useFrontCamera = true,
                calibrationStep = CalibrationStep.COMPLETED,
                dribbleScore = 0,
                dribbleCrossovers = 0,
                dribbleStreak = 0,
                dribbleLevel = 1,
                dribbleGaugeProgress = 0.25f,
                dribbleComboBanner = null,
                dribbleTimerRemainingSec = 45,
                isDribbleTimerRunning = true,
                isDribbleSessionFinished = false,
                dribblePopups = emptyList(),
                dribbleSmokePuffs = emptyList(),
                inputMode = InputMode.LIVE_CAMERA
            )
        }

        dribbleTimerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isDribbleMode && _uiState.value.dribbleTimerRemainingSec > 0) {
                delay(1000L)
                _uiState.update { current ->
                    if (!current.isDribbleMode || !current.isDribbleTimerRunning) {
                        current
                    } else {
                        val remaining = current.dribbleTimerRemainingSec - 1
                        if (remaining <= 0) {
                            dribbleCoolDownJob?.cancel()
                            current.copy(
                                dribbleTimerRemainingSec = 0,
                                isDribbleTimerRunning = false,
                                isDribbleSessionFinished = true
                            )
                        } else {
                            current.copy(dribbleTimerRemainingSec = remaining)
                        }
                    }
                }
            }
        }

        startDribbleGaugeDecayLoop()
    }

    private fun startDribbleGaugeDecayLoop() {
        dribbleCoolDownJob?.cancel()
        dribbleCoolDownJob = viewModelScope.launch {
            while (isActive && _uiState.value.isDribbleMode && _uiState.value.isDribbleTimerRunning) {
                delay(950L)
                _uiState.update { current ->
                    if (!current.isDribbleMode || !current.isDribbleTimerRunning || current.isDribbleSessionFinished) {
                        current
                    } else {
                        val decayRate = 0.04f
                        val newProgress = (current.dribbleGaugeProgress - decayRate).coerceAtLeast(0.08f)
                        current.copy(dribbleGaugeProgress = newProgress)
                    }
                }
            }
        }
    }

    fun restartDribbleMode() {
        startDribbleSessionNow()
    }

    // --- MÉTODOS DEL MODO REACTION POINTS (60 SEGUNDOS) ---
    private var reactionTimerJob: Job? = null
    private var pointTimeoutJob: Job? = null
    private var lastReactionHitTimestamp: Long = 0L
    private var lastReactionHeightIndex: Int = 0
    private var lastEmptyHandWarningTime: Long = 0L
    private var warningResetJob: Job? = null

    private fun generateReactionPoint(number: Int, side: ReactionTargetSide): ReactionPoint {
        val skeleton = latestSkeleton
        val landmarks = skeleton?.landmarks

        // Altura: zona de bote controlado (entre cadera baja y rodilla, 0.58f a 0.74f)
        val y = if (landmarks != null && landmarks.size >= 27) {
            val leftHip = landmarks.getOrNull(23)
            val rightHip = landmarks.getOrNull(24)
            val leftKnee = landmarks.getOrNull(25)
            val rightKnee = landmarks.getOrNull(26)

            val hipY = if (leftHip != null && rightHip != null && leftHip.visibility > 0.2f && rightHip.visibility > 0.2f) {
                (leftHip.y + rightHip.y) / 2f
            } else leftHip?.takeIf { it.visibility > 0.2f }?.y
                ?: rightHip?.takeIf { it.visibility > 0.2f }?.y
                ?: 0.52f

            val kneeY = if (leftKnee != null && rightKnee != null && leftKnee.visibility > 0.2f && rightKnee.visibility > 0.2f) {
                (leftKnee.y + rightKnee.y) / 2f
            } else leftKnee?.takeIf { it.visibility > 0.2f }?.y
                ?: rightKnee?.takeIf { it.visibility > 0.2f }?.y
                ?: 0.74f

            val safeHipY = hipY.coerceIn(0.46f, 0.62f)
            val safeKneeY = kneeY.coerceIn(safeHipY + 0.12f, 0.84f)

            // Variación de altura a nivel de rodilla/muslo (bote controlado lejos del torso)
            val factor = when (number % 3) {
                0 -> 0.45f
                1 -> 0.65f
                else -> 0.85f
            }
            (safeHipY + (safeKneeY - safeHipY) * factor).coerceIn(0.58f, 0.74f)
        } else {
            // Presets realistas a la altura de bote cómodo
            val realisticPresets = listOf(0.62f, 0.68f, 0.58f, 0.72f, 0.64f)
            lastReactionHeightIndex = (lastReactionHeightIndex + 1) % realisticPresets.size
            realisticPresets[lastReactionHeightIndex]
        }

        // Posición lateral X: BIEN ALEJADO DEL CUERPO (en los extremos laterales de pantalla)
        // para que el jugador nunca lo toque sin querer con el torso o piernas
        val x = if (side == ReactionTargetSide.LEFT) {
            val leftHip = landmarks?.getOrNull(23)
            if (leftHip != null && leftHip.visibility > 0.25f) {
                (leftHip.x - 0.32f).coerceIn(0.09f, 0.16f)
            } else {
                0.12f
            }
        } else {
            val rightHip = landmarks?.getOrNull(24)
            if (rightHip != null && rightHip.visibility > 0.25f) {
                (rightHip.x + 0.32f).coerceIn(0.84f, 0.91f)
            } else {
                0.88f
            }
        }

        return ReactionPoint(
            id = System.currentTimeMillis() + number,
            number = number,
            side = side,
            xNorm = x,
            yNorm = y,
            isHit = false,
            isHandOnlyBlocked = false,
            spawnTimeMs = System.currentTimeMillis(),
            durationMs = 5000L
        )
    }

    private fun startPointTimeout(pointId: Long) {
        pointTimeoutJob?.cancel()
        pointTimeoutJob = viewModelScope.launch {
            delay(5000L) // 5.0 seconds as requested by user
            val current = _uiState.value
            if (current.isReactionPointsMode && current.isReactionTimerRunning && !current.isReactionSessionFinished) {
                val active = current.activeReactionPoint
                if (active != null && active.id == pointId) {
                    // Desaparece y pierde la oportunidad de tocarlo, sale en otro sitio
                    onReactionPointExpired(active)
                }
            }
        }
    }

    private fun onReactionPointExpired(expiredPoint: ReactionPoint) {
        val nextSide = if (expiredPoint.side == ReactionTargetSide.LEFT) ReactionTargetSide.RIGHT else ReactionTargetSide.LEFT
        val nextNumber = expiredPoint.number + 1
        val nextPoint = generateReactionPoint(number = nextNumber, side = nextSide)
        _uiState.update {
            it.copy(activeReactionPoint = nextPoint)
        }
        startPointTimeout(nextPoint.id)
    }

    private fun startReactionSessionNow() {
        reactionTimerJob?.cancel()
        pointTimeoutJob?.cancel()
        val firstPoint = generateReactionPoint(number = 1, side = ReactionTargetSide.LEFT)
        _uiState.update {
            it.copy(
                isReactionPointsMode = true,
                isDribbleMode = false,
                isTacticalMode = false,
                useFrontCamera = true,
                calibrationStep = CalibrationStep.COMPLETED,
                reactionScore = 0,
                reactionTimerRemainingSec = 60,
                isReactionTimerRunning = true,
                isReactionSessionFinished = false,
                reactionPopups = emptyList(),
                activeReactionPoint = firstPoint,
                inputMode = InputMode.LIVE_CAMERA
            )
        }
        startPointTimeout(firstPoint.id)

        reactionTimerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isReactionPointsMode && _uiState.value.reactionTimerRemainingSec > 0) {
                delay(1000L)
                _uiState.update { current ->
                    if (!current.isReactionPointsMode || !current.isReactionTimerRunning) {
                        current
                    } else {
                        val remaining = current.reactionTimerRemainingSec - 1
                        if (remaining <= 0) {
                            pointTimeoutJob?.cancel()
                            current.copy(
                                reactionTimerRemainingSec = 0,
                                isReactionTimerRunning = false,
                                isReactionSessionFinished = true,
                                activeReactionPoint = null
                            )
                        } else {
                            current.copy(reactionTimerRemainingSec = remaining)
                        }
                    }
                }
            }
        }
    }

    fun startReactionPointsMode() {
        val alreadyCalibrated = _uiState.value.skeletonCalibrated && _uiState.value.ballCalibrated
        if (alreadyCalibrated) {
            startReactionSessionNow()
        } else {
            reactionTimerJob?.cancel()
            _uiState.update {
                it.copy(
                    isReactionPointsMode = true,
                    isDribbleMode = false,
                    isTacticalMode = false,
                    useFrontCamera = true,
                    calibrationStep = CalibrationStep.CALIBRATE_SKELETON,
                    skeletonCalibrationProgress = 0f,
                    skeletonCalibrationFeedback = "Colócate frente a la cámara frontal",
                    ballCalibrationProgress = 0f,
                    ballCalibrationFeedback = "Sostén el balón dentro del círculo guía",
                    inputMode = InputMode.LIVE_CAMERA,
                    isReactionTimerRunning = false,
                    isReactionSessionFinished = false,
                    reactionScore = 0,
                    activeReactionPoint = null
                )
            }
        }
    }

    fun recalibrateFrontDrills() {
        reactionTimerJob?.cancel()
        pointTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                calibrationStep = CalibrationStep.CALIBRATE_SKELETON,
                skeletonCalibrationProgress = 0f,
                skeletonCalibrated = false,
                skeletonCalibrationFeedback = "Colócate frente a la cámara frontal",
                ballCalibrationProgress = 0f,
                ballCalibrated = false,
                ballCalibrationFeedback = "Sostén el balón dentro del círculo guía",
                isReactionTimerRunning = false
            )
        }
    }

    fun restartReactionPointsMode() {
        startReactionSessionNow()
    }

    fun hitReactionPoint(pointId: Long, hitX: Float? = null, hitY: Float? = null) {
        val state = _uiState.value
        if (!state.isReactionPointsMode || !state.isReactionTimerRunning || state.isReactionSessionFinished) return
        val currentPoint = state.activeReactionPoint ?: return
        if (currentPoint.id != pointId) return

        pointTimeoutJob?.cancel()

        val newScore = state.reactionScore + 1
        val posX = currentPoint.xNorm
        val posY = currentPoint.yNorm

        val popup = ReactionPopup(
            id = System.currentTimeMillis() + (0..999).random(),
            text = "+1",
            xNorm = posX,
            yNorm = posY
        )

        val nextSide = if (currentPoint.side == ReactionTargetSide.LEFT) ReactionTargetSide.RIGHT else ReactionTargetSide.LEFT
        val nextNumber = currentPoint.number + 1
        val nextPoint = generateReactionPoint(number = nextNumber, side = nextSide)

        _uiState.update {
            it.copy(
                reactionScore = newScore,
                activeReactionPoint = nextPoint,
                reactionWarningMessage = null,
                reactionPopups = (it.reactionPopups + popup).takeLast(6)
            )
        }
        startPointTimeout(nextPoint.id)
    }

    fun dismissReactionPopup(id: Long) {
        _uiState.update {
            it.copy(reactionPopups = it.reactionPopups.filterNot { p -> p.id == id })
        }
    }

    private fun checkReactionPointHit(skeleton: PoseSkeleton?, ball: Det?) {
        val state = _uiState.value
        if (!state.isReactionPointsMode || !state.isReactionTimerRunning || state.isReactionSessionFinished) return
        val point = state.activeReactionPoint ?: return
        val landmarks = skeleton?.landmarks ?: return
        if (landmarks.size < 21) return

        val now = System.currentTimeMillis()
        if (now - lastReactionHitTimestamp < 350L) return

        val pointX = point.xNorm
        val pointY = point.yNorm

        // 1. Comprobamos las manos del esqueleto (muñecas 15/16, dedos 17/18/19/20):
        val handIndices = listOf(15, 16, 17, 18, 19, 20)
        var closestHandLm: PosePoint? = null
        var closestHandDist = Float.MAX_VALUE

        for (idx in handIndices) {
            val lm = landmarks.getOrNull(idx) ?: continue
            if (lm.visibility > 0.16f) {
                val dx = lm.x - pointX
                val dy = lm.y - pointY
                val dist = kotlin.math.hypot(dx, dy)
                if (dist < closestHandDist) {
                    closestHandDist = dist
                    closestHandLm = lm
                }
            }
        }

        // Radio amplio (0.20f) para que el toque sea reactivo e instantáneo como antes
        val isHandAtTarget = closestHandDist < 0.20f
        if (!isHandAtTarget) return

        // 2. Comprobación Inteligente de Balón + Mano:
        val currentBall = ball ?: latestEffectiveBall
        val ballDistToPoint = if (currentBall != null) {
            kotlin.math.hypot(currentBall.nx - pointX, currentBall.ny - pointY)
        } else Float.MAX_VALUE

        val handDistToBall = if (currentBall != null && closestHandLm != null) {
            val hx = closestHandLm.x
            val hy = closestHandLm.y
            kotlin.math.hypot(currentBall.nx - hx, currentBall.ny - hy)
        } else Float.MAX_VALUE

        // El bote hacia el point es válido si:
        // A) El balón está botando cerca del point (< 0.38f)
        // B) O el balón está en control de la mano que toca el point (< 0.42f)
        // C) O el balón está activo en juego botado recientemente en los últimos 700ms (ventana de rebote contra el suelo)
        val ballDirectlyAtPoint = ballDistToPoint < 0.38f
        val ballInHandControl = handDistToBall < 0.42f
        val ballRecentlyDribbledByHand = (now - lastBallNearHandTime < 700L) || (now - lastBallSeenTime < 600L)

        val isValidDribbleHit = ballDirectlyAtPoint || ballInHandControl || ballRecentlyDribbledByHand

        if (isValidDribbleHit) {
            // ¡Punto válido! Instantáneo, fluido y fiable
            lastReactionHitTimestamp = now
            warningResetJob?.cancel()
            val hitX = currentBall?.nx ?: closestHandLm?.x ?: pointX
            val hitY = currentBall?.ny ?: closestHandLm?.y ?: pointY
            viewModelScope.launch(Dispatchers.Main) {
                hitReactionPoint(point.id, hitX, hitY)
            }
        } else {
            // Si NO hay ningún balón en escena (usuario tocando con la mano vacía sin balón durante más de 1.2s):
            val noBallInGame = (now - lastBallSeenTime > 1200L) && (now - lastBallNearHandTime > 1200L)
            if (noBallInGame && now - lastEmptyHandWarningTime > 1500L) {
                lastEmptyHandWarningTime = now
                warningResetJob?.cancel()
                _uiState.update {
                    it.copy(
                        reactionWarningMessage = "⚠️ ¡Bota con el BALÓN hacia el point!\nNo vale solo con la mano vacía"
                    )
                }
                warningResetJob = viewModelScope.launch {
                    delay(1400L)
                    _uiState.update {
                        it.copy(reactionWarningMessage = null)
                    }
                }
            }
        }
    }

    fun startDribbleMode() {
        val alreadyCalibrated = _uiState.value.skeletonCalibrated && _uiState.value.ballCalibrated
        if (alreadyCalibrated) {
            startDribbleSessionNow()
        } else {
            _uiState.update {
                it.copy(
                    isDribbleMode = true,
                    isReactionPointsMode = false,
                    isTacticalMode = false,
                    useFrontCamera = true,
                    calibrationStep = CalibrationStep.CALIBRATE_SKELETON,
                    skeletonCalibrationProgress = 0f,
                    skeletonCalibrationFeedback = "Colócate frente a la cámara frontal",
                    ballCalibrationProgress = 0f,
                    ballCalibrationFeedback = "Sostén el balón dentro del círculo guía",
                    inputMode = InputMode.LIVE_CAMERA,
                    dribbleScore = 0,
                    dribbleCrossovers = 0,
                    dribbleStreak = 0,
                    dribbleLevel = 1,
                    dribbleGaugeProgress = 0.20f,
                    dribbleComboBanner = null,
                    dribbleTimerRemainingSec = 45,
                    isDribbleTimerRunning = false,
                    isDribbleSessionFinished = false,
                    dribblePopups = emptyList(),
                    dribbleSmokePuffs = emptyList()
                )
            }
        }
    }

    fun startShootingModeWithTutorial() {
        _uiState.update {
            it.copy(
                isDribbleMode = false,
                useFrontCamera = false,
                calibrationStep = CalibrationStep.POSITION_PHONE,
                ballCalibrationProgress = 0f,
                ballCalibrated = false,
                ballCalibrationFeedback = "Sostén el balón dentro del círculo guía",
                inputMode = InputMode.LIVE_CAMERA
            )
        }
    }

    fun toggleDribbleMode() {
        val nextMode = !_uiState.value.isDribbleMode
        _uiState.update {
            it.copy(
                isDribbleMode = nextMode,
                useFrontCamera = nextMode,
                calibrationStep = if (nextMode) CalibrationStep.CALIBRATE_SKELETON else CalibrationStep.COMPLETED,
                skeletonCalibrationProgress = 0f,
                skeletonCalibrated = false,
                ballCalibrationProgress = 0f,
                ballCalibrated = false
            )
        }
    }

    private fun processSkeletonCalibration(skeleton: PoseSkeleton?) {
        val lm = skeleton?.landmarks
        val hasKeyPoints = lm != null && lm.size >= 25

        val nose = if (hasKeyPoints) lm!![0] else null
        val lShoulder = if (hasKeyPoints) lm!![11] else null
        val rShoulder = if (hasKeyPoints) lm!![12] else null
        val lElbow = if (hasKeyPoints) lm!![13] else null
        val rElbow = if (hasKeyPoints) lm!![14] else null
        val lWrist = if (hasKeyPoints) lm!![15] else null
        val rWrist = if (hasKeyPoints) lm!![16] else null
        val lHip = if (hasKeyPoints) lm!![23] else null
        val rHip = if (hasKeyPoints) lm!![24] else null

        // 1. Strict Visibility: BOTH shoulders and head/nose MUST be clearly detected with >= 0.48 visibility
        val bothShouldersVisible = lShoulder != null && rShoulder != null &&
            lShoulder.visibility >= 0.48f && rShoulder.visibility >= 0.48f
        val headVisible = nose != null && nose.visibility >= 0.45f
        val limbsVisible = ((lElbow?.visibility ?: 0f) >= 0.40f || (rElbow?.visibility ?: 0f) >= 0.40f) ||
            ((lWrist?.visibility ?: 0f) >= 0.35f || (rWrist?.visibility ?: 0f) >= 0.35f) ||
            ((lHip?.visibility ?: 0f) >= 0.35f || (rHip?.visibility ?: 0f) >= 0.35f)

        // 2. Anatomical sanity checks
        // - Distance between shoulders (proves actual human body scale, not a tiny artifact)
        val shoulderDist = if (bothShouldersVisible) {
            kotlin.math.hypot(lShoulder!!.x - rShoulder!!.x, lShoulder.y - rShoulder.y)
        } else 0f
        val validScale = shoulderDist in 0.12f..0.65f

        // - Head is above shoulders
        val headAboveShoulders = if (bothShouldersVisible && headVisible) {
            val avgShoulderY = (lShoulder!!.y + rShoulder!!.y) / 2f
            nose!!.y < avgShoulderY - 0.02f
        } else false

        // - Centered in frame (between 25% and 75% horizontally)
        val centerTorsoX = if (bothShouldersVisible) (lShoulder!!.x + rShoulder!!.x) / 2f else 0f
        val isCentered = centerTorsoX in 0.25f..0.75f

        // Real person presence is only true when ALL anatomical conditions are met
        val isRealPersonPresent = bothShouldersVisible && headVisible && headAboveShoulders && validScale && isCentered && limbsVisible

        _uiState.update { current ->
            val currProgress = current.skeletonCalibrationProgress
            if (isRealPersonPresent) {
                // Progressive increase over ~2.5 seconds (at 30fps: +0.014f)
                val newProgress = (currProgress + 0.014f).coerceAtMost(1.0f)
                val isDone = newProgress >= 1.0f
                val feedback = if (isDone) {
                    "¡Cuerpo humano verificado y calibrado!"
                } else {
                    "¡Persona detectada! Mantén la postura... ${(newProgress * 100).toInt()}%"
                }
                current.copy(
                    skeletonCalibrationProgress = newProgress,
                    skeletonCalibrated = isDone,
                    skeletonCalibrationFeedback = feedback
                )
            } else {
                // Fast decay: if user steps out or camera sees empty space, reset rapidly
                val decayed = (currProgress - 0.035f).coerceAtLeast(0.0f)
                val feedback = when {
                    skeleton == null || !hasKeyPoints -> "No se detecta ninguna persona frente a la cámara"
                    !bothShouldersVisible || !headVisible -> "Colócate de frente mostrando cabeza y hombros"
                    !validScale -> if (shoulderDist < 0.12f) "Acércate más a la cámara" else "Aléjate un poco de la cámara"
                    !headAboveShoulders -> "Mantén la postura de pie mirando al móvil"
                    !isCentered -> if (centerTorsoX < 0.25f) "Muévete hacia tu derecha" else "Muévete hacia tu izquierda"
                    !limbsVisible -> "Muestra los brazos para calibrar el movimiento"
                    else -> "Buscando cuerpo del jugador..."
                }
                current.copy(
                    skeletonCalibrationProgress = decayed,
                    skeletonCalibrated = false,
                    skeletonCalibrationFeedback = feedback
                )
            }
        }
    }

    private fun processRealBallCalibration(bitmap: Bitmap, yoloBall: Det?) {
        val w = bitmap.width
        val h = bitmap.height
        // Center circle target at (0.5w, 0.45h), radius ~0.15w
        val cx = (w * 0.5f).toInt()
        val cy = (h * 0.45f).toInt()
        val radius = (w * 0.15f).toInt().coerceIn(35, 110)
        val radiusSq = radius * radius

        val minX = (cx - radius).coerceAtLeast(0)
        val maxX = (cx + radius).coerceAtMost(w - 1)
        val minY = (cy - radius).coerceAtLeast(0)
        val maxY = (cy + radius).coerceAtMost(h - 1)

        val roiW = maxX - minX + 1
        val roiH = maxY - minY + 1
        if (roiW <= 10 || roiH <= 10 || roiW * roiH > 200 * 200) return

        val pixels = IntArray(roiW * roiH)
        try {
            bitmap.getPixels(pixels, 0, roiW, minX, minY, roiW, roiH)
        } catch (_: Exception) {
            return
        }

        var totalCircleSamples = 0
        var matchedSamples = 0
        var darkSeamSamples = 0
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumHue = 0.0
        var sumSat = 0.0
        var sumVal = 0.0

        val hsv = FloatArray(3)
        // Step 2 for high performance (< 0.4ms)
        for (y in 0 until roiH step 2) {
            val actualY = minY + y
            val dy = actualY - cy
            for (x in 0 until roiW step 2) {
                val actualX = minX + x
                val dx = actualX - cx
                if (dx * dx + dy * dy <= radiusSq) {
                    totalCircleSamples++
                    val pixel = pixels[y * roiW + x]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    android.graphics.Color.colorToHSV(pixel, hsv)
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val value = hsv[2]

                    // Real basketball leather/rubber characteristics:
                    // Hue: 8° to 36° (deep orange, tan orange, burnt sienna)
                    // Saturation: >= 0.38 (crucial: distinguishes from beige walls, wood floors, desks, and human skin)
                    // Value: 0.22 to 0.88 (avoids specular reflection and deep background shadows)
                    val isOrangeLeatherHue = (hue in 8f..36f) || (hue in 356f..360f)
                    val isVibrantBasketballSat = sat >= 0.38f
                    val isGoodBrightness = value in 0.22f..0.88f
                    val isRgbBasketball = (r > 110 && r > (b * 1.45f) && r >= (g * 1.05f) && b < 120)

                    if (isOrangeLeatherHue && isVibrantBasketballSat && isGoodBrightness && isRgbBasketball) {
                        matchedSamples++
                        sumR += r
                        sumG += g
                        sumB += b
                        sumHue += hue
                        sumSat += sat
                        sumVal += value
                    } else if (value < 0.24f || (r < 65 && g < 65 && b < 65)) {
                        // Black rubber ribs / seams characteristic of basketballs
                        darkSeamSamples++
                    }
                }
            }
        }

        val matchRatio = if (totalCircleSamples > 0) matchedSamples.toFloat() / totalCircleSamples else 0f
        val darkSeamRatio = if (totalCircleSamples > 0) darkSeamSamples.toFloat() / totalCircleSamples else 0f

        // 1. YOLO Basketball Detection Check (Fast and robust for all indoor/outdoor lighting)
        val yoloBallValid = yoloBall != null && yoloBall.conf >= 0.25f
        val yoloInCircle = if (yoloBallValid) {
            val dist = kotlin.math.hypot(yoloBall!!.nx - 0.5f, yoloBall.ny - 0.45f)
            dist < 0.38f
        } else false
        val yoloVerifiedBall = yoloBallValid && yoloInCircle

        // 2. Flexible CV Basketball Check (tolerant to all lighting conditions, indoor gym, black/white/rubber balls)
        val cvVerifiedBall = matchRatio >= 0.18f || (matchRatio >= 0.12f && yoloBallValid)

        val isRealBall = yoloVerifiedBall || cvVerifiedBall || (yoloBallValid && matchRatio > 0.06f)

        _uiState.update { current ->
            val currProgress = current.ballCalibrationProgress
            if (isRealBall) {
                // Calibrate smoothly and quickly in ~15-20 frames (< 0.8s) for an effortless experience
                val newProgress = (currProgress + 0.055f).coerceAtMost(1.0f)
                val isDone = newProgress >= 1.0f
                val feedback = if (isDone) {
                    "¡Balón 100% verificado y calibrado!"
                } else {
                    "¡Balón detectado! Calibrando... ${(newProgress * 100).toInt()}%"
                }

                val colorProfile = if (isDone && matchedSamples > 10) {
                    val meanR = (sumR / matchedSamples).toInt()
                    val meanG = (sumG / matchedSamples).toInt()
                    val meanB = (sumB / matchedSamples).toInt()
                    val meanH = (sumHue / matchedSamples).toFloat()
                    val meanS = (sumSat / matchedSamples).toFloat()
                    val meanV = (sumVal / matchedSamples).toFloat()
                    val prof = BallColorProfile(
                        meanR = meanR,
                        meanG = meanG,
                        meanB = meanB,
                        minHue = (meanH - 12f).coerceAtLeast(0f),
                        maxHue = (meanH + 12f).coerceAtMost(360f),
                        minSat = (meanS - 0.12f).coerceAtLeast(0.25f),
                        minVal = (meanV - 0.18f).coerceAtLeast(0.20f)
                    )
                    fastBallTracker.calibratedColorProfile = prof
                    prof
                } else current.calibratedBallColor

                current.copy(
                    ballCalibrationProgress = newProgress,
                    ballCalibrated = isDone,
                    ballCalibrationFeedback = feedback,
                    calibratedBallColor = colorProfile
                )
            } else {
                // Very gentle decay so slight hand tremor or light flicker doesn't reset progress
                val decayed = (currProgress - 0.008f).coerceAtLeast(0.0f)
                val feedback = "Sostén el balón dentro del círculo guía"
                current.copy(
                    ballCalibrationProgress = decayed,
                    ballCalibrated = false,
                    ballCalibrationFeedback = feedback
                )
            }
        }
    }

    fun forceCompleteBallCalibration() {
        _uiState.update {
            it.copy(
                ballCalibrationProgress = 1.0f,
                ballCalibrated = true,
                ballCalibrationFeedback = "¡Balón calibrado con éxito!"
            )
        }
    }

    fun toggleCameraLens() {
        if (_uiState.value.isDribbleMode) {
            // En modo cambios de mano solo funciona la cámara frontal
            return
        }
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    fun resetDribbleScore() {
        _uiState.update {
            it.copy(
                dribbleScore = 0,
                dribbleCrossovers = 0,
                dribbleStreak = 0,
                dribblePopups = emptyList()
            )
        }
    }

    fun toggleTacticalMode() {
        val newMode = !_uiState.value.isTacticalMode
        _uiState.update { it.copy(isTacticalMode = newMode) }
    }

    fun updateCurrentTacticalAnalysis(analysis: TacticalFrameAnalysis?) {
        _uiState.update { it.copy(currentTacticalAnalysis = analysis) }
    }

    fun showTacticalReport() {
        val elapsed = (System.currentTimeMillis() - sessionStartTime).coerceAtLeast(35000L)
        val report = _uiState.value.tacticalReport ?: tacticalEngine.generateMatchReport(elapsed)
        _uiState.update { it.copy(tacticalReport = report, showTacticalReportDialog = true) }
    }

    fun dismissTacticalReportDialog() {
        _uiState.update { it.copy(showTacticalReportDialog = false) }
    }

    fun simulateTacticalPlays() {
        tacticalSimJob?.cancel()
        _uiState.update { it.copy(isSimulatingTactical = true, isTacticalMode = true) }

        tacticalSimJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                tacticalEngine.reset()

                // PLAY 1: CONTRAATAQUE (3 vs 1 en transición rápida)
                for (step in 0..25) {
                    val p = step / 25f
                    val players = listOf(
                        // 3 Home attackers rushing forward
                        TacticalPlayerTrack(id = 1, xNorm = 0.50f, yNorm = 0.20f + p * 0.65f, team = TacticalTeam.HOME, isWithBall = true, role = "Base"),
                        TacticalPlayerTrack(id = 2, xNorm = 0.20f, yNorm = 0.15f + p * 0.60f, team = TacticalTeam.HOME, role = "Alero"),
                        TacticalPlayerTrack(id = 3, xNorm = 0.80f, yNorm = 0.15f + p * 0.60f, team = TacticalTeam.HOME, role = "Escolta"),
                        // 1 Lone defender retreating
                        TacticalPlayerTrack(id = 4, xNorm = 0.50f, yNorm = 0.55f + p * 0.30f, team = TacticalTeam.AWAY, role = "Defensor")
                    )
                    val ballPos = Pair(0.50f, 0.20f + p * 0.65f)
                    val analysis = TacticalFrameAnalysis(
                        timestampMs = step * 100L,
                        players = players,
                        ballPosition = ballPos,
                        offensiveSpacingArea = 0.78f,
                        isPressingFullCourt = false,
                        isFastBreak = true,
                        isPickAndRollOccurring = false,
                        dominantTeamWithBall = TacticalTeam.HOME,
                        activePlayBadge = TacticalPlayType.TRANSITION_FASTBREAK,
                        activePlayDescription = "Contraataque en superioridad 3 vs 1 (Llegada en 2.6s)"
                    )
                    _uiState.update { it.copy(currentTacticalAnalysis = analysis) }
                    delay(50)
                }
                delay(400)

                // PLAY 2: PRESIÓN A TODA PISTA (2 defensores en saque)
                for (step in 0..25) {
                    val p = step / 25f
                    val players = listOf(
                        // Home ballhandler trapped in backcourt
                        TacticalPlayerTrack(id = 1, xNorm = 0.30f + (p * 0.05f), yNorm = 0.22f, team = TacticalTeam.HOME, isWithBall = true, role = "Base"),
                        TacticalPlayerTrack(id = 2, xNorm = 0.75f, yNorm = 0.35f, team = TacticalTeam.HOME, role = "Apoyo"),
                        // 2 Away defenders trapping
                        TacticalPlayerTrack(id = 3, xNorm = 0.27f, yNorm = 0.20f, team = TacticalTeam.AWAY, role = "Trap 1"),
                        TacticalPlayerTrack(id = 4, xNorm = 0.34f, yNorm = 0.23f, team = TacticalTeam.AWAY, role = "Trap 2"),
                        TacticalPlayerTrack(id = 5, xNorm = 0.50f, yNorm = 0.45f, team = TacticalTeam.AWAY, role = "Líbero")
                    )
                    val analysis = TacticalFrameAnalysis(
                        timestampMs = 3000L + step * 100L,
                        players = players,
                        ballPosition = Pair(0.30f, 0.22f),
                        offensiveSpacingArea = 0.42f,
                        isPressingFullCourt = true,
                        isFastBreak = false,
                        isPickAndRollOccurring = false,
                        dominantTeamWithBall = TacticalTeam.HOME,
                        activePlayBadge = TacticalPlayType.FULL_COURT_PRESS,
                        activePlayDescription = "Presión a toda pista: 2 defensores atrapando en esquina"
                    )
                    _uiState.update { it.copy(currentTacticalAnalysis = analysis) }
                    delay(50)
                }
                delay(400)

                // PLAY 3: PICK AND ROLL CENTRAL (Bloqueo y continuación del pívot al aro)
                for (step in 0..30) {
                    val p = step / 30f
                    // Screener sets pick and rolls to basket
                    val screenerX = if (p < 0.4f) 0.50f else (0.50f - (p - 0.4f) * 0.10f)
                    val screenerY = if (p < 0.4f) 0.70f else (0.70f + (p - 0.4f) * 0.24f)

                    val ballhandlerX = if (p < 0.4f) 0.52f else (0.52f + (p - 0.4f) * 0.20f)
                    val ballhandlerY = 0.68f

                    val players = listOf(
                        TacticalPlayerTrack(id = 1, xNorm = ballhandlerX, yNorm = ballhandlerY, team = TacticalTeam.HOME, isWithBall = p < 0.7f, role = "Base"),
                        TacticalPlayerTrack(id = 2, xNorm = screenerX, yNorm = screenerY, team = TacticalTeam.HOME, isWithBall = p >= 0.7f, role = "Pívot (Roll)"),
                        TacticalPlayerTrack(id = 3, xNorm = 0.15f, yNorm = 0.78f, team = TacticalTeam.HOME, role = "Esquina"),
                        TacticalPlayerTrack(id = 4, xNorm = 0.85f, yNorm = 0.78f, team = TacticalTeam.HOME, role = "Tirador"),
                        // Defenders
                        TacticalPlayerTrack(id = 5, xNorm = 0.53f, yNorm = 0.71f, team = TacticalTeam.AWAY, role = "Defensa balón"),
                        TacticalPlayerTrack(id = 6, xNorm = 0.49f, yNorm = 0.74f, team = TacticalTeam.AWAY, role = "Defensa pívot")
                    )
                    val ballPos = if (p < 0.7f) Pair(ballhandlerX, ballhandlerY) else Pair(screenerX, screenerY)
                    val analysis = TacticalFrameAnalysis(
                        timestampMs = 6000L + step * 100L,
                        players = players,
                        ballPosition = ballPos,
                        offensiveSpacingArea = 0.82f,
                        isPressingFullCourt = false,
                        isFastBreak = false,
                        isPickAndRollOccurring = true,
                        dominantTeamWithBall = TacticalTeam.HOME,
                        activePlayBadge = TacticalPlayType.PICK_AND_ROLL,
                        activePlayDescription = if (p < 0.4f) "Bloqueo directo central fijado" else "Continuación (Roll) del pívot libre hacia canasta"
                    )
                    _uiState.update { it.copy(currentTacticalAnalysis = analysis) }
                    delay(50)
                }
                delay(400)

                // Complete report generation
                val report = tacticalEngine.generateMatchReport(45000L)
                _uiState.update {
                    it.copy(
                        isSimulatingTactical = false,
                        tacticalReport = report,
                        showTacticalReportDialog = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSimulatingTactical = false) }
            }
        }
    }

    fun reset() {
        engine.reset()
        fastBallTracker.reset()
        frameTimestamps.clear()
        sessionStartTime = System.currentTimeMillis()
        _uiState.update { current ->
            VisionState(
                modelState = current.modelState,
                cameraActive = current.cameraActive,
                calibrationStep = current.calibrationStep,
                lockedHoop = current.lockedHoop,
                ballCalibrated = current.ballCalibrated,
                ballCalibrationProgress = current.ballCalibrationProgress,
                isSimulating = isSimulating,
                inputMode = current.inputMode,
                selectedVideoUri = current.selectedVideoUri,
                isRecordingLive = current.isRecordingLive,
                recordingDurationSec = current.recordingDurationSec,
                lastRecordedVideoUri = current.lastRecordedVideoUri,
                showRecordedVideoBanner = current.showRecordedVideoBanner,
                isVideoLoading = current.isVideoLoading,
                isVideoAnalyzing = current.isVideoAnalyzing,
                videoAnalysisProgress = current.videoAnalysisProgress,
                videoAnalyzedFrames = current.videoAnalyzedFrames,
                videoTotalFrames = current.videoTotalFrames,
                videoAnalysisStatus = current.videoAnalysisStatus,
                videoTimeline = current.videoTimeline,
                hoopVisual = current.hoopVisual,
                showSkeleton = current.showSkeleton,
                showHoop = current.showHoop,
                showBall = current.showBall,
                showFps = current.showFps,
                courtCalibration = current.courtCalibration,
                showCourtPointSelector = current.showCourtPointSelector
            )
        }
    }

    fun recalibrate() {
        fastBallTracker.reset()
        _uiState.update { it.copy(calibrationStep = CalibrationStep.LOCK_HOOP) }
    }

    fun simulateShot(make: Boolean = true) {
        if (isSimulating) return
        isSimulating = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val hoopNx = _uiState.value.lockedHoop?.nx ?: 0.5f
                val hoopNy = _uiState.value.lockedHoop?.ny ?: 0.28f
                val hoopCx = hoopNx * 640f
                val hoopCy = hoopNy * 640f
                val hoopW = 100f
                val hoopH = 40f
                val hoopDet = Det(cx = hoopCx, cy = hoopCy, w = hoopW, h = hoopH, conf = 0.95f)

                val playerX = 220f
                val playerY = 460f
                val playerDet = Det(cx = playerX, cy = playerY, w = 110f, h = 200f, conf = 0.90f)

                // Simulated skeleton landmarks
                val skeletonPoints = mutableListOf<PosePoint>()
                for (i in 0..32) {
                    val px = (playerX / 640f) + (if (i % 2 == 0) -0.04f else 0.04f)
                    val py = (playerY / 640f) + ((i / 33f) * 0.3f - 0.15f)
                    skeletonPoints.add(PosePoint(x = px, y = py, visibility = 0.9f))
                }
                val simSkeleton = PoseSkeleton(
                    landmarks = skeletonPoints,
                    wristReleasePoint = PosePoint(x = playerX / 640f, y = 0.50f),
                    feetCourtPoint = PosePoint(x = playerX / 640f, y = 0.75f),
                    releaseAngle = 49
                )

                var ballX = playerX
                var ballY = 380f

                // Rise
                for (step in 0..10) {
                    val p = step / 10f
                    ballX = playerX + (hoopCx - playerX) * p * 0.7f
                    ballY = 380f - p * 250f
                    val ballDet = Det(cx = ballX, cy = ballY, w = 32f, h = 32f, conf = 0.88f)
                    frameTimestamps.add(System.currentTimeMillis())
                    engine.push(DetectionFrame(ball = ballDet, hoop = hoopDet, player = playerDet, skeleton = simSkeleton))
                    delay(35)
                }

                // Peak
                for (step in 0..5) {
                    val p = step / 5f
                    ballX = playerX + (hoopCx - playerX) * (0.7f + p * 0.3f)
                    ballY = 130f - (1f - (p - 0.5f) * (p - 0.5f) * 4f) * 20f
                    val ballDet = Det(cx = ballX, cy = ballY, w = 32f, h = 32f, conf = 0.90f)
                    frameTimestamps.add(System.currentTimeMillis())
                    engine.push(DetectionFrame(ball = ballDet, hoop = hoopDet, player = playerDet, skeleton = simSkeleton))
                    delay(35)
                }

                // Descent
                val targetOffset = if (make) 0f else (hoopW * 1.2f)
                for (step in 0..12) {
                    val p = step / 12f
                    ballX = hoopCx + targetOffset * p
                    ballY = 140f + p * 180f
                    val ballDet = Det(cx = ballX, cy = ballY, w = 32f, h = 32f, conf = 0.86f)
                    frameTimestamps.add(System.currentTimeMillis())
                    engine.push(DetectionFrame(ball = ballDet, hoop = hoopDet, player = playerDet, skeleton = simSkeleton))
                    delay(35)
                }

                // Settle
                for (step in 0..10) {
                    frameTimestamps.add(System.currentTimeMillis())
                    engine.push(DetectionFrame(ball = null, hoop = hoopDet, player = playerDet, skeleton = simSkeleton))
                    delay(35)
                }
            } finally {
                isSimulating = false
                updateSnapshot()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        try {
            yoloExecutor.shutdown()
        } catch (_: Exception) {}
        try {
            poseExecutor.shutdown()
        } catch (_: Exception) {}
        detector.close()
        poseEstimator.close()
    }
}
