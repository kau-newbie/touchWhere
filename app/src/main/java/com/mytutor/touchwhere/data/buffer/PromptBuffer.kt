package com.mytutor.touchwhere.data.buffer

import android.content.Context
import com.mytutor.touchwhere.R
import com.mytutor.touchwhere.util.log

// LLM API 전송을 위한 공용 버퍼객체
/*
 <<사용법>>
 내부적으로 basePrompt라는 String을 담는 메모리 상 버퍼가 있다.

 1. fun loadBasePrompt(context: Context) {

    기능: 버퍼 초기화. res/raw/ 기본 프롬프트 txt 파일을 읽어들인다.

 2. fun buildFinalPrompt(response: OpenAiResponse){

    기능: 실행시 프롬프트를 완성.

    주의 사항: **실행 시점이 적어도, 음성파일이 변환되고 그 결과가 String으로 버퍼에 저장된 이후여야 함.**
 */
object PromptBuffer { //object는 전역 scope를 가지는 객체. (공용 버퍼에요!)
    private var theGoal: String =""
    private var basePrompt: String = ""
    //only for test
    private var finalPrompt : String =""
    private val footprints = mutableListOf<String>()

    /*
     앱 실행 시 'res/raw' (읽기 전용 원본)에서 직접 파일을 읽어
     '메모리(basePrompt 변수)'로 로드.
     */
    fun loadBasePrompt(context: Context) {
        try {
            // 1. 'res/raw/base_prompt.txt' 를 연다.
            val inputStream = context.resources.openRawResource(R.raw.base_prompt)

            // 2. 파일 내용을 읽어서 basePrompt 변수(메모리)에 바로 저장.
            basePrompt = inputStream.bufferedReader().use { it.readText() }

            inputStream.close()
            // Log는 Logcat에서 볼 수 있음.
            log("res/raw에서 기본 프롬프트를 메모리로 로드 성공.")

        } catch (e: Exception) {
           log("프롬프트 파일 읽기 실패. 기본값을 사용합니다.${e}")
            basePrompt = ""
        }
    }

    // 초기화 함수 (앱 종료하거나 새로 시작할 때)
    fun clearAll() {
        theGoal = ""
        finalPrompt = ""
        footprints.clear()
        log("\n버퍼 클리어 완료\n")
    }

    fun addFootprint(instructionSet: String) {
        footprints.add(instructionSet)
    }
    enum class FootprintViewOptions {
        ALL, LAST_ONLY
    }

    fun getFootprint(option: FootprintViewOptions = FootprintViewOptions.ALL): String {
        if (footprints.isEmpty()) return "아직 사용자가 받은 지시 없음."

        return when (option) {
            FootprintViewOptions.LAST_ONLY -> footprints.last()
            FootprintViewOptions.ALL -> footprints.joinToString("\n")
        }
    }

    fun buildFinalPrompt(){
        val historyText = getFootprint()
        val footprintsSection = "\n[지금까지 순서대로 받은 지시사항들]\n$historyText\n"

        finalPrompt = basePrompt
            /* response(wihpser로부터) 받아온 reponse.text를 THE_GOAL에 대체하고,
            LogData의 logFlow.value를 MY_UI_LOG에 대체할 것.
        */
            .replace("FOOT_PRINTS", "위에서 아래로 시간순: $footprintsSection")
            .replace("THE_GOAL", theGoal)
            .replace("MY_UI_LOG", UIstateBuffer.logFlowNoLocInfo)
            .replace(
                "NEXT_ACTIONS",
                "touch, swipe_left, swipe_right, swipe_up, swipe_down, type"
            )
        log("최종 프롬프트 생성 완료. 발자국 수: ${footprints.size}개\n")
        //log("최종 완성 프롬프트 내용: $finalPrompt\n------------------")
    }
    //set&get the goal
    fun setTheGoal(responseText : String){
        theGoal =  responseText
    }
    fun getTheGoal():String{
        return theGoal
    }

    fun getFinalPrompt(): String{
        log("getFinalPrompt() 호출.")
        return finalPrompt
    }
}