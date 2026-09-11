package com.example.onboarding

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gestor del flujo de Onboarding secuencial (Pantallas 1, 2, 3)
 * 1: Bienvenida
 * 2: Coach IA Personal
 * 3: Modalidad de Entrenamiento
 * Al completar la pantalla 3, se invoca [onFinishOnboarding] con la modalidad elegida.
 */
@Composable
fun OnboardingFlowScreen(
    onFinishOnboarding: (OnboardingConfig.TrainingModeChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by rememberSaveable { mutableIntStateOf(1) }

    BackHandler(enabled = currentStep > 1) {
        currentStep -= 1
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "onboarding_step_transition"
        ) { step ->
            when (step) {
                1 -> Step1WelcomeView(
                    onStartClick = {
                        currentStep = 2
                    },
                    onLoginClick = {
                        Toast.makeText(
                            context,
                            "Modo demostración: Entrando a la app...",
                            Toast.LENGTH_SHORT
                        ).show()
                        onFinishOnboarding(OnboardingConfig.TrainingModeChoice.SHOOTING_TUTORIAL)
                    }
                )
                2 -> Step3CoachPlanView(
                    onBuildPlanClick = {
                        currentStep = 3
                    },
                    onBackClick = {
                        currentStep = 1
                    }
                )
                3 -> Step4TrainingModeSelectorView(
                    onSelectMode = { mode ->
                        onFinishOnboarding(mode)
                    },
                    onBackClick = {
                        currentStep = 2
                    }
                )
            }
        }

        // Botón discreto superior para saltar o ir directo a la app en desarrollo
        TextButton(
            onClick = { onFinishOnboarding(OnboardingConfig.TrainingModeChoice.SHOOTING_TUTORIAL) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(top = 8.dp, end = 12.dp)
        ) {
            Text(
                text = "Saltar",
                color = OnboardingConfig.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}
