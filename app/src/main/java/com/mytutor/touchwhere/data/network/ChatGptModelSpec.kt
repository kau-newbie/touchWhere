package com.mytutor.touchwhere.data.network

import com.mytutor.touchwhere.data.dto.ChatMessage
import com.mytutor.touchwhere.data.dto.ChatRequest

// ChatGPT에게 보낼 Request 규격
data class ChatGptModelSpec(
    val messages: List<ChatMessage>,   // 그동안의 대화 내용 전체
    val temperature: Float = 0.2f      // 0에 가까울수록 일관된 답변
): ChatModelSpec{
    val model: String = "gpt-4o" // 사용할 모델 gpt-4o
    override val endPoint: String = "v1/chat/completions"

    override fun toRequestBody(): ChatRequest {
        return ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature
        )
    }
}