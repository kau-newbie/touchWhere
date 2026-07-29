package com.mytutor.touchwhere.util

import android.util.Log

// 공통 태그
private const val MODULE_TAG = "KakaoTutor"

fun Any.log(message: String?) {
    // 클래스 이름 parsing해서 얻기.
    val className = this::class.java.simpleName.substringBefore("$").ifEmpty { "Anonymous" }

    // 태그는 'KakaoTutor'로 고정.
    // 메시지는 '[클래스명] 실제메시지' 형태.
    Log.d(MODULE_TAG, "[$className] ${message ?: "empty."}")
}