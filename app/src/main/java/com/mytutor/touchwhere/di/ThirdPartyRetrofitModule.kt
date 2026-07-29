package com.mytutor.touchwhere.di

import com.example.myapp.data.network.createRetrofit
import com.mytutor.touchwhere.data.api.ChatApiService
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import dagger.hilt.*
import jakarta.inject.Singleton
import com.example.myapp.data.network.createService
import com.mytutor.touchwhere.data.api.ATTApiService
import com.mytutor.touchwhere.util.log
import okio.Buffer

private const val OPENAI_API_URL = "https://api.openai.com/"
private const val OPENAI_API_KEY = "sk-"
@Module
@InstallIn(SingletonComponent::class)
object ThirdPartyRetrofitModule {
    const val baseurl = OPENAI_API_URL
    // [1] Chat API용 Retrofit
    @Provides
    @Singleton
    @ThirdPartyChatApi
    fun provideChatRetrofit(
        builder: OkHttpClient.Builder, // CommonModule에서 온 기본 빌더
        gson: GsonConverterFactory
    ): Retrofit {
        val client = builder.build().newBuilder()
            .addInterceptor { chain ->
                // API 전용 키 추가
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${OPENAI_API_KEY.trim()}")
                    .header("Content-Type", "application/json") // JSON 형식 명시
                    .build()
                // [디버깅 로그 추가]
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val fullJson = buffer.readUtf8()
                log("전체 JSON 길이: ${fullJson.length}") // 길이를 먼저 확인
                log("JSON 바디 시작: ${fullJson.take(100)}") // 앞부분 100자만
                log("JSON 바디 끝: ${fullJson.takeLast(100)}")
                chain.proceed(request)
            }
            .build()

        return client.createRetrofit(baseurl,gson)
    }

    // [2] STT API용 Retrofit
    @Provides
    @Singleton
    @ThirdPartySttApi
    fun provideMapRetrofit(
        builder: OkHttpClient.Builder,
        gson: GsonConverterFactory
    ): Retrofit {
        val client = builder
            .addInterceptor { chain ->
                // API 전용 키 추가
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                    .build()
                chain.proceed(request)
            }
            .build()

        return client.createRetrofit(baseurl,gson)
    }
    // retrofit과 service 반환을 분리.
    @Provides
    fun provideChatApi(@ThirdPartyChatApi retrofit: Retrofit): ChatApiService =
        retrofit.createService(ChatApiService::class.java)

    @Provides
    fun provideSttApi(@ThirdPartySttApi retrofit: Retrofit): ATTApiService =
        retrofit.createService(ATTApiService::class.java)
}