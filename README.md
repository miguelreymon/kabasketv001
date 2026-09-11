# KaBasket (KA BASKET VISION TEST)

KaBasket is an on-device computer vision basketball shot tracking and vision testing Android application built with Kotlin, Jetpack Compose, CameraX, and TensorFlow Lite.

## Features

- **Live On-Device Computer Vision**: Runs YOLOv8 object detection on-device using TensorFlow Lite (`best_float32.tflite`) with zero network latency and complete user privacy.
- **Real-Time Detection Indicators**: Continuously detects and highlights the **PLAYER**, **BALL**, and **HOOP** with confidence scores.
- **Basketball Shot State Machine (`ShotEngine`)**:
  - **Stage 1 (Ball Above)**: Detects when the ball ascends above the hoop within calibrated lateral boundaries.
  - **Stage 2 (Ball Below)**: Tracks the descent of the ball below the rim or detect rim disappearance.
  - **Stage 3 (Classification)**: Accurately classifies shots into **MAKE** or **MISS** using trajectory narrowing, disappearance heuristics, and hoop vanish logic.
  - **Location Estimation**: Classifies release and entry orientation as `LEFT`, `CENTER`, or `RIGHT`.
- **Live HUD Display**:
  - Camera status indicator with pulsing green/red dot (`CAMERA: ON / OFF`)
  - Live FPS counter
  - Real-time detection status pills for Player, Ball, and Hoop
  - Shot statistics stack: **SHOTS**, **MAKE**, **MISS**, and **ACCURACY (%)**
  - Shooting location badge (`LOCATION`)
  - Quick **RESET** and **DEBUG** controls
  - **TEST SHOT (SIMULATE)** trigger to test the state machine and trajectory overlay with realistic animated basketball shots
- **Interactive Debug Overlay**:
  - Precise color-coded bounding boxes for Player (Blue), Hoop (Yellow), and Ball (Brand Orange)
  - Normalized trajectory trail showing the path of the ball
  - Live telemetry readout displaying object confidences, processed frame count, detector lifecycle state, and model status
- **Permission & Privacy Handling**:
  - Seamless Camera permission gate with clear explanations of on-device processing
  - Settings fallback flow if permission is denied

## Tech Stack

- **Platform**: Android (minSdk 26, targetSdk 36, compileSdk 36)
- **Language**: Kotlin 2.1
- **UI Toolkit**: Jetpack Compose with Material Design 3
- **Camera Pipeline**: CameraX 1.4 (`PreviewView`, `ImageAnalysis`)
- **ML / Inference**: TensorFlow Lite 2.16 (CPU delegate)
- **Architecture**: MVVM with Kotlin Coroutines and StateFlow
- **Theme**: Dark Sport palette with high-contrast basketball orange accents
