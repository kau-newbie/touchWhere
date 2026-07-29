package com.mytutor.touchwhere.feature.guide

const val DPWIDTH_OF_ANIMATION = 160
const val DPHEIGHT_OF_ANIMATION = 160

abstract class ImageSize {
    abstract val width: Int
    abstract val height: Int
    abstract operator fun div(scale: Int): ImageSize
}

data class AnyImageSize(
    override var width: Int,
    override var height: Int
): ImageSize(){
    override operator fun div(scale: Int): AnyImageSize = copy(
        width = width / scale,
        height = height / scale
    )
}
// 원본 이미지 단위.
data class GuideImageSize(
    override val width: Int = 640,
    override val height: Int= 640
): ImageSize(){
    override operator fun div(scale: Int): GuideImageSize = copy(
        width = width / scale,
        height = height / scale
    )
}

data class OverlayImageSize( // overlay로 띄우는 버튼(icon_bot)사이즈
    override val width: Int = 100,
    override val height: Int= 100,
): ImageSize(){
    override operator fun div(scale: Int): OverlayImageSize = copy(
        width = width / scale,
        height = height / scale
    )
}

data class CentrePoint(
    val x: Int,
    val y: Int
)

data class ImgLayoutTransformed(
    val point : CentrePoint,
    val rot: Float,
    val coordList: List<Int>
)