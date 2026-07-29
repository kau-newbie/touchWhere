package com.mytutor.touchwhere.data.network
import com.mytutor.touchwhere.data.dto.ChatRequest
interface ChatModelSpec {
    val endPoint: String
    fun toRequestBody(): ChatRequest
}

