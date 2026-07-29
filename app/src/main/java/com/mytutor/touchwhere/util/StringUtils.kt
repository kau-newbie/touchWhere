package com.mytutor.touchwhere.util

import com.mytutor.touchwhere.data.buffer.UIstateBuffer
import com.mytutor.touchwhere.feature.guide.GuideSignature

const val REGEX_LINE_NUMBER = """\(LN:(\d+)\)"""

fun String.getLineAt(lineIndex: Int): String? {
    val allLines = this.lines() // 줄바꿈(\n)을 기준으로 리스트화
    return allLines.getOrNull(lineIndex) // 해당 인덱스가 없으면 null 반환
}
internal fun String.trimCoords(): String{
    val regex = Regex("Rect.*?\\)")
    // 패턴에 매칭되는 모든 부분을 빈 문자열("")로 대체
    return this.replace(regex, "")
}
internal fun String.toGuideSignature(): GuideSignature {
    val parts = this.split("\t")

    val instruction = parts.getOrNull(0)?.trim() ?: ""
    val rawCoords = parts.getOrNull(1)?.trim() ?: ""
    val action = parts.getOrNull(2)?.trim() ?: ""

    val coords = Regex("\\d+").findAll(rawCoords).mapNotNull { it.value.toIntOrNull() }.toList()

    log("디버깅 - 최종 추출된 coords: $coords")
    return GuideSignature(instruction, coords, action)
}
object StringEditor {
    // parsing하는 함수.
    fun extractTargetLine(fullResponse: String, pattern: String): String {
        val afterSubstring = fullResponse.substringAfter(pattern, "")
        if(afterSubstring == "") return ""
        return afterSubstring.substringBefore("\n").trim()
    }
    fun extractRegex(str: String, pattern: String): String {
        // 정규식 정의: """를 사용하면 이스케이프(\)를 줄일 수 있어 편하대요.
        val regex = pattern.toRegex()

        // find를 통해 매칭되는 부분을 찾고, groupValues를 통해 추출합니다.
        val matchResult = regex.find(str)

        if (matchResult != null) {
            // 예시로 ....(LN:123)... 인 String에서
            // groupValues[0]은 전체 매칭값 "(LN:123)"
            // groupValues[1]은 첫 번째 괄호 안의 숫자 "123"
            val coordsLine = matchResult.groupValues[1]
            return coordsLine
        }
        else return "0"
    }

    fun extractSignature(fullResponse: String): GuideSignature {
        val resTextParsed = extractTargetLine(fullResponse, "Rect")
        val coordinateNumbers = Regex("[0-9]+")
            .findAll(resTextParsed)
            .map { it.value.toIntOrNull() } // 변환 실패 시 null 반환
            .filterNotNull()               // null 값은 리스트에서 제외
            .toList()
        val guidedAction= extractTargetLine(fullResponse, "행동:").trim()
        val instructionText = extractTargetLine(fullResponse, """지시사항:""").trim()
        return GuideSignature(instructionText, coordinateNumbers, guidedAction)
    }

    fun assembleCoords(fullResponse: String, coords: String?): String{
        if(coords == null) return fullResponse
        val assembledRes = buildString{
            append(fullResponse)
            append("\n")
            append(coords.trim())
        }
        return assembledRes
    }
    fun makeCompleteResponse(responseText: String): String{
        val coordsLine = StringEditor.extractRegex(responseText, REGEX_LINE_NUMBER).toInt()
        val foundCoords = UIstateBuffer.getSavedUIState().getLineAt(coordsLine)
        val finalResText = StringEditor.assembleCoords(responseText, foundCoords)
        return finalResText
    }
}