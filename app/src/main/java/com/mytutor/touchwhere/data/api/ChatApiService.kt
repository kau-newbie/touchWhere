package com.mytutor.touchwhere.data.api

import com.mytutor.touchwhere.data.dto.ChatRequest
import com.mytutor.touchwhere.data.dto.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

// Retrofit에게 "이 주소로, 이 데이터를 POST 방식으로 보내라"고 지시하는 명세서
interface ChatApiService {
    @POST
    suspend fun getChatCompletion(
        @Url url: String,
        @Body request: ChatRequest // 우리가 만든 request를 보냄
    ): Response<ChatResponse>      // ChatGPT의 답장을 받아옴
}