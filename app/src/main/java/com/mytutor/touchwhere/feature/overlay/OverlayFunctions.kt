package com.mytutor.touchwhere.feature.overlay

import com.mytutor.touchwhere.feature.guide.CentrePoint
import com.mytutor.touchwhere.feature.guide.ImgLayoutTransformed
import com.mytutor.touchwhere.feature.guide.OverlayImageSize
import com.mytutor.touchwhere.feature.guide.AnyImageSize
import com.mytutor.touchwhere.util.dpToPx
import com.mytutor.touchwhere.feature.guide.DPWIDTH_OF_ANIMATION
import com.mytutor.touchwhere.feature.guide.DPHEIGHT_OF_ANIMATION
import com.mytutor.touchwhere.util.log

//screenWidth 와 screenHeight를 안 넣어주려고 확장함수로 만들었습니다.
//애니메이션 피해갈 좌표 구하는 함수, 이미 rotation 계산 이후에 호출됨.
const val OFFSET_FOR_SPACE = 1
internal fun OverlayService.avoidAnimation(baseImg: CentrePoint, avoidingImg: ImgLayoutTransformed, avoidRate: OverlayService.RateOfAvoidance): Pair<Int, Int>{

    var newX= avoidingImg.point.x
    var newY= avoidingImg.point.y
    val rotation = avoidingImg.rot
    val baseX = baseImg.x
    val baseY = baseImg.y
    val guideImgSizeW = dpToPx(DPWIDTH_OF_ANIMATION)//GuideImageSize().width
    val guideImgSizeH = dpToPx(DPHEIGHT_OF_ANIMATION)//GuideImageSize().height
    when(avoidRate){
        OverlayService.RateOfAvoidance.AVOID_OVERLY -> {
            //val overlayImgW = OverlayImageSize().width
            //val overlayImgH = OverlayImageSize().height
            newX = if(baseX < screenWidth/2){
                screenWidth
            } else {
                0
            }
            newY = if(baseY < screenHeight/2){
                screenHeight
            } else 0
        }
        OverlayService.RateOfAvoidance.AVOID_DYNAMICALLY -> {
            log("guideImgSizeW is $guideImgSizeW, guideImgSizeH is $guideImgSizeH\n")
            val coordList = avoidingImg.coordList
            var left = 0
            var top = 0
            var right = 0
            var bottom = 0
            if (coordList.isNotEmpty()) {
                left = coordList[0]
                top = coordList[1]
                right = coordList[2]
                bottom = coordList[3]
            }
            //val viewImgSizeW = (right-left)/2
            //val viewImgSizeH = (bottom-top)/2
            newY = when(rotation) {
                0f -> {
                    if (newY-guideImgSizeH/2 <= bottom)
                        bottom + guideImgSizeH/2 + OFFSET_FOR_SPACE else newY

                }
                180f -> {
                    if (newY+guideImgSizeH/2 >= top)
                        top - guideImgSizeH/2 - OFFSET_FOR_SPACE else newY
                }
                else -> {
                    newY
                }
            }
            newX = when(rotation){
                90f -> {
                    if(newX+guideImgSizeW/2 >= left)
                        left - guideImgSizeW/2 - OFFSET_FOR_SPACE else newX
                }
                -90f -> {
                    if(newX-guideImgSizeW/2 <= right)
                        right + guideImgSizeW/2 + OFFSET_FOR_SPACE else newX
                }
                else -> {
                    newX
                }
            }
        }
    }
    return newX to newY
}

// 좌표 추출, 처리 및 회전 정보 반환
// swipe_up일 때를 대비해서 left, right, top, bottom 도 수정함 --> coordinates list도 반환함.
// 이미지는 언제나 화면 기준 북쪽(0도)을 가리킨다고 가정합니다.
internal fun OverlayService.calculateImgLayout(
    coordinateNumbers:List<Int>,
    actionIndex: Int
): ImgLayoutTransformed{
    //초기화 - x,y는 화면 가운데로 둡니다. [left,top-right,bottom]은 화면 전체를 가리키게 합니다.
    var x = screenWidth/2
    var y = screenHeight/2
    var left = 0
    var top = 0
    var right = screenWidth
    var bottom = screenHeight
    if (coordinateNumbers.isNotEmpty()) {
        left = coordinateNumbers[0]
        top = coordinateNumbers[1]
        right = coordinateNumbers[2]
        bottom = coordinateNumbers[3]
    }
    var rotation = 0f

    if (actionIndex !in 1..2) { // swipe_left나 right이 아닐 때
        if (actionIndex !in 3..4) {  // 일반 touch나 type일 때
            val targetCenterX = (left + right) / 2
            val targetCenterY = (top + bottom) / 2
            x=targetCenterX
            y=targetCenterY

            rotation = calculateRotation(CentrePoint(x,y), AnyImageSize(screenWidth, screenHeight))
            val tmpCentrePoint = avoidAnimation(
                CentrePoint(x, y),
                ImgLayoutTransformed(CentrePoint(x,y), rotation, coordinateNumbers),
                OverlayService.RateOfAvoidance.AVOID_DYNAMICALLY
            )
            x = tmpCentrePoint.first
            y = tmpCentrePoint.second

        } else if (actionIndex == 3) {
            // swipe_up일 때
            left = 0
            top = screenHeight / 2
            right = screenWidth
            bottom = screenHeight
            y *= (3 / 2)
        } else {
            // swipe_down일 때
            left = 0
            top = 0
            right = screenWidth
            bottom = screenHeight / 2
            y += (1 / 2)
        }
    }
    val coords = listOf(left, top, right, bottom)
    return ImgLayoutTransformed(CentrePoint(x,y),rotation,coords)
}

fun OverlayService.calculateRotation(centre: CentrePoint, screen: AnyImageSize): Float{
    val screenW = screen.width
    val screenH = screen.height
    val offset = DPHEIGHT_OF_ANIMATION
    val x = centre.x
    val y = centre.y
    val guideImgHalfW = DPWIDTH_OF_ANIMATION /2
    val guideImgH = DPHEIGHT_OF_ANIMATION/2
    val rotation =
        if(y + 3*offset >= screenH)  180f
        else if(x + guideImgHalfW >= screenW) 90f
        else if(x - guideImgHalfW <= 0)  -90f
        else 0f
    return rotation
}