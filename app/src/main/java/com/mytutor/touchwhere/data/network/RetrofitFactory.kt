// 파일명: RetrofitFactory.kt 혹은 RetrofitExtensions.kt
package com.example.myapp.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * OkHttpClient를 기반으로 Retrofit 객체를 생성하는 확장 함수
 */
// 1. retrofit 객체를 만들고
fun OkHttpClient.createRetrofit(
    baseUrl: String,
    gson: GsonConverterFactory
): Retrofit {
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(this) // 이 함수를 호출한 OkHttpClient 인스턴스가 주입됨
        .addConverterFactory(gson)
        .build()
}
// 2. retrofit service를 만듦(.create())
fun <T> Retrofit.createService(service: Class<T>): T = this.create(service)