package com.example.jumpgpt

import android.app.Application
import com.example.jumpgpt.util.TimeUtil
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JumpGPTApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TimeUtil.init(this)
    }
} 