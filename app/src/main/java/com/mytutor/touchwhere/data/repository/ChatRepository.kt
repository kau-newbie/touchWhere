package com.mytutor.touchwhere.data.repository

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Backend

import com.mytutor.touchwhere.util.log
import javax.inject.Inject
import android.content.Context
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.text.trimIndent

class ChatRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val systemInstruction = getSystemPrompt().trimIndent()

    init {
        setupLocalLLM()
    }

    private fun setupLocalLLM() {
        try {
            val engineConfig = EngineConfig(
                modelPath = "/data/local/tmp/gemma-4-E2B-it.litertlm",
                // 에뮬레이터면 CPU(), 실제면 GPU()
                backend = Backend.CPU()
            )

            engine = Engine(engineConfig).apply { initialize() }

            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(
                    topK = 4,
                    topP = 0.2 ,
                    temperature = 0.1)
            )

            conversation = engine?.createConversation(convConfig)
            log("로컬 Gemma-4 엔진 초기화 완료")
        } catch (e: Exception) {
            log("로컬 모델 로드 실패: ${e.message}")
        }
    }

    fun analyzeTextOnly(prompt: String): String {
        if (conversation == null) return "엔진이 준비되지 않았습니다."

        return try {
            log("로컬 LLM 추론 시작 (동기)...")
            // 공식 문서 기준: conversation.sendMessage("질문 문자열") -> Message 반환
            val responseMessage: Message = conversation?.sendMessage(prompt)
                ?: throw Exception("Response is null")

            log("Gemma-4 응답 성공")
            responseMessage.contents.toString()
        } catch (e: Exception) {
            log("추론 도중 예외 발생: $e")
            "오류 발생: ${e.localizedMessage}"
        }
    }

    fun clearHistory() {
        // 기존 대화 내용을 지우고 새로운 세션을 생성하여 문맥 초기화
        conversation?.close()
        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction)
        )
        conversation = engine?.createConversation(convConfig)
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