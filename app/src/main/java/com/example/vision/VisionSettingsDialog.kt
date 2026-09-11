package com.example.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.theme.SportOrange

@Composable
fun VisionSettingsDialog(
    state: VisionState,
    onDismiss: () -> Unit,
    onToggleShowSkeleton: () -> Unit,
    onToggleShowHoop: () -> Unit,
    onToggleShowBall: () -> Unit,
    onSetHoopPerspective: (HoopPerspective) -> Unit,
    onOpenSavedVideos: () -> Unit,
    onRecalibrateHoop: () -> Unit = {},
    onResetSession: () -> Unit = {},
    onToggleShowFps: () -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onAlignCourtPoints: () -> Unit = {},
    onToggleRecording: () -> Unit = {},
    onExitDrill: (() -> Unit)? = null,
    onSelectDribbleCombo: (() -> Unit)? = null,
    onSelectReactionPoints: (() -> Unit)? = null,
    onSelectShooting: (() -> Unit)? = null,
    onSelectUploadVideo: (() -> Unit)? = null
) {
    // Determine active training context
    val isShootingMode = !state.isDribbleMode && !state.isReactionPointsMode
    val isReactionMode = state.isReactionPointsMode
    val isDribbleMode = state.isDribbleMode

    var showModeSelector by remember { mutableStateOf(false) }

    if (showModeSelector) {
        ModeSelectionDialog(
            currentModeIsDribble = state.isDribbleMode,
            currentModeIsReaction = state.isReactionPointsMode,
            onDismiss = { showModeSelector = false },
            onSelectDribbleCombo = {
                showModeSelector = false
                onDismiss()
                onSelectDribbleCombo?.invoke()
            },
            onSelectReactionPoints = {
                showModeSelector = false
                onDismiss()
                onSelectReactionPoints?.invoke()
            },
            onSelectShooting = {
                showModeSelector = false
                onDismiss()
                onSelectShooting?.invoke()
            },
            onSelectUploadVideo = {
                showModeSelector = false
                onDismiss()
                onSelectUploadVideo?.invoke()
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F1420))
                .border(1.5.dp, Color(0x33445577), RoundedCornerShape(24.dp))
                .testTag("vision_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // 1. DIALOG HEADER (Modern, distinct, clear mode badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1E293B), Color(0xFF111827))
                                    )
                                )
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (isReactionMode) Color(0xFFFFB300) else Color(0xFF00E5FF),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            // Mode badge
                            val (badgeText, badgeColor) = when {
                                isReactionMode -> "REACTION DRILL (60S)" to Color(0xFFFFB300)
                                isDribbleMode -> "CONTROL DE BOTE AI" to Color(0xFFBA68C8)
                                else -> "SESIÓN DE TIRO" to Color(0xFF00E5FF)
                            }
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeColor,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Ajustes del Entrenamiento",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .clickable { onDismiss() }
                            .testTag("close_settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. SCROLLABLE CONTENT BODY
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECCIÓN: MINIJUEGOS Y MODOS DE ENTRENAMIENTO
                    if (onSelectDribbleCombo != null) {
                        SettingsSectionTitle(title = "MINIJUEGOS & MODOS")

                        SettingActionCard(
                            title = "Cambiar de Minijuego o Modo",
                            subtitle = "Dribble Combo (LV3), Reaction Points o Sesión de Tiro",
                            icon = Icons.Default.SportsEsports,
                            iconTint = Color(0xFF00E5FF),
                            actionLabel = "ELEGIR JUEGO ›",
                            actionColor = Color(0xFF00E5FF),
                            isHighlighted = true,
                            testTag = "setting_switch_mode_button",
                            onClick = { showModeSelector = true }
                        )
                    }

                    // SECCIÓN: GRABACIÓN Y VÍDEO (Botón de grabar integrado elegantemente dentro de ajustes)
                    if (state.inputMode == InputMode.LIVE_CAMERA) {
                        SettingsSectionTitle(title = "GRABACIÓN & VÍDEO")

                        // Tarjeta de Grabar Sesión
                        SettingActionCard(
                            title = if (state.isRecordingLive) "Detener Grabación en Vivo" else "Grabar Sesión en Vídeo",
                            subtitle = if (state.isRecordingLive)
                                "Grabando actualmente: ${state.recordingDurationSec}s"
                            else
                                "Guarda el clip de entrenamiento en tu galería",
                            icon = if (state.isRecordingLive) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            iconTint = if (state.isRecordingLive) Color(0xFFFF5252) else Color(0xFFFF1744),
                            actionLabel = if (state.isRecordingLive) "DETENER" else "GRABAR",
                            actionColor = if (state.isRecordingLive) Color(0xFFFF5252) else Color(0xFF00E5FF),
                            isHighlighted = state.isRecordingLive,
                            testTag = "setting_record_live_button",
                            onClick = onToggleRecording
                        )
                    }

                    // Tarjeta de Mis Vídeos Guardados
                    SettingActionCard(
                        title = "Mis Vídeos Guardados",
                        subtitle = "${state.savedVideos.size} grabaciones y análisis disponibles",
                        icon = Icons.Default.VideoLibrary,
                        iconTint = Color(0xFFFFB300),
                        actionLabel = "VER VÍDEOS ›",
                        actionColor = Color(0xFFFFB300),
                        testTag = "setting_open_saved_videos_button",
                        onClick = {
                            onDismiss()
                            onOpenSavedVideos()
                        }
                    )

                    // SECCIÓN: CÁMARA Y VISIÓN ARTIFICIAL
                    SettingsSectionTitle(title = "CÁMARA & DETECCIÓN IA")

                    // Selector Cámara Frontal / Trasera (Disponible en cámara en vivo)
                    if (state.inputMode == InputMode.LIVE_CAMERA) {
                        SettingActionCard(
                            title = "Cambiar Cámara",
                            subtitle = if (state.useFrontCamera) "Actualmente: Frontal (Selfie)" else "Actualmente: Trasera (Canasta)",
                            icon = Icons.Default.Cameraswitch,
                            iconTint = Color(0xFF00E5FF),
                            actionLabel = if (state.useFrontCamera) "FRONTAL" else "TRASERA",
                            actionColor = Color(0xFF00E5FF),
                            testTag = "setting_toggle_camera",
                            onClick = onToggleCamera
                        )
                    }

                    // Capa: Esqueleto del Jugador
                    SettingToggleCard(
                        title = "Esqueleto del Jugador (MediaPipe)",
                        subtitle = "Detecta brazos, muñecas y postura corporal.",
                        iconEmoji = "🦴",
                        accentColor = Color(0xFFBA68C8),
                        checked = state.showSkeleton,
                        testTag = "setting_toggle_skeleton",
                        onToggle = onToggleShowSkeleton
                    )

                    // Capa: Seguimiento del Balón
                    SettingToggleCard(
                        title = "Seguimiento del Balón",
                        subtitle = "Retículo de detección en tiempo real para el balón.",
                        iconEmoji = "🏀",
                        accentColor = SportOrange,
                        checked = state.showBall,
                        testTag = "setting_toggle_ball",
                        onToggle = onToggleShowBall
                    )

                    // Capa: Contador de FPS
                    SettingToggleCard(
                        title = "Contador de FPS en Pantalla",
                        subtitle = "Muestra los fotogramas por segundo procesados.",
                        iconEmoji = "⚡",
                        accentColor = Color(0xFF00E676),
                        checked = state.showFps,
                        testTag = "setting_toggle_fps",
                        onToggle = onToggleShowFps
                    )

                    // Opción de Recalibrar Jugador y Balón (en modos frontales: bote y reaction)
                    if (isReactionMode || isDribbleMode) {
                        SettingActionCard(
                            title = "Recalibrar Jugador y Balón",
                            subtitle = if (state.skeletonCalibrated && state.ballCalibrated)
                                "Calibración lista ✓ (Válida para ambos modos)"
                            else
                                "Calibrar postura corporal y textura del balón",
                            icon = Icons.Default.CropFree,
                            iconTint = Color(0xFF00E5FF),
                            actionLabel = "CALIBRAR ›",
                            actionColor = Color(0xFF00E5FF),
                            testTag = "setting_recalibrate_front_drills_button",
                            onClick = {
                                onDismiss()
                                onRecalibrateHoop()
                            }
                        )
                    }

                    // SECCIÓN: CANASTA & PISTA (Únicamente visible en Modo Tiro en Canasta)
                    if (isShootingMode) {
                        SettingsSectionTitle(title = "CANASTA & PISTA DE TIRO")

                        // Capa: Aro y Canasta
                        SettingToggleCard(
                            title = "Aro y Canasta",
                            subtitle = "Tablero, aro y red con detección de aciertos.",
                            iconEmoji = "🎯",
                            accentColor = Color(0xFF00E5FF),
                            checked = state.showHoop,
                            testTag = "setting_toggle_hoop",
                            onToggle = onToggleShowHoop
                        )

                        // Selector de Perspectiva 3D del Aro
                        if (state.showHoop) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF161E2E))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "PERSPECTIVA 3D DE LA CANASTA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                                Text(
                                    text = "Adapta la inclinación del tablero según el ángulo de la cámara:",
                                    fontSize = 11.sp,
                                    color = Color(0xFFAAAAAA)
                                )

                                val currentPerspective = state.lockedHoop?.perspective ?: HoopPerspective.AUTO
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HoopPerspective.values().forEach { persp ->
                                        val isSelected = currentPerspective == persp
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("setting_perspective_${persp.name}")
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF00E5FF) else Color(0x33FFFFFF),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { onSetHoopPerspective(persp) }
                                                .padding(vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = persp.shortLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isSelected) Color.Black else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Calibrar Aro
                        SettingActionCard(
                            title = "Calibrar / Fijar Aro",
                            subtitle = "Ajustar la posición de la canasta en la cámara",
                            icon = Icons.Default.CropFree,
                            iconTint = Color(0xFF00E5FF),
                            actionLabel = "CALIBRAR ›",
                            actionColor = Color(0xFF00E5FF),
                            testTag = "setting_recalibrate_button",
                            onClick = {
                                onDismiss()
                                onRecalibrateHoop()
                            }
                        )

                        // Alinear Pista 3PT
                        SettingActionCard(
                            title = "Alinear Pista (3PT y Zona)",
                            subtitle = "Ajustar puntos de triple y pintura en el suelo",
                            icon = Icons.Default.Tune,
                            iconTint = Color(0xFFB388FF),
                            actionLabel = "ALINEAR ›",
                            actionColor = Color(0xFFB388FF),
                            testTag = "setting_align_court_button",
                            onClick = {
                                onDismiss()
                                onAlignCourtPoints()
                            }
                        )
                    }

                    // SECCIÓN: REINICIO DE SESIÓN
                    SettingsSectionTitle(title = "ACCIONES RÁPIDAS")

                    val resetTitle = when {
                        isReactionMode -> "Reiniciar Reto (60s)"
                        isDribbleMode -> "Reiniciar Puntuación de Bote"
                        else -> "Reiniciar Contador de Tiros"
                    }
                    val resetSubtitle = when {
                        isReactionMode -> "Comienza un nuevo temporizador de 60 segundos"
                        isDribbleMode -> "Pone a cero la puntuación y racha de crossovers"
                        else -> "Pone a cero tiros encestados y fallados"
                    }

                    SettingActionCard(
                        title = resetTitle,
                        subtitle = resetSubtitle,
                        icon = Icons.Default.Refresh,
                        iconTint = Color(0xFF8E8E93),
                        actionLabel = "REINICIAR",
                        actionColor = Color(0xFFE0E0E0),
                        testTag = "setting_reset_session_button",
                        onClick = {
                            onDismiss()
                            onResetSession()
                        }
                    )

                    // Salir al Menú Principal (dentro de ajustes como solicitado)
                    if (onExitDrill != null) {
                        SettingActionCard(
                            title = "Salir del Entrenamiento",
                            subtitle = "Finalizar la sesión actual y volver al menú principal",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            iconTint = Color(0xFFFF5252),
                            actionLabel = "SALIR",
                            actionColor = Color(0xFFFF5252),
                            testTag = "setting_exit_drill_button",
                            onClick = {
                                onDismiss()
                                onExitDrill()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 3. DIALOG FOOTER: "LISTO" BUTTON
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF9800), Color(0xFFFF6D00))
                            )
                        )
                        .clickable { onDismiss() }
                        .testTag("settings_done_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GUARDAR Y CONTINUAR",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Título de sección dentro de la ventana de ajustes
 */
@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        color = Color(0xFF7E8B9F),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

/**
 * Tarjeta de acción con botón a la derecha (sin que el texto se rompa verticalmente)
 */
@Composable
private fun SettingActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    actionLabel: String,
    actionColor: Color,
    testTag: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isHighlighted) Color(0x33F44336) else Color(0xFF161E2E))
            .border(
                1.dp,
                if (isHighlighted) Color(0xFFF44336) else Color(0x22FFFFFF),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contenedor principal que ocupa todo el ancho disponible
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF9EABB8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Pastilla de acción a la derecha con ancho seguro anti-wrap
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(actionColor.copy(alpha = 0.16f))
                    .border(1.dp, actionColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = actionColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Tarjeta de conmutador (Switch) para activar/desactivar capas
 */
@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    iconEmoji: String,
    accentColor: Color,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161E2E))
            .border(
                1.dp,
                if (checked) accentColor.copy(alpha = 0.4f) else Color(0x22FFFFFF),
                RoundedCornerShape(14.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconEmoji,
                        fontSize = 18.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF9EABB8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = Color(0xFF7E8B9F),
                    uncheckedTrackColor = Color(0xFF1E2838)
                )
            )
        }
    }
}
