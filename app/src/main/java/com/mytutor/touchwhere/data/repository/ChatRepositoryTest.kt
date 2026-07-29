package com.mytutor.touchwhere.data.repository

import com.mytutor.touchwhere.data.api.ChatApiService
import com.mytutor.touchwhere.data.dto.ChatMessage
import com.mytutor.touchwhere.data.network.ChatGptModelSpec
import com.mytutor.touchwhere.util.log
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
class ChatRepositoryTest @Inject constructor(
    private val chatApi: ChatApiService,
    @param:ApplicationContext private val context: Context
) {
    // 대화의 문맥을 유지하기 위한 리스트
    private val conversationHistory = mutableListOf<ChatMessage>()

    init {
        log("ChatGPTModule 초기화되었습니다.")

        // 시스템 프롬프트
        val systemPrompt = getSystemPrompt().trimIndent()
        // 대화 기록 맨 첫 줄에 시스템 프롬프트를 삽입
        conversationHistory.add(ChatMessage(role = "system", content = systemPrompt))
    }
    // GoalGuide에서 계속 호출할 메인 함수
    suspend fun analyzeTextOnly(prompt: String): String {
        try {
            log("ChatGPT API에게 질문 전송 중...")
            if (prompt.isBlank() || prompt.length <= 1) return "오류: 빈 프롬프트"

            // 1. 시스템 프롬프트 찾기
            val systemMsg = conversationHistory.firstOrNull { it.role == "system" }
            // 2. AI가 직전에 대답했던 응답  1개.
            val lastAssistantMsg = conversationHistory.lastOrNull { it.role == "assistant" }
            // 3. AI 한테 전에 보낸 내 user prompt 1개.
            val lastUserMsg = conversationHistory.lastOrNull { it.role == "user" }
            // 4. 기존 대화 기록 비우기 (오래된 UI 로그 삭제)
            conversationHistory.clear()

            // 5. 다시 조립하기 (직전 AI와의 문답)
            if (systemMsg != null) {
                conversationHistory.add(systemMsg)
            }
            if (lastUserMsg != null) {
                conversationHistory.add(lastUserMsg)
            }
            if (lastAssistantMsg != null) {
                conversationHistory.add(lastAssistantMsg)
            }

            // 5. 현재 사용자의 새로운 질문(최신 UI 로그) 추가
            conversationHistory.add(ChatMessage(role = "user", content = prompt))
            // 6. 통신 시작
            val spec = ChatGptModelSpec(messages = conversationHistory)
            log("=============실제로 보낸 프롬프트==========\n${spec.messages}\n======================")
            val response = chatApi.getChatCompletion(
                url = spec.endPoint,
                request = spec.toRequestBody()
            )

            if (response.isSuccessful) {
                val assistantMsg = response.body()?.choices?.firstOrNull()?.message
                if (assistantMsg != null) {
                    // 이번에 받은 AI의 응답을 기록 (다음 루프 때 '직전 응답'으로 쓰임)
                    conversationHistory.add(assistantMsg)
                    log("ChatGPT 응답 성공!")
                    return assistantMsg.content
                }
            } else {
                val errorStr = response.errorBody()?.string()
                log("ChatGPT 통신 에러: ${response.code()} - $errorStr")
            }

        } catch (e: Exception) {
            log("ChatGPT 인터넷 문제 또는 예외 발생: $e")
        }
        return "응답없음"
    }

    fun clearHistory() {
        val systemMsg = conversationHistory.firstOrNull { it.role == "system" }
        conversationHistory.clear()
        if (systemMsg != null) {
            conversationHistory.add(systemMsg)
        }
    }
    private fun getSystemPrompt(): String {
        return try {
            context.assets.open("system_prompt.txt").use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            log("프롬프트 파일 읽기 실패: ${e.message}")
            // 파일이 없거나 읽기 실패 시 기본값 반환
            ""
        }
    }
}



