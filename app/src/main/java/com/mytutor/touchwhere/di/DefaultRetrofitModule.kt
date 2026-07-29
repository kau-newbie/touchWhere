package com.mytutor.touchwhere.di

import com.example.myapp.data.network.createRetrofit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import dagger.hilt.components.SingletonComponent
import dagger.Module
import dagger.Provides
import dagger.hilt.*
import jakarta.inject.Singleton

private const val OPENAI_API_URL = "https://api.openai.com/"
@Module
@InstallIn(SingletonComponent::class)
object DefaultRetrofitModule {
    val baseurl = OPENAI_API_URL
    @Provides
    @Singleton
    @DefaultChatApi
    fun provideChatRetrofit(
        builder: OkHttpClient.Builder, // CommonModule에서 온 기본 빌더
        gson: GsonConverterFactory
    ): Retrofit {
        val client = builder
            .addInterceptor { chain ->
                // API 전용 키 추가
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json") // JSON 형식 명시
                    .build()
                chain.proceed(request)
            }
            .build()

        return client.createRetrofit(baseurl,gson)
    }
}