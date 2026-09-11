package com.example.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

enum class HomeBottomTab {
    HOME,
    THE_COURT,
    WORKOUT,
    PROFILE
}

/**
 * Pantalla principal Home inspirada fielmente en la interfaz moderna con gamificación,
 * tarjeta hero interactiva de Dribbling con preview de cancha, tarjetas de rachas/XP
 * y barra de navegación inferior con acceso directo a Workout.
 */
@Composable
fun MainHomeScreen(
    onNavigateToWorkouts: () -> Unit,
    onLaunchDribbleDrill: () -> Unit = onNavigateToWorkouts,
    userHandle: String = "SHARPWING3098",
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(HomeBottomTab.HOME) }
    val scrollState = rememberScrollState()

    // Animación continua y sutil para dar vida y dinamismo a la interfaz
    val infiniteTransition = rememberInfiniteTransition(label = "home_pulse_anim")
    val playPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "play_pulse"
    )
    val flameGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_glow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
    ) {
        // Contenido scrolleable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 86.dp) // Espacio para la barra de navegación inferior
                .verticalScroll(scrollState)
        ) {
            // 1. TOP HEADER (Título + Avatar estilizado)
            HomeHeaderRow(
                userHandle = userHandle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )

            // 2. BANNER INCENTIVO MAGENTA / PINK
            HomeIncentiveBanner(
                text = "🎉 Keep training and climbing the leaderboard!",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. TARJETA HERO PRINCIPAL ("NEXT UP: DRIBBLING")
            HeroDribblingCard(
                playPulseScale = playPulseScale,
                onPlayClick = onLaunchDribbleDrill,
                onBrowseMoreClick = onNavigateToWorkouts,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4. FILA DE GAMIFICACIÓN (ROOKIE, 0 DAYS, 0 XP)
            GamificationCardsRow(
                flameGlowAlpha = flameGlowAlpha,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(26.dp))
        }

        // 5. BARRA DE NAVEGACIÓN INFERIOR ELEGANTE (DARK)
        HomeBottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                if (tab == HomeBottomTab.WORKOUT) {
                    onNavigateToWorkouts()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

/**
 * Encabezado con tipografía condensada y avatar badge circular multicolor con estética de baloncesto.
 */
@Composable
private fun HomeHeaderRow(
    userHandle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "WELCOME BACK $userHandle!",
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.4).sp,
            color = Color(0xFF22242A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Avatar estilizado Hoopstars (multicolor con textura de baloncesto)
        Box(
            modifier = Modifier
                .testTag("home_avatar_badge")
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFF00E5FF),
                            Color(0xFFFF2A85),
                            Color(0xFFFFD600),
                            Color(0xFF00E5FF)
                        )
                    )
                )
                .padding(2.5.dp)
                .clip(CircleShape)
                .background(Color(0xFF00BCD4)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Líneas de cancha / costuras de balón
                drawCircle(
                    color = Color(0xFFFF4081),
                    radius = w * 0.46f,
                    style = Stroke(width = 2.5f)
                )
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(w * 0.5f, 0f),
                    end = Offset(w * 0.5f, h),
                    strokeWidth = 2.5f
                )
            }

            // Emblema H★ central
            Text(
                text = "H★",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF111827)
            )
        }
    }
}

/**
 * Banner superior vibrante en tono magenta/pink con mensaje de incentivo.
 */
@Composable
private fun HomeIncentiveBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE91E63))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Tarjeta Hero Azul Eléctrico "NEXT UP: DRIBBLING"
 * Incluye el anillo de progreso circular, el preview del drill en la cancha con badges,
 * el botón de Play interactivo con animación y el enlace inferior para explorar más drills.
 */
@Composable
private fun HeroDribblingCard(
    playPulseScale: Float,
    onPlayClick: () -> Unit,
    onBrowseMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D63F3),
                        Color(0xFF0052D4)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header del Card: Medidor 0% + Títulos
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Anillo de progreso 0%
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 7.dp.toPx()
                        // Pista circular blanca translúcida
                        drawCircle(
                            color = Color(0x33FFFFFF),
                            style = Stroke(width = strokeWidth)
                        )
                        // Arco sutil de inicio
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = 18f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Donut central blanco con 0%
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "0%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NEXT UP: DRIBBLING",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Finish 5 more workouts to unlock Level 2!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xE6FFFFFF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contenedor de la vista previa del Drill
            Box(
                modifier = Modifier
                    .testTag("hero_drill_preview_card")
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onPlayClick() }
            ) {
                // Imagen de fondo del jugador botando en la pista multicolor
                Image(
                    painter = painterResource(id = R.drawable.img_drill_dribbling),
                    contentDescription = "Dribbling Drill Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradiente oscuro inferior para máxima legibilidad del texto
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x22000000),
                                    Color(0x99000000)
                                ),
                                startY = 100f
                            )
                        )
                )

                // Badges en la esquina superior derecha ("2 MIN", "BEGINNER")
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "2 MIN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "BEGINNER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }

                // Título inferior izquierdo "DRIBBLING"
                Text(
                    text = "DRIBBLING",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 14.dp)
                )

                // Botón Play negro circular con animación pulsante
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 14.dp)
                        .scale(playPulseScale)
                        .size(46.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF111114))
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Empezar Entrenamiento",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Enlace inferior "Browse More Drills →"
            Box(
                modifier = Modifier
                    .testTag("browse_more_drills_btn")
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White)
                    ) { onBrowseMoreClick() }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Browse More Drills  →",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Fila con las 3 tarjetas de gamificación (Rookie, 0 Days, 0 XP)
 * diseñadas con badges circulares flotantes que sobresalen por la parte superior.
 */
@Composable
private fun GamificationCardsRow(
    flameGlowAlpha: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. ROOKIE CARD (Magenta / Rosa)
        GamificationBadgeCard(
            topBadgeContent = {
                // Badge MVP League con trofeo
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD2E3))
                        .border(2.dp, Color(0xFFE91E63), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterVertically,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "MVP",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFC2185B),
                            lineHeight = 9.sp
                        )
                        Icon(
                            imageVector = Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFC2185B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            title = "ROOKIE",
            subtitle = "Keep climbing",
            cardColor = Color(0xFFE91E63),
            modifier = Modifier.weight(1f)
        )

        // 2. 0 DAYS STREAK CARD (Cyan / Turquesa con llama animada)
        GamificationBadgeCard(
            topBadgeContent = {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB2EBF2))
                        .border(2.dp, Color(0xFF00BCD4), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 18.sp,
                        modifier = Modifier.scale(flameGlowAlpha)
                    )
                }
            },
            title = "0 DAYS",
            subtitle = "Don't break it",
            cardColor = Color(0xFF00ACC1),
            modifier = Modifier.weight(1f)
        )

        // 3. 0 XP CARD (Royal Blue)
        GamificationBadgeCard(
            topBadgeContent = {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFBBDEFB))
                        .border(2.dp, Color(0xFF2196F3), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "XP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1565C0)
                    )
                }
            },
            title = "0",
            subtitle = "XP earned",
            cardColor = Color(0xFF1E88E5),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Componente individual de tarjeta con badge flotante superior.
 */
@Composable
private fun GamificationBadgeCard(
    topBadgeContent: @Composable () -> Unit,
    title: String,
    subtitle: String,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(cardColor)
            .padding(top = 10.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Badge flotante
            topBadgeContent()

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xEEFFFFFF),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Barra de navegación inferior oscura (#121214) con 4 items:
 * Home (activo con indicador elevado circular), The Court, Workout (acceso directo a juegos) y Profile.
 */
@Composable
private fun HomeBottomNavigationBar(
    selectedTab: HomeBottomTab,
    onTabSelected: (HomeBottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color(0xFF111114),
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TAB 1: HOME (Con diseño especial elevado de balón)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("nav_tab_home")
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(HomeBottomTab.HOME) }
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (selectedTab == HomeBottomTab.HOME) Color(0xFF23242A) else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (selectedTab == HomeBottomTab.HOME) Color.White else Color.Transparent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SportsBasketball,
                        contentDescription = "Home",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Home",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == HomeBottomTab.HOME) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedTab == HomeBottomTab.HOME) Color.White else Color(0xFF888888)
                )
            }

            // TAB 2: THE COURT
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("nav_tab_court")
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(HomeBottomTab.THE_COURT) }
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        // Icono minimalista de cancha / llave de baloncesto
                        drawRect(
                            color = if (selectedTab == HomeBottomTab.THE_COURT) Color.White else Color(0xFF888888),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = if (selectedTab == HomeBottomTab.THE_COURT) Color.White else Color(0xFF888888),
                            radius = 4.dp.toPx(),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "The Court",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == HomeBottomTab.THE_COURT) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedTab == HomeBottomTab.THE_COURT) Color.White else Color(0xFF888888)
                )
            }

            // TAB 3: WORKOUT (¡Al pulsar abre la pantalla de seleccionar cada juego!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("nav_tab_workout")
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(HomeBottomTab.WORKOUT) }
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FitnessCenter,
                        contentDescription = "Workout",
                        tint = if (selectedTab == HomeBottomTab.WORKOUT) Color(0xFFFF5722) else Color(0xFF888888),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Workout",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == HomeBottomTab.WORKOUT) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedTab == HomeBottomTab.WORKOUT) Color(0xFFFF5722) else Color(0xFF888888)
                )
            }

            // TAB 4: PROFILE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("nav_tab_profile")
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(HomeBottomTab.PROFILE) }
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = if (selectedTab == HomeBottomTab.PROFILE) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Profile",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == HomeBottomTab.PROFILE) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedTab == HomeBottomTab.PROFILE) Color.White else Color(0xFF888888)
                )
            }
        }
    }
}
