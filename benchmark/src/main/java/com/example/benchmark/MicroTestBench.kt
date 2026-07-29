package com.example.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleMicroBenchmark {

    // 1. 마이크로벤치마크 규칙 선언 (이게 측정 도구입니다)
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmarkMyFunction() {
        // 2. 측정하고 싶은 코드 작성
        // measureRepeated: 안드로이드가 알아서 수천 번 반복 실행하며 평균을 냅니다.
        benchmarkRule.measureRepeated {
            // [테스트할 코드]
            val result = performExpensiveOperation()

            // (참고) 결과값은 runWithMeasurementDisabled로 감싸지 않는 이상
            // 컴파일러 최적화로 삭제되지 않도록 내부적으로 처리됩니다.
        }
    }

    // 테스트할 무거운 함수 예시
    private fun performExpensiveOperation(): String {
        var text = ""
        for (i in 0 until 100) {
            text += i.toString()
        }
        return text
    }
}