package com.mytutor.touchwhere.feature.guide

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.mytutor.touchwhere.R
import com.mytutor.touchwhere.util.log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.mytutor.touchwhere.util.dpToPx

class AnimationPlayer {

    // 뷰와 파라미터를 멤버 변수로 저장해두고 계속 재사용.
    private var guideView: ImageView? = null
    private var guideParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    // 뷰가 윈도우에 붙어있는지 확인하는 플래그
    private var isViewAdded = false

    /**
     * 초기화 함수: 최초 1회만 실행되어 뷰를 만들고 설정합니다.
     */
     fun initViewIfNeeded(context: Context) {
        if (guideView == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            guideView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE // 처음엔 숨기기.
                // 이 뷰는 접근성(Accessibility) 이벤트로 잡지 x 선언
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val sizeInPx = context.dpToPx(DPWIDTH_OF_ANIMATION)
            //val sizeInPxW = GuideImageSize().width
            guideParams = WindowManager.LayoutParams(
                sizeInPx, sizeInPx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                log("animationplayer 초기화진행시점.")
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = 0
            }
        }
    }
    /**
     * 함수 호출 시점에 context와 windowManager를 받아서 실행
     */
    suspend fun playAnimation(
        context: Context,
        animaIndex: Int,
        x: Int,
        y: Int,
        rotation: Float = 0f
    ) {
        log("playanimation 진입.")
        // 1. 뷰가 없으면 초기화 (최초 1회 필수)
        //initViewIfNeeded(context, windowManager)

        // 취소 체크
        currentCoroutineContext().ensureActive()

        val gifResId = when (animaIndex) {
            0 -> R.drawable.touch
            1 -> R.drawable.swipe_left
            2 -> R.drawable.swipe_right
            3 -> R.drawable.swipe_up
            4 -> R.drawable.swipe_down
            5 -> R.drawable.type
            else -> null
        }

        // 2. 뷰 재사용 로직 (guideView가 null이 아님을 보장받고 실행)
        guideView?.let { view ->
            // (1) 이미지 로딩 (Glide는 비동기지만 명령은 즉시 실행됨)
            if (gifResId != null) {
                try {
                    Glide.with(context)
                        .asGif()
                        .load(gifResId)
                        .into(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // 뷰 회전 설정
            view.rotation = rotation
            guideParams?.apply {
                this.x = x - context.dpToPx(DPWIDTH_OF_ANIMATION) / 2
                this.y = y - context.dpToPx(DPHEIGHT_OF_ANIMATION) / 2
            }

            try {
                currentCoroutineContext().ensureActive()

                if (!isViewAdded) {
                    // 처음 한 번만 addView
                    windowManager?.addView(view, guideParams)
                    isViewAdded = true
                } else {
                    // 이미 붙어있으면 위치만 업데이트
                    windowManager?.updateViewLayout(view, guideParams)
                }

                // 숨겨뒀던 뷰를 보이게 설정
                view.visibility = View.VISIBLE

            } catch (e: Exception) {
                e.printStackTrace()
                // 뷰가 이미 삭제되었거나 하는 예외 상황 대비
                isViewAdded = false
            }
        }
    }

    //  가이드 숨기기 (삭제하지 않음)
    fun removeGuide() {
        // 뷰를 파괴하지 않고 GONE으로 숨겨서 재사용 대기 상태로 만듦
        guideView?.visibility = View.GONE

        // 주의: updateViewLayout을 해줘야 GONE 상태가 윈도우에 반영되어 터치 영역도 사라짐
        /* 일부 기기에서는 visibility 변경만으로 즉시 반영되지만,
           확실하게 하기 위해 updateViewLayout을 호출하거나,
           터치 통과 플래그가 있어서 굳이 안 해도 괜찮을 수 있습니다.
           일단은 visibility = GONE 만으로도 화면에서 사라집니다.
        */
    }

    //  서비스 종료 시(onDestroy) 호출하여 메모리 해제하는 함수
    fun destroy() {
        if (guideView != null && isViewAdded) {
            // 저장해둔 windowManager 사용
            windowManager?.removeView(guideView)
            guideView = null
            isViewAdded = false
            windowManager = null // 참조 해제
        }
    }
}