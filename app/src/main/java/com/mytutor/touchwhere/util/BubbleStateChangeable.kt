package com.mytutor.touchwhere.util

interface BubbleStateChangeable {
    fun onStateChange(message: String) // 모듈이 Activity에게 상태 보고
    fun onResult(text: String)
    fun onError(message: String)
}