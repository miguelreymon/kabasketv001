package com.example.vision

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.theme.SportBorder
import com.example.theme.SportError
import com.example.theme.SportMuted
import com.example.theme.SportOnBrand
import com.example.theme.SportOnSurface
import com.example.theme.SportOrange
import com.example.theme.SportSuccess
import com.example.theme.SportWarning

@Composable
fun PulseDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun VisionHUD(
    state: VisionState,
    cameraReady: Boolean,
    debug: Boolean,
    onToggleDebug: () -> Unit,
    onReset: () -> Unit,
    onRecalibrateHoop: () -> Unit,
    onSimulateShot: (Boolean) -> Unit,
    onToggleRecording: () -> Unit,
    onPickVideo: () -> Unit,
    onAnalyzeRecordedVideo: () -> Unit,
    onDismissVideoBanner: () -> Unit,
    onToggleHoopVisual: () -> Unit = {},
    onToggleTacticalMode: () -> Unit = {},
    onShowTacticalReport: () -> Unit = {},
    onSimulateTactical: () -> Unit = {},
    onOpenSavedVideos: () -> Unit = {},
    onReopenOnboarding: () -> Unit = {},
    onTriggerDribbleCrossover: () -> Unit = {},
    onDismissDribblePopup: (Long) -> Unit = {},
    onToggleDribbleMode: () -> Unit = {},
    onToggleShowSkeleton: () -> Unit = {},
    onToggleShowHoop: () -> Unit = {},
    onToggleShowBall: () -> Unit = {},
    onCycleHoopPerspective: () -> Unit = {},
    onSetHoopPerspective: (HoopPerspective) -> Unit = {},
    onToggleShowFps: () -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onOpenCourtAlignment: () -> Unit = {},
    onCourtPointMoved: (String, Float, Float) -> Unit = { _, _, _ -> },
    onCourtPresetApplied: (CourtPreset) -> Unit = {},
    onCourtReset: () -> Unit = {},
    onCloseCourtAlignment: () -> Unit = {},
    onSelectDribbleCombo: () -> Unit = {},
    onSelectReactionPoints: () -> Unit = {},
    onSelectShooting: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showModeSelectionDialog by remember { mutableStateOf(false) }

    if (showModeSelectionDialog) {
        ModeSelectionDialog(
            currentModeIsDribble = state.isDribbleMode,
            currentModeIsReaction = state.isReactionPointsMode,
            onDismiss = { showModeSelectionDialog = false },
            onSelectDribbleCombo = onSelectDribbleCombo,
            onSelectReactionPoints = onSelectReactionPoints,
            onSelectShooting = onSelectShooting,
            onSelectUploadVideo = onPickVideo
        )
    }

    if (showSettingsDialog) {
        VisionSettingsDialog(
            state = state,
            onDismiss = { showSettingsDialog = false },
            onToggleShowSkeleton = onToggleShowSkeleton,
            onToggleShowHoop = onToggleShowHoop,
            onToggleShowBall = onToggleShowBall,
            onSetHoopPerspective = onSetHoopPerspective,
            onOpenSavedVideos = onOpenSavedVideos,
            onRecalibrateHoop = onRecalibrateHoop,
            onResetSession = onReset,
            onToggleShowFps = onToggleShowFps,
            onToggleCamera = onToggleCamera,
            onAlignCourtPoints = onOpenCourtAlignment,
            onToggleRecording = onToggleRecording,
            onExitDrill = onReopenOnboarding,
            onSelectDribbleCombo = onSelectDribbleCombo,
            onSelectReactionPoints = onSelectReactionPoints,
            onSelectShooting = onSelectShooting,
            onSelectUploadVideo = onPickVideo
        )
    }

    val minutes = state.sessionDurationSec / 60
    val seconds = state.sessionDurationSec % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    val recMin = state.recordingDurationSec / 60
    val recSec = state.recordingDurationSec % 60
    val recFormatted = String.format("%02d:%02d", recMin, recSec)

    Box(modifier = modifier.fillMaxSize()) {
        // Edge scrims
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xCC000000), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
        )

        // Always render skeleton, locked hoop, and trajectory overlay
        DetectionOverlay(state = state)

        // Dribble Score popups animation (+5 points effect when changing hands)
        if (state.isDribbleMode && state.dribblePopups.isNotEmpty()) {
            DribbleScorePopupOverlay(
                popups = state.dribblePopups,
                onPopupExpired = onDismissDribblePopup
            )
        }

        // HUD Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Exit 'X' Button + Ball AI Branding + Camera Status + Recording Badge
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Botón simple con una X para salir de los modos de entrenamiento a la pantalla principal
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC1B1C2A))
                                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                                .clickable { onReopenOnboarding() }
                                .testTag("exit_training_mode_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Salir a la pantalla principal",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsBasketball,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "POWERED BY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0x99FFFFFF)
                            )
                            Text(
                                text = "BALL AI • KABASKET",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = SportOnSurface
                            )
                        }

                        // Botón de acceso directo a Minijuegos y Modos de Entrenamiento
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                                    )
                                )
                                .clickable { showModeSelectionDialog = true }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                                .testTag("top_bar_switch_mode_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "JUEGOS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    // REC indicator when recording
                    if (state.isRecordingLive) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xDDF44336))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PulseDot(color = Color.White)
                            Text(
                                text = "REC $recFormatted",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    // FPS badge (activable desde Ajustes por petición del usuario)
                    if (state.showFps && state.fps > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xCC111827))
                                .border(1.dp, Color(0x4400E676), RoundedCornerShape(999.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (state.fps >= 24) Color(0xFF00E676) else Color(0xFFFFB300))
                            )
                            Text(
                                text = "${state.fps} FPS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Right: Actions (Grabar, Cargar vídeo) + Session Card
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Action Buttons: Ajustes de Capas y estado de grabación (botón de grabar oculto en ajustes)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Indicador si la grabación está activa
                        if (state.isRecordingLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xDDF44336))
                                    .border(1.dp, Color.Red, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PulseDot(color = Color.White)
                                    Text(
                                        text = "REC ${state.recordingDurationSec}s",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Icono Ajustes (para configurar capas y grabar sesión)
                        Box(
                            modifier = Modifier
                                .testTag("vision_settings_button")
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                                .clickable { showSettingsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ajustes",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Session Card (Solo visible en modo canasta/tiro; en modo bote se oculta para no estorbar)
                    if (!state.isDribbleMode) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xB31E1E24))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Entrenamiento",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SportOnSurface
                                        )
                                        Text(
                                            text = "${state.makes}/${state.attempts} ENCESTADOS",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF00E5FF)
                                        )
                                    }

                                    val progress = if (state.attempts > 0) state.makes.toFloat() / state.attempts else 0f
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.size(30.dp),
                                            color = Color(0xFF00E5FF),
                                            trackColor = Color(0x33FFFFFF),
                                            strokeWidth = 3.dp
                                        )
                                        Text(
                                            text = "${state.accuracy}%",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = SportOnSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // NOTIFICATION BANNER: When a recorded video is ready to analyze or view in Mis Videos
            AnimatedVisibility(
                visible = state.showRecordedVideoBanner,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xEE1E1E24))
                        .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "¡ENTRENAMIENTO GUARDADO EN MIS VÍDEOS!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                            Text(
                                text = "Guardado con tus estadísticas. Toca para analizar ahora o consúltalo en Mis Vídeos.",
                                fontSize = 11.sp,
                                color = SportOnSurface
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .testTag("analyze_now_button")
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF00E5FF))
                                    .clickable { onAnalyzeRecordedVideo() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ANALIZAR AHORA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                                    .clickable { onDismissVideoBanner() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = SportOnSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // MIDDLE SECTION: Debug info if enabled
            if (debug) {
                Column(
                    modifier = Modifier
                        .testTag("debug_panel")
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC000000))
                        .border(1.dp, SportBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = "ESQUELETO: ${if (state.skeleton != null) "DETECTADO (${state.skeleton.landmarks.size} pts)" else "BUSCANDO"}", fontSize = 11.sp, color = Color(0xFF9C27B0), fontWeight = FontWeight.Bold)
                    Text(text = "BALÓN: ${state.ball?.let { String.format("%.2f", it.conf) } ?: "—"}", fontSize = 11.sp, color = SportOrange, fontWeight = FontWeight.Bold)
                    Text(text = "ARO: ${if (state.lockedHoop?.isLocked == true) "BLOQUEADO" else state.hoop?.let { String.format("%.2f", it.conf) } ?: "—"}", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    Text(text = "ESTADO TIRO: ${state.detectorState.label} | ÚLTIMO: ${state.lastEvent}", fontSize = 11.sp, color = SportOnSurface, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }

            if (state.isTacticalMode) {
                // TACTICAL FLOATING CONTROL AND 2D COURT RADAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xDD121218))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tactical Court Radar (2D Homography View)
                    TacticalCourtMap(
                        analysis = state.currentTacticalAnalysis,
                        modifier = Modifier
                            .width(170.dp)
                            .height(110.dp)
                    )

                    // Tactical Play & Report Controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Play badge
                        val activePlay = state.currentTacticalAnalysis?.activePlayBadge
                        val activeDesc = state.currentTacticalAnalysis?.activePlayDescription ?: "Analizando posicionamiento táctico..."

                        if (activePlay != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(activePlay.badgeColor))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = activePlay.title,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Text(
                            text = activeDesc,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xEEFFFFFF),
                            maxLines = 2
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Demo Simulator button
                            Box(
                                modifier = Modifier
                                    .testTag("simulate_tactical_button")
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (state.isSimulatingTactical) SportOrange else Color(0x3300E5FF))
                                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                                    .clickable { onSimulateTactical() }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (state.isSimulatingTactical) "SIMULANDO..." else "DEMO PATRONES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (state.isSimulatingTactical) Color.Black else Color(0xFF00E5FF)
                                )
                            }

                            // View Report button
                            Box(
                                modifier = Modifier
                                    .testTag("show_tactical_report_button")
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF00E5FF))
                                    .clickable { onShowTacticalReport() }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "VER INFORME",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }

            // BOTTOM BAR: Scoreboard (Center) + Controls (Left) + Court Map / Dribble Badge (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left Controls (Clean layout: calibration moved to Settings; test buttons removed)
                if (state.isDribbleMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // In Dribble Mode: Button to simulate crossover (+5 points popup effect!)
                        Box(
                            modifier = Modifier
                                .testTag("simulate_dribble_crossover_button")
                                .clip(RoundedCornerShape(999.dp))
                                .background(SportOrange)
                                .clickable { onTriggerDribbleCrossover() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "⚡", fontSize = 14.sp)
                                Text(
                                    text = "SIMULAR CAMBIO (+5)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Reset Session Button
                        Box(
                            modifier = Modifier
                                .testTag("reset_button")
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .border(1.dp, SportBorder, CircleShape)
                                .clickable { onReset() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reiniciar",
                                tint = SportOnSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Empty placeholder matching CourtShotMap width (175.dp) to keep the Center Scoreboard perfectly centered
                    Box(modifier = Modifier.width(175.dp))
                }

                // Center Scoreboard
                if (state.isDribbleMode) {
                    // Scoreboard Modo Bote / Dribble AI
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.testTag("dribble_scoreboard")
                    ) {
                        Text(
                            text = "CONTROL DE BOTE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = SportOrange
                        )

                        Text(
                            text = "${state.dribbleScore} PTS",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )

                        Text(
                            text = "${state.dribbleCrossovers} cambios de mano  •  Racha 🔥 x${state.dribbleStreak}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color(0xCCFFFFFF)
                        )
                    }
                } else {
                    // Scoreboard Modo Tiro en Canasta
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.testTag("shooting_scoreboard")
                    ) {
                        Text(
                            text = timeFormatted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xCCFFFFFF)
                        )

                        Text(
                            text = "${state.makes} / ${state.attempts}",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = SportOnSurface
                        )

                        Text(
                            text = "${state.releaseAngle?.let { "$it°" } ?: "N/A°"}  |  ${state.releaseTimeSec?.let { String.format("%.2f s", it) } ?: "N/A s"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0x99FFFFFF)
                        )
                    }
                }

                // Right: Mini Court Shot Chart en modo tiro; en modo bote se quita el badge a petición del usuario
                if (!state.isDribbleMode) {
                    CourtShotMap(shots = state.courtShots)
                } else {
                    // En modo bote se quita el badge inferior derecho ("MODO BOTE / SIN CANASTA")
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }
        }

        // Overlay: Re-alinear puntos de la pista desde Ajustes o HUD
        if (state.showCourtPointSelector) {
            CourtPointSelectorView(
                courtCalibration = state.courtCalibration,
                onPointMoved = onCourtPointMoved,
                onApplyPreset = onCourtPresetApplied,
                onReset = onCourtReset,
                onConfirm = onCloseCourtAlignment,
                onSkip = onCloseCourtAlignment
            )
        }
    }
}
