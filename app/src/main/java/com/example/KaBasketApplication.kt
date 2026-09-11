package com.example

import android.app.Application
import com.example.vision.VideoAnalysisNotificationHelper

class KaBasketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VideoAnalysisNotificationHelper.createNotificationChannel(this)
    }
}
