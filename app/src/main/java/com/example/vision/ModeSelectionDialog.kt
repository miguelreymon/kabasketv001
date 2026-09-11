package com.example.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Diálogo modal para cambiar instantáneamente entre minijuegos y modos de entrenamiento
 * desde cualquier pantalla de la app (Tiro, Dribble Combo, Reaction Points o Ajustes).
 */
@Composable
fun ModeSelectionDialog(
    currentModeIsDribble: Boolean,
    currentModeIsReaction: Boolean,
    onDismiss: () -> Unit,
    onSelectDribbleCombo: () -> Unit,
    onSelectReactionPoints: () -> Unit,
    onSelectShooting: () -> Unit,
    onSelectUploadVideo: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(26.dp))
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(26.dp))
                .testTag("mode_selection_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101726))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabecera del selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF00E5FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "MINIJUEGOS & MODOS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Elige qué entrenar",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 1. NUEVO: DRIBBLE COMBO (LV3 & SLICK MOVES)
                ModeCardItem(
                    title = "Dribble Combo (LV3 & Slick Moves)",
                    badge = "🔥 ¡NUEVO JUEGO! • LV3",
                    badgeColor = Color(0xFF00FFC2),
                    description = "Medidor vertical LV3 dinámico, combos 'Slick Moves' y efecto de humo estilo dibujo animado en los pies al cambiar de mano.",
                    emoji = "⚡",
                    isActive = currentModeIsDribble,
                    accentColor = Color(0xFF00E5FF),
                    testTag = "select_mode_dribble_combo",
                    onClick = {
                        onDismiss()
                        onSelectDribbleCombo()
                    }
                )

                // 2. REACTION POINTS (60S)
                ModeCardItem(
                    title = "Bote & Reaction Points (60s)",
                    badge = "REACTION DRILL",
                    badgeColor = Color(0xFFFFB300),
                    description = "Bota con una mano mientras tocas los objetivos a izquierda y derecha con la mano libre en 60 segundos.",
                    emoji = "🎯",
                    isActive = currentModeIsReaction,
                    accentColor = Color(0xFFFFB300),
                    testTag = "select_mode_reaction_points",
                    onClick = {
                        onDismiss()
                        onSelectReactionPoints()
                    }
                )

                // 3. SESIÓN DE TIRO EN CANASTA
                val isShootingActive = !currentModeIsDribble && !currentModeIsReaction
                ModeCardItem(
                    title = "Sesión de Tiro en Canasta",
                    badge = "TUTORIAL + ARO",
                    badgeColor = Color(0xFF00B0FF),
                    description = "Calibra el aro con la cámara trasera y registra tus tiros, aciertos, ángulos y postura.",
                    emoji = "🏀",
                    isActive = isShootingActive,
                    accentColor = Color(0xFF00B0FF),
                    testTag = "select_mode_shooting",
                    onClick = {
                        onDismiss()
                        onSelectShooting()
                    }
                )

                // 4. SUBIR VÍDEO DE GALERÍA
                ModeCardItem(
                    title = "Subir y Analizar Vídeo",
                    badge = "GALERÍA / MP4",
                    badgeColor = Color(0xFFB388FF),
                    description = "Sube una grabación desde tu móvil para descomponer la técnica de tiro o bote fotograma a fotograma.",
                    emoji = "🎬",
                    isActive = false,
                    accentColor = Color(0xFFB388FF),
                    testTag = "select_mode_upload_video",
                    onClick = {
                        onDismiss()
                        onSelectUploadVideo()
                    }
                )
            }
        }
    }
}

@Composable
private fun ModeCardItem(
    title: String,
    badge: String,
    badgeColor: Color,
    description: String,
    emoji: String,
    isActive: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.16f) else Color(0xFF161E2E))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) accentColor else Color(0x22FFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 24.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                if (isActive) {
                    Text(
                        text = "• ACTIVO",
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFFA0AEC0),
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
    }
}
