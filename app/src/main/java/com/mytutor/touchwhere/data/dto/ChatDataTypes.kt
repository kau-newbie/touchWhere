package com.mytutor.touchwhere.data.dto

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float
)
// 2. 대화 한 마디 한 마디의 규격
data class ChatMessage(
    @SerializedName("role")
    val role: String,    // role (system, user, agent)
    @SerializedName("content")
    val content: String  // 대화 내용
)

// 3. ChatGPT가 보내주는 Response 규격
data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChatMessage
)