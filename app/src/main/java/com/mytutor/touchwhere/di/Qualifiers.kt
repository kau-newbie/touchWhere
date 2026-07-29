package com.mytutor.touchwhere.di
import javax.inject.Qualifier

// 1. 인증 관련 서버용 이름표 - 별도 API key를 가진 유저용으로 할겁니다.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ThirdPartyChatApi

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ThirdPartySttApi

// 2. 메인 데이터 서버용 이름표 - 우리 서버에 보내는 API에는 이걸로 할겁니다.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultChatApi