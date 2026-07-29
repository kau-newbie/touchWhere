package com.mytutor.touchwhere.feature.overlay
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.mytutor.touchwhere.data.repository.AudioRecordRepository
import com.mytutor.touchwhere.feature.guide.AnimationManageable
import com.mytutor.touchwhere.feature.guide.AnimationPlayer
import com.mytutor.touchwhere.feature.guide.GoalGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
//tts기능
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.core.content.edit
import com.mytutor.touchwhere.data.repository.ChatRepository
import com.mytutor.touchwhere.R
import com.mytutor.touchwhere.data.buffer.PromptBuffer
import com.mytutor.touchwhere.data.buffer.UIstateBuffer
//
import com.mytutor.touchwhere.feature.guide.CentrePoint
import com.mytutor.touchwhere.feature.guide.ImgLayoutTransformed
import com.mytutor.touchwhere.util.BubbleStateChangeable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import com.mytutor.touchwhere.util.StringEditor
import com.mytutor.touchwhere.util.dpToPx
import com.mytutor.touchwhere.util.log
import com.mytutor.touchwhere.data.repository.ChatRepositoryTest
//hilt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import javax.inject.Inject

private const val CLOSE_REGION_RADIUS = 350f
private const val INFINITY = Long.MAX_VALUE
@AndroidEntryPoint
class OverlayService : Service(), BubbleStateChangeable, AnimationManageable {

    private enum class RemoveOptions {
        DELETED, VANISHED
    }
    private lateinit var windowManager: WindowManager

    // 메인 로봇 뷰
    private var overlayView: View? = null
    private var overlayImage: ImageView? = null
    // 말풍선 텍스트뷰
    private var messageTextView: TextView? = null
    // 말풍선을 위한 tts 객체
    private lateinit var tts: TextToSpeech
    private lateinit var windowParams: WindowManager.LayoutParams

    // 삭제(X) 버튼 뷰
    private var closeView: View? = null
    private lateinit var closeParams: WindowManager.LayoutParams

    // 화면 크기 (삭제 영역 계산용)
    internal var screenWidth = 0
    internal var screenHeight = 0
// job 객체 변수
    private var ancestorJob: Job? = null
    // 말풍선 자동 숨김 타이머
    private var messageJob: Job? = null
    private var loopJob: Job? = null
    // 깜빡임 애니메이션을 위한 Job
    private var blinkJob: Job? = null
    // 상태 변수
    private var isRecording: Boolean = false

    // Window params

    //좌표 확인용
    private var debugRectView: View? = null
// custom Scope들
    // io 작업을 위한 servicescope
    //private val serviceScopeIO = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // ------------Gemini & Audio & whisper 설정----------------
    private val serviceScopeMain = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    // AudioRecordModule 초기화
    // -------------audio & whisper 초기화 ----- 실례합니다아
    @Inject
    lateinit var audioRecordRepository: AudioRecordRepository
    // GeminiModule 초기화
    @Inject
    lateinit var chatRepository: ChatRepositoryTest
    // 최근목표 저장변수
    private var savedGoal = ""
    companion object{
        private const val KEY_LAST_GOAL = "LAST_GOAL"
    }
    private val animationPlayer = AnimationPlayer()
    // GoalGuide 모듈 초기화
    val goalGuide by lazy {
        GoalGuide(chatRepository, animationManageable = this, this)
    }

