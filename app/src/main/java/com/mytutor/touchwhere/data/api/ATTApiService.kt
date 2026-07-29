package com.mytutor.touchwhere.data.api

import com.mytutor.touchwhere.data.dto.AttResponse
import  retrofit2.http.PartMap
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

/*
    자바란 참... 어렵네요;;

    interface로 함수 prototype만 선언한 이유는, prarameter로 안에서 annotation을 통해 multipart라던가, post 방식이라던가,
    이렇게 적어놓으면 retrofit 내부적으로 알아서 해석하고 알아서 만들어준대요..;; 이게 무슨 ㄴㅁㅇㄻㄴㄹㅇ
 */
interface ATTApiService {
    @Multipart
    @POST
    suspend fun transcribeAudio(            //suspend를 이용: 통신응답이 오지 않으면, 앱을 중지x, 백그라운드에서 작업하도록 한다.
        @Url url: String,
        @Part file: MultipartBody.Part,      //얘네가 전부 파라미터에요.
        @PartMap params: Map<String, @JvmSuppressWildcards RequestBody>
    ): Response<AttResponse>    //transcribeAudio 함수의 return 타입은 OpenAiResponse.
    // { } 함수 본문 어디갔냐, 하면 retrofit이 만들어준대요.
}