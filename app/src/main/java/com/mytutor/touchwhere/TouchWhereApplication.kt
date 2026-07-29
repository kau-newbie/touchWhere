package com.mytutor.touchwhere

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TouchWhereApplication : Application() {
    // 나중에 앱이 켜질 때 초기화할 작업이 생기면 여기에 onCreate()를 작성.
    override fun onCreate() {
        super.onCreate()
    }
}