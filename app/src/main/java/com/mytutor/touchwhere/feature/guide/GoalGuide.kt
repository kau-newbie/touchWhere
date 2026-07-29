package com.mytutor.touchwhere.feature.guide

import com.mytutor.touchwhere.data.repository.ChatRepository
import com.mytutor.touchwhere.util.log
import com.mytutor.touchwhere.data.buffer.UIstateBuffer
import com.mytutor.touchwhere.data.buffer.PromptBuffer
import com.mytutor.touchwhere.data.repository.ChatRepositoryTest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive

import kotlinx.coroutines.withContext
// 반복된 이벤트 flowing을 막기 위한 라이브러리
// Flow 연산자를 쓰기 위해 import 필요
import kotlinx.coroutines.flow.debounce

import kotlinx.coroutines.currentCoroutineContext
import com.mytutor.touchwhere.util.BubbleStateChangeable
import com.mytutor.touchwhere.util.StringEditor
import com.mytutor.touchwhere.util.toGuideSignature
class GoalGuide(private val llmModule: ChatRepositoryTest,
                private val animationManageable: AnimationManageable,
                private val onShowSpeechBubble: BubbleStateChangeable ){
    init{log("goalguide 만들어지는 시점.")}
    var responseText: String = """응답없음."""
    /*
        이 클래스가 해야할 일:
            루프를 돌면서 매번 사용자에게 지시를 내려줌. 언제까지? goal을 달성할 때까지.
     */
    // 루프 돌기전에, 일단 조건문 검사할테니까, 미리 첫 루프는 실행

    // 1. 루프를 돈다.
    // 1-1. 언제까지? goal을 달성할 때까지.
    // goal 달성을 어떻게 아는가? gemini응답으로. <== 특정 키워드가 있어야 할 것.
    suspend fun processEvent() { // processEvent 함수를 AccessibilityService에서 호출하면,

        if(isGoalAchived(responseText)){
            UIstateBuffer.isTouched.set(false)
            return
        }

        UIstateBuffer.uiStateFlow
            //.takeWhile{(!isGoalAchived(responseText))}
            //.debounce(200L) // (ms단위) 동안 새로운 이벤트가 없으면 그때 아래로 통과시킴
            .collect{
                log("===================================\n새로운 ui 스냅샷이 UIstateBuffer.LogFlow에 업데이트된 시점.\n" +
                        "===================================")
                log("\n현 시점 isTouched : ${UIstateBuffer.isTouched}\n isMicUsed : ${UIstateBuffer.isMicUsed}")
                //여기선 isMicUsed가 죽은 job이 prompt를 변경시키는 걸 막아줄 것.
            if(UIstateBuffer.isMicUsed.compareAndSet(true,true) && UIstateBuffer.isTouched.compareAndSet(true,false)){
                log("isMicused && isTouched 통과. 이제 false로 바꿈.")
                PromptBuffer.buildFinalPrompt()
                //LLM 호출
                onShowSpeechBubble.onStateChange("분석중입니다. 조작을 잠시 멈춰주세요.")
                withContext(Dispatchers.IO) {
                    responseText = llmModule.analyzeTextOnly(PromptBuffer.getFinalPrompt())
                }
                //UIStateBuffer에서 저장해둔 좌표 정보를 responseText에 붙여서 finalResText로 만듦.
                val finalResText = StringEditor.makeCompleteResponse(responseText)
                log("===========final responseText========\n$finalResText\n")
                //지시사항, ui 요소 좌표, 행동을 signature로 이전과 비교.
                val (instructionText, coordinateNumbers, guidedAction) = StringEditor.extractSignature(finalResText)
                val currentGuideSignature = GuideSignature(instructionText.trim(), coordinateNumbers, guidedAction.trim())
                //lastGuideSignature는 추후 수정이 필요. 단, 알람 시간을 선택한 후 "ok(확인)버튼을 누르세요."같이 반복되는 signature가 발생하는 경우가 있음.
                val lastGuideSignature = PromptBuffer.getFootprint(PromptBuffer.FootprintViewOptions.LAST_ONLY).toGuideSignature()
                log("비교 대상 1 (신규): $currentGuideSignature")
                log("비교 대상 2 (기존): $lastGuideSignature")
                log("결과: ${currentGuideSignature == lastGuideSignature}")
                //앞선 루프가 "목표달성!"시에도 return
                if(lastGuideSignature.instruction.contains("목표달성")) return@collect
                log("\nn번 째 LLM 응답(마이크 녹음 호출 이후): $finalResText\n")
                // 1-5. 애니메이션을 띄운다.
                currentCoroutineContext().ensureActive() // 취소해야할 때인지 확인.
                animationManageable.preprocessResponse(finalResText)
            }else{
                UIstateBuffer.isTouched.set(false)
                log("isTouched.set(false) 시점(isTOuched&&isMicUsed != true)")
            }
        }

    }
    // method
    fun isGoalAchived(reponsePrompt : String): Boolean{
        return reponsePrompt.contains("목표달성!")
    }
}