    internal enum class RateOfAvoidance{
        AVOID_DYNAMICALLY, AVOID_OVERLY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // 바인딩을 사용하지 않을 경우 null 반환
    }
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate() {
        super.onCreate()
        MyAccessibilityService.isActive.compareAndSet(false,true)
        serviceScopeMain.launch {
            setupForegroundService() // 포그라운드 알림 (함수로 분리함)
            savedGoal = withContext(Dispatchers.IO) { loadLastGoal() ?: ""}
            withContext(Dispatchers.Main) {
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                val displayMetrics = resources.displayMetrics
                screenWidth = displayMetrics.widthPixels
                screenHeight = displayMetrics.heightPixels

                val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

                // -------------------------------------------------------
                // 1. 삭제(X) 버튼 설정 (화면 하단 중앙, 처음엔 숨김)
                // -------------------------------------------------------
                closeView = inflater.inflate(R.layout.overlay_close, null)

                @Suppress("DEPRECATION") val typeParams =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }

                closeParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    typeParams,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                closeParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                closeParams.y = 50
                closeView?.visibility = View.GONE

                try {
                    withContext(Dispatchers.Main) { windowManager.addView(closeView, closeParams) }
                } catch (e: Exception) {
                    log("$e addView 실패.")
                }

                //tts 객체 초기화
                tts = TextToSpeech(this@OverlayService) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts.setLanguage(Locale.KOREAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            log("이 언어는 지원되지 않습니다.")
                        }
                    } else {
                        log("초기화 실패")
                    }
                }

                // -------------------------------------------------------
                // 2. 메인 가이드(오버레이)버튼 뷰 설정
                // -------------------------------------------------------
                overlayView = inflater.inflate(R.layout.overlay_view, null)
                overlayImage = overlayView?.findViewById(R.id.overlay_icon)
                overlayImage?.alpha = 0.5f
                messageTextView = overlayView?.findViewById(R.id.tv_message)

                // 버튼 뷰 찾기
                val layoutButtons = overlayView?.findViewById<View>(R.id.layout_resume_buttons)
                val btnResumeYes = overlayView?.findViewById<View>(R.id.btn_resume_yes)
                val btnResumeNo = overlayView?.findViewById<View>(R.id.btn_resume_no)

                // "이어서 할래" 버튼 눌렀을 때
                btnResumeYes?.setOnClickListener {
                    layoutButtons?.visibility = View.GONE
                    setupTouchListener()
                    ancestorJob?.cancel()
                    ancestorJob = serviceScopeMain.launch { resumeSavedGoal() }
                }

                // "새로 할래" 버튼 눌렀을 때
                btnResumeNo?.setOnClickListener {
                    layoutButtons?.visibility = View.GONE
                    setupTouchListener()
                    showSpeechBubble("", 0)
                    serviceScopeMain.launch {
                        ancestorJob?.cancelAndJoin()
                        clearLastGoal()
                        PromptBuffer.clearAll()
                    }
                }

                windowParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    typeParams,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                windowParams.gravity = Gravity.TOP or Gravity.START
                windowParams.x = 50
                windowParams.y = 300

                try {
                    windowManager.addView(overlayView, windowParams)
                    animationPlayer.initViewIfNeeded(this@OverlayService)
                } catch (e: Exception) {
                    log("View add failed: ${e.message}")
                }
                if (savedGoal.isNotEmpty()) {
                    PromptBuffer.clearAll()
                    PromptBuffer.setTheGoal(savedGoal)
                    showSpeechBubble("이전에 하시던 작업이 있어요!\n'$savedGoal'\n이어서 안내를 받을까요?", INFINITY)
                    layoutButtons?.visibility = View.VISIBLE
                    updateOverlayLayout(overlayView!!, windowParams)
                } else {
                    // 저장된 목표가 없으면 즉시 활성화
                    setupTouchListener()
                }
            }
        }
    }
    // onCreate() 끝.----------
    // 빨간색 점멸 효과 함수
    private fun startBlinkRedEffect() {
        blinkJob?.cancel()
        blinkJob = serviceScopeMain.launch {
            // 기존에 로봇을 붉게 만들던 필터는 완전히 제거합니다.
            overlayImage?.clearColorFilter()

            while (true) {
                // 1. 녹음 중 아이콘 (배경 코랄색)으로 변경
                overlayImage?.setImageResource(R.drawable.ic_overlay_robot_recording)
                delay(500)

                // 2. 평상시 아이콘 (배경 크림색)으로 복구
                overlayImage?.setImageResource(R.drawable.ic_overlay_robot)
                delay(500)
            }
        }
    }

    private fun stopBlinkRedEffect() {
        blinkJob?.cancel()
        serviceScopeMain.launch {
            overlayImage?.clearColorFilter()
            // 애니메이션이 끝나면 평상시 아이콘으로 확실하게 원상복구
            overlayImage?.setImageResource(R.drawable.ic_overlay_robot)
        }
    }

    // 말풍선 띄우기 함수
    private fun showSpeechBubble(message: String, duration: Long = 3000L) {
        // 1. 기존 타이머 취소
        messageJob?.cancel()
        if (duration <=0) {
            messageTextView?.text = ""
            messageTextView?.visibility = View.GONE
            updateOverlayLayout(overlayView!!, windowParams)
            return
        }
        messageJob = serviceScopeMain.launch(Dispatchers.Main) {
            // 2. 텍스트 설정 및 보이기 & tts 재생
            try {
                messageTextView?.text = message
                messageTextView?.visibility = View.VISIBLE
                if (!message.contains("듣고 있어요... (5초)")) {
                    speakBubbleText(message)
                }
                // 3. 뷰 크기 갱신 (말풍선 때문에 크기 변함)
                updateOverlayLayout(overlayView!!, windowParams)

                // 4. 자동 숨김 (duration이 양수일 때만)
                delay(duration)
                messageTextView?.visibility = View.GONE
                try {
                    updateOverlayLayout(overlayView!!, windowParams)
                } catch (e: Exception) {
                    log("error ${e}: ...")
                }

            } catch (e: CancellationException) {
                // 취소되었을 때의 처리 (필요시)
                log("메시지 표시가 중단되었습니다.(새 버블 생성)$e")
            } catch (e: Exception) {
                log("오류 발생: ${e.message}")
            }
        }
    }
    // 화면 가장자리 붙이기 (자석 효과)
    private fun snapToEdge(currentRawX: Float) {
        val middle = screenWidth / 2
        if (currentRawX > middle) {
            windowParams.x = screenWidth - (overlayView?.width ?: 0)
        } else {
            windowParams.x = 0
        }
        updateOverlayLayout(overlayView!!, windowParams)
    }

    // 삭제 영역(화면 하단 중앙)인지 판별하는 함수
    private fun isOverCloseRegion(x: Float, y: Float): Boolean {
        val centerX = screenWidth / 2

        // 좌우 범위를 350으로, 높이를 350으로
        val horizontalMargin = CLOSE_REGION_RADIUS
        val verticalThreshold = screenHeight - CLOSE_REGION_RADIUS

        return (x > (centerX - horizontalMargin) && x < (centerX + horizontalMargin))
                && (y > verticalThreshold)
    }
    private fun setupTouchListener() {
        val touchSlop = ViewConfiguration.get(applicationContext).scaledTouchSlop
        overlayImage?.alpha = 1.0f
        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = windowParams.x
                        initialY = windowParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false

                        //  터치 시작하면 '삭제 버튼' 보여주기
                        closeView?.visibility = View.VISIBLE
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY

                        if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                            isDragging = true
                            // 드래그 중에는 말풍선 잠시 숨김 구현할수도.
                            // messageTextView?.visibility = View.GONE
                            windowParams.x = initialX + deltaX.toInt() // (Gravity 방향에 따라 +/- 조정 필요)
                            windowParams.y = initialY + deltaY.toInt() // Gravity.BOTTOM 기준이면 -deltaY가 맞을 수도 있음


                            updateOverlayLayout(overlayView!!, windowParams)

                            // 삭제 영역 위에 있는지 확인해서 'X' 버튼 키우기
                            if (isOverCloseRegion(event.rawX, event.rawY)) {
                                closeView?.scaleX = 2.0f
                                closeView?.scaleY = 2.0f
                            } else {
                                closeView?.scaleX = 1.0f
                                closeView?.scaleY = 1.0f
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v?.performClick()
                        //  손을 뗐을 때 삭제 영역 위라면 -> 서비스 종료
                        if (isOverCloseRegion(event.rawX, event.rawY)) {
                            // 목표 인식과 동시에 재부팅시 재시작할수 있도록 메모장에 목표를 저장
                            // 해당 목표는 '목표인식'이 인식될 때까지 계속 저장되어 있음. 인식되면 clear
                            serviceScopeMain.launch {
                                withContext(Dispatchers.IO){saveLastGoal(PromptBuffer.getTheGoal())}
                                removeDebugRect(RemoveOptions.DELETED)
                                stopSelf() // 서비스 종료 (onDestroy 호출됨)
                            }
                        }
                        else if (!isDragging) {
                            // 드래그가 아니면 -> 클릭 기능 (녹음)
                            ancestorJob?.cancel()
                            ancestorJob = serviceScopeMain.launch {startRecordingAndAskLLM()}
                        }else {
                            // 드래그 종료 시 스냅
                            snapToEdge(event.rawX)
                        }

                        // 삭제 버튼 다시 숨기기
                        closeView?.visibility = View.GONE
                        closeView?.scaleX = 1.0f
                        closeView?.scaleY = 1.0f

                        return true
                    }
                }
                return false
            }
        })
    }
    // 목표를 로컬- 일종의 메모장에 적는 함수 (마이크 인식 성공 시 호출)
    //shared preference라고 합니다. 최신 jetpack에선 datastore를 권장합니다....
    // MODE_PRIVATE flag: 이 앱만 사용가능
    private suspend fun saveLastGoal(goal: String) {
        withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences("TutorPrefs", MODE_PRIVATE)
            prefs.edit { putString(KEY_LAST_GOAL, goal) } // key(=p0) - value(=p1) 쌍 입니다.
        }
    }

    // 메모장을 읽어보는 함수 (앱 켜질 때 호출)
    private suspend fun loadLastGoal(): String? {
        val prefs = withContext(Dispatchers.IO) {
             getSharedPreferences("TutorPrefs", MODE_PRIVATE)
        }
        return prefs.getString(KEY_LAST_GOAL, null)  // 여기서 p1은 value(key)가 없을 때 반환값
    }

    // 메모장을 찢어버리는 함수 (목표 달성하거나, 새로 시작할 때 호출)
    private suspend fun clearLastGoal() {
        withContext(Dispatchers.IO) {
            val prefs = getSharedPreferences("TutorPrefs", MODE_PRIVATE)
            prefs.edit { remove(KEY_LAST_GOAL) }
        }
    }
    // --- 기능 로직  ---
    private suspend fun startRecordingAndAskLLM() = coroutineScope {
        //안전하게 혹시라도 버튼 눌리는 경우 앞선 job은 취소.
        loopJob?.cancelAndJoin()
        /*
          initialization: 버튼을 누르면 오디오 녹음 작업, 기존 애니메이션, 그리고 목표달성까지 loop를 도는 job을 중지 및 초기화.
        */
        //audio도 초기화
        if(isRecording) showSpeechBubble("이전 녹음을 취소 중 입니다...")
        audioRecordRepository.cleanUpRecordSetting()
        UIstateBuffer.isMicUsed.set(false) // goalGuide 들어가있는 스레드가 buildPrompt하지 않도록.
        UIstateBuffer.isTouched.set(false)
        //animation도 삭제
        animationPlayer.removeGuide()
        goalGuide.responseText=""
        //removeGuide(windowManager)
        //promopt의 race condition을 막기 위해선 애초에 prompt 초기화도 필요.
        chatRepository.clearHistory()
        PromptBuffer.clearAll()
        // 아이콘 빨간색 점멸 시작
        startBlinkRedEffect()
        // 말풍선 표시 (5초)
        showSpeechBubble("듣고 있어요... (5초)", 5000L)
        try {
            try{audioRecordRepository.startRecordingAndUpload()}
            catch (e: Exception){
                log("음성 인식 중 오류 : $e")
                showSpeechBubble("음성 인식 중 오류 발생. 관리자에게 문의하세요.")
                return@coroutineScope
            }
            log("startRecordingAndUpload() 완료.")
            showSpeechBubble("분석 중입니다...", INFINITY)
            val startTime = System.currentTimeMillis()
            val result = chatRepository.analyzeTextOnly(PromptBuffer.getFinalPrompt())
            log("\n첫 번째 LLM 응답(마이크녹음 직후):\n${result}")
            val timeTaken = System.currentTimeMillis() - startTime
            val timeInSeconds = timeTaken / 1000.0
            log("=========================================")
            log(" GPT LLM 응답 소요 시간(Latency): ${timeTaken}ms ($timeInSeconds 초)")
            log("=========================================")
            val finalResText = StringEditor.makeCompleteResponse(result)
            // 일단 한 번은 애니메이션 응답을 받아야 함.
            withContext(Dispatchers.Main){
                preprocessResponse(finalResText)   // preprocessResponse를 suspend로.
            }
            UIstateBuffer.isMicUsed.set(true)
            // 이제 루프 시작 (목표달성 때까지)
            fallInLoop()
        } catch (e: IOException) {
            log("\n내부오류 : err msg: $e")
            e.cause?.let {
                log("LLMError: 구체적인 원인: ${it.message}")
                it.printStackTrace()
            }
            showSpeechBubble("오류: ${e.message} 관리자에게 문의하세요.")
        } finally {
            //_root_ide_package_.com.mytutor.kakaotalktutorviews.data.buffer.UIstateBuffer.isTouched=false // <-- goalGuide에서 isTouched가 ture여야 loop가 돌아가는데, loopjob은 별도의 CoroutineScope여서 finally로 바로 올 거에요..
            //다시 버튼 살리기.
            resetRecordingButton()
        }
    }
    private suspend fun resumeSavedGoal() = coroutineScope {
        loopJob?.cancelAndJoin()

        UIstateBuffer.isMicUsed.set(false)
        UIstateBuffer.isTouched.set(false)
        //animationPlayer.removeGuide(windowManager)
        goalGuide.responseText = ""
        chatRepository.clearHistory()
        startBlinkRedEffect()
        showSpeechBubble("이전 작업을 이어서 분석할게요...", 2000L)
        try {
            //delay(500L)
            //val savedGoal = loadLastGoal() ?: ""
            PromptBuffer.clearAll()
            PromptBuffer.setTheGoal(savedGoal)
            PromptBuffer.buildFinalPrompt()
            val result = chatRepository.analyzeTextOnly(PromptBuffer.getFinalPrompt())
            log("\n첫 번째 제미나이 응답(불러오기 직후):\n${result}")
            val finalResText = StringEditor.makeCompleteResponse(result)
            withContext(Dispatchers.Main){
                preprocessResponse(finalResText)
            }
            UIstateBuffer.isMicUsed.set(true)
            fallInLoop()
        } catch (e: Exception) {
            showSpeechBubble("오류: ${e.message}")
        } finally {
            resetRecordingButton()
        }
    }

    private fun resetRecordingButton() {
        isRecording = false
        // 점멸 애니메이션 중지 및 원상복구
        stopBlinkRedEffect()
    }

    private fun setupForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("overlay_ch", "Overlay", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            val noti = Notification.Builder(this, "overlay_ch")
                .setContentTitle("튜터 실행 중").setSmallIcon(R.mipmap.ic_launcher).build()
            startForeground(1, noti)
        }
    }

    // 좌표 테스트용
    private fun drawDebugRect(left: Int, top: Int, right: Int, bottom: Int, offset: Int) {
        if (debugRectView == null) {
            debugRectView = View(this).apply {
                setBackgroundResource(R.drawable.border)
            }
        }

        val width = right - left + 2 * dpToPx(4)
        //if(width>screenWidth) screenWidth - 2 * dpToPx(4)
        val height = bottom - top + 2 * dpToPx(4)
        //if(height>screenHeight) screenHeight - 2 * dpToPx(4)

        // 좌표가 이상하면(음수거나 0이면) 그리지 않음
        if (width <= 0 || height <= 0) return

        @Suppress("DEPRECATION") val params = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x =
            if (left - offset < 0) 0
            else left - offset
        params.y =
            if(top - offset < 0) 0
            else top - offset

        if(left + width > screenWidth) params.width -= 2 * dpToPx(4)
        if(top + height > screenHeight) params.height -= 2 * dpToPx(4)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (debugRectView?.parent != null) {
                    updateOverlayLayout(debugRectView!!, params)
                    debugRectView?.visibility = View.VISIBLE
                }
                else windowManager.addView(debugRectView, params)
            } catch (e: Exception) {
                log("drawing Debug Rect Error: ${e.message}")
            }
        }
    }
    private fun removeDebugRect(options: RemoveOptions) {
        val view = debugRectView ?: return
        if (view.parent == null) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                when (options) {
                    RemoveOptions.DELETED -> {
                        windowManager.removeView(view)
                        debugRectView = null
                    }
                    RemoveOptions.VANISHED -> {
                        view.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                log("Debug Rect Remove Error (${options.name}): ${e.message}")
            }
        }
    }
    // tts 재생 함수
    fun speakBubbleText(text: String) {
        // QUEUE_FLUSH: 이전 음성이 나오고 있다면 즉시 중단하고 새 텍스트 재생
        // QUEUE_ADD: 이전 음성이 끝난 뒤 이어서 재생
        if(tts.isSpeaking){tts.stop()}
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bubble_id")
    }

    //activity의 주기에 따른 함수가 있대요.
    // 그 중, activity가 파괴될 때 실행되는 함수인데, override로 cleanup...() 호출까지 기능을 추가 해준 거에요.
    override fun onDestroy() {
        //일단 접근성서비스먼저 종료 브로드캐스트를 하고,
        /**val intentToAcc = Intent("ACTION_TERMINATE_ACCESSIBILITY")
        intentToAcc.setPackage(packageName) // 보안을 위해 내 앱에만 발송
        sendBroadcast(intentToAcc)**/
        MyAccessibilityService.isActive.set(false)
        removeDebugRect(RemoveOptions.DELETED)
        audioRecordRepository.cleanUpRecordSetting() // 후처리까지.
        UIstateBuffer.isMicUsed.set(false)
        UIstateBuffer.isTouched.set(false)
        goalGuide.isGoalAchived("목표달성!") //강제종료
        //tts 객체 자원반환
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        // 현빈님 onDestroy 내용
        if (overlayView != null) windowManager.removeView(overlayView)
        animationPlayer.destroy()
        // 포그라운드 상태를 해제하고 알림을 제거함
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScopeMain.cancel()
        super.onDestroy()
    }
    // --- AudioRecordManager 구현 (Toast 대신 말풍선 사용) ---
    override fun onStateChange(message: String) {
        // 상태 변화는 로그로만 찍거나 필요하면 말풍선 사용
        //log("State: $message")
        showSpeechBubble(message)
    }

    override fun onResult(text: String) {
        //statusTextView.text = "인식 결과: $text"
        //Toast.makeText(this@OverlayService, "인식 결과: $text", Toast.LENGTH_SHORT).show()
        log("인식됨: $text")
    }

    override fun onError(message: String) {
        // statusTextView.text = message
        //Toast.makeText(this@OverlayService, message, Toast.LENGTH_SHORT).show()
        showSpeechBubble("오류: $message")
    }
    // 레이아웃 업데이트 공통 함수
    private fun updateOverlayLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            if (overlayView?.isAttachedToWindow == true) {
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            log("레이아웃 갱신 실패: ${e.message}")
        }
    }

    // Animation을 위한 override 함수들 (implements of guide interface)
    override suspend fun preprocessResponse(resText: String?) {
        if (resText == null) return
        if (resText.contains("목표달성")) {
            withContext(Dispatchers.Main + NonCancellable) {
                UIstateBuffer.isMicUsed.set(false)

                val aiMessage = StringEditor.extractTargetLine(resText, "목표달성!")
                val finalMessage = if (aiMessage.isNotBlank()) {
                    "목표달성! ${aiMessage.trim()}"  // <-- 이렇게 추가
                } else {
                    "목표달성! 성공했어요. 더 궁금한 게 있다면 저를 눌러주세요."
                }
                // 목표 달성시 저장하던 지난 목표 지우기
                clearLastGoal()
                PromptBuffer.clearAll()

                // 루프 정지
                loopJob?.cancel()
                loopJob = null

                // 말풍선 띄우기
                showSpeechBubble(finalMessage, 5000L)
            }
            return
        }else {
            // 1. 좌표 정보 파싱
            val (instructionText, coordinateNumbers, guidedAction) = StringEditor.extractSignature(
                resText
            )
            // 2. 행동 찾기
            val actionIndex = when {
                guidedAction.contains("touch") -> 0
                guidedAction.contains("swipe_left") -> 1
                guidedAction.contains("swipe_right") -> 2
                guidedAction.contains("swipe_up") -> 3
                guidedAction.contains("swipe_down") -> 4
                guidedAction.contains("type") -> 5
                else -> -1
            }
            log("\nactionIndex가 $actionIndex 로 설정됨.\n")
            //공통

            val imgLayoutCalculated = calculateImgLayout(coordinateNumbers,actionIndex) //여기서 가이드 애니메이션 좌표를 보정해서 반환함.
            val x = imgLayoutCalculated.point.x //결과적으로 x, y는 가이드 애니메이션의 좌표임.
            val y = imgLayoutCalculated.point.y
            val rotation = imgLayoutCalculated.rot
            val left = imgLayoutCalculated.coordList[0]
            val top = imgLayoutCalculated.coordList[1]
            val right = imgLayoutCalculated.coordList[2]
            val bottom = imgLayoutCalculated.coordList[3]
            log("테두리 좌표: $left, $top, $right, $bottom\nrotation: $rotation")
            //좌표 테스트용 (빨간 테두리 그리기)
            if(actionIndex == 0 || actionIndex ==5)
                drawDebugRect(left, top, right, bottom, dpToPx(4))
            else
                drawDebugRect(left, top, right, bottom, 0)
            // 3. 애니메이션 실행 (UI 스레드에서)
            if (actionIndex != -1) {                //handler쓰는 방식에서 withContext썼습니다. withContext 라던가,
                // suspend 함수에서는 항상 멈춰서 코루틴이 지금 중지해야하는지 확인한답니다.
                //loopJob.cancel에서 이 시점이었다면 애니메이션 생성을 막을 수 있어요.
                //playAnimation 호출 (중앙값 x, y 전달)
                withContext(Dispatchers.Main) {
                    // speechbubble 띄우고 애니메이션 실행
                    showSpeechBubble(instructionText, 20000L)
                    //지시사항set 기록에 추가
                    PromptBuffer.addFootprint("$instructionText\t$coordinateNumbers\t$guidedAction") //지시사항, ui 요소 좌표, 행동
                    //animation좌표보고 아이콘치우기 & 목표 좌표 자체도 피해야 함. -> 목표 좌표를 피하도록 수정.
                    val (tempX, tempY) = if(actionIndex==0 || actionIndex ==5){((left+right)/2) to ((top+bottom)/2)}else x to y
                    val (overlayX,overlayY) = avoidAnimation(CentrePoint(tempX, tempY),
                        ImgLayoutTransformed(CentrePoint(windowParams.x, windowParams.y),0f,coordinateNumbers),
                        RateOfAvoidance.AVOID_OVERLY)
                    log("avoid_dynamically결과: $x, $y")
                    log("avoid_overly결과: $tempX, $tempY")
                    windowParams.x = overlayX
                    windowParams.y = overlayY
                    windowManager.updateViewLayout(overlayView, windowParams)
                    animationPlayer.playAnimation(
                        context = this@OverlayService,
                        animaIndex = actionIndex,
                        x = x,
                        y = y,
                        rotation = rotation // 회전값 전달
                    )

                    //40초 동안 사용자의 터치가 없다면, 혹은 사용자가 터치한다면
                    withTimeoutOrNull(40000L) {
                        UIstateBuffer.uiStateFlow.first { UIstateBuffer.isTouched.compareAndSet(true, true) }
                    }
                    log("playAnimation 종료시점.")
                    //넘어가서 실행..
                    animationPlayer.removeGuide()
                    removeDebugRect(RemoveOptions.VANISHED)
                    showSpeechBubble("", 0) //speechBubble도 지움.
                }
            } else {
                showSpeechBubble("정의되지 않은 동작을 전달받았습니다.")
                log("실행할 동작을 찾지 못했습니다.")
            }
        }
    }

    fun fallInLoop(){
        loopJob = serviceScopeMain.launch {
            goalGuide.processEvent()
        }
    }
}