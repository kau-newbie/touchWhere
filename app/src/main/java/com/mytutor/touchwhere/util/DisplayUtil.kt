package com.mytutor.touchwhere.util

import android.content.Context
import kotlin.math.roundToInt

/**
 * DP 값을 PX 값으로 변환하는 확장 함수
 */
fun Context.dpToPx(dp: Int): Int {
    val density = resources.displayMetrics.density
    return (dp * density).roundToInt()
}

/**
 * Float 형태의 DP 변환이 필요할 경우를 위해
 */
fun Context.dpToPx(dp: Float): Float {
    return dp * resources.displayMetrics.density
}