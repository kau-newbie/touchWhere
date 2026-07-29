package com.mytutor.touchwhere.data.repository
import  com.mytutor.touchwhere.util.log

class PromptChecker {
/**
    fun checkRequest(user_input: String): String{
        if (guardrail.detect_injection(user_input)){
            log("필터링에 의해 프롬프트 걸러짐. 유해한 질의.")
            return "부적절한 요청을 받았습니다. 잠시 후 다시 시도하세요"
        }
    }
    fun checkResponse(res: String): String{
        if(!guardrail.validate(res, schema=SafeResponseSchema){
            log("필터링에 의해 응답 걸러짐. 유해한 응답.")
            return "부적절한 응답을 받았습니다. 잠시 후 다시 시도하세요."
        }
    }
    **/
}