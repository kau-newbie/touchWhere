package com.mytutor.touchwhere.feature.guide

data class GuideSignature(
    val instruction: String,
    val coordinates: List<Int>,
    val action: String
)