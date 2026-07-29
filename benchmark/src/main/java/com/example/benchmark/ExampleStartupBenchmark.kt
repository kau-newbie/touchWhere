package com.example.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        // 1. 측정할 내 앱의 패키지 이름 (반드시 정확해야 함!)
        packageName = "com.mytutor.kakaotalktutorviews",

        // 2. 측정할 지표: 앱 시작 시간
        metrics = listOf(StartupTimingMetric()),

        // 3. 반복 횟수: 5번 실행해서 평균을 냄
        iterations = 5,

        // 4. 시작 모드: COLD(완전 처음 켬), WARM, HOT 중 선택
        startupMode = StartupMode.COLD
    ) {
        // 5. 테스트 시나리오: 홈 화면에서 앱 아이콘을 누르고 기다림
        pressHome()
        startActivityAndWait()
    }
}