package com.mytutor.touchwhere.feature.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mytutor.touchwhere.data.buffer.UIstateBuffer
import com.mytutor.touchwhere.util.log
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import android.os.Looper
private data class EventContext(
    val event: AccessibilityEvent,
    val pkg: String,
    val node: AccessibilityNodeInfo,
    val rootNode : AccessibilityNodeInfo,
    val nodeClass: CharSequence,
    val nodeName: CharSequence,
)
//안드로이드 api 버전별 코드 참조: https://developer.android.com/reference/android/os/Build.VERSION_CODES
private const val DEBOUNCE_DELAY = 800L
class MyAccessibilityService : AccessibilityService() {
    // companion object 안에 넣으면 '클래스명.변수명'으로 바로 접근 가능합니다.
    companion object {
        var isActive: AtomicBoolean = AtomicBoolean(false)
        var isUiChanged: AtomicBoolean = AtomicBoolean(false)
    }
    //counter
    private var lineNum:Int =0
    //patterns
    val appDrawerPattern = """Apps|scroll|recycler|applist|container|options""".toRegex(RegexOption.IGNORE_CASE)
    //val activityPattern = """Activity""".toRegex(RegexOption.IGNORE_CASE)
    val hasPagesPattern = """\d+\s*page|\d+\s*페이지|\d+\s*쪽""".toRegex(RegexOption.IGNORE_CASE)
    val adsPattern = """(?<=^|_)ad(?=$|_)|(?<=^|_)banner(?=$|_)""".toRegex(RegexOption.IGNORE_CASE)
    val layOutPattern = """layout|ViewGroup""".toRegex(RegexOption.IGNORE_CASE)
    val internetPattern = """webview|webx|googlequicksearchbox""".toRegex(RegexOption.IGNORE_CASE)
    val clickablePattern = """button|search|edittext|thumbnail""".toRegex(RegexOption.IGNORE_CASE)
    val stateOnOffPattern = """state.?(on|off)""".toRegex(RegexOption.IGNORE_CASE)
    val textBoxPattern = """textview|edit.text|text.edit""".toRegex(RegexOption.IGNORE_CASE)
    //var init
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCheckRunnable: Runnable? = null
    //the system calls back this method(onAccess...) when it detects an AccessibilityEvent that matches the event filtering parameters specified by your accessibility service
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { //AccessibilityEvent라는 객체를 사용자의 조작마다 안드로이드가 생성해서 반환.
        if (isActive.compareAndSet(false, false)) return
        event ?: return
        val pkg = event.packageName?.toString() ?: ""
        val node = event.source ?: return
        val rootNode = rootInActiveWindow ?: return
        val nodeClass = node.className ?: "noClass"
        val nodeName = node.viewIdResourceName ?: "noId"
        // var init
        val context = EventContext(
            event= event,
            pkg= event.packageName?.toString() ?: "",
            node= node,
            rootNode= rootNode,
            nodeClass= nodeClass,
            nodeName= nodeName
        )
        // 앱에서 나온 이벤트인지 필터링
        if (pkg == this.packageName) { // 이 앱에서 발생하는 event면 return
            return
        }
        // 광고로 인한 이벤트인지 필터링
        if (adsPattern.containsMatchIn(event.source?.viewIdResourceName ?: "") && (event.className
                ?: "").contains("imageView")
        ) {
            return
        }
        if (nodeName == "com.android.systemui:id/mobile_combo" &&
            nodeClass == "android.widget.FrameLayout"
        ) return
        if (nodeName == "com.android.systemui:id/clock" &&
            nodeClass == "android.widget.TextView"
        ) return
        processLatestState(context)
    }
    private fun processLatestState(context: EventContext) {
        //var init
        val(event,pkg,node,rootNode,nodeClass,nodeName) = context
        /*
           1. 이제 사용자 터치 조작 감지
       */
        val targetWindow = node.window ?: "no_window"
        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                UIstateBuffer.isTouched.set(true)
                log("\n사용자 조작 감지: class=${event.className ?: "no class name"} \nisTouched: ${UIstateBuffer.isTouched}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
            }
            // flag=true를 위한 이벤트 필터링 시작.
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (clickablePattern.containsMatchIn(nodeName) ||
                    clickablePattern.containsMatchIn(nodeClass) ||
                    internetPattern.containsMatchIn(nodeName) ||
                    internetPattern.containsMatchIn(nodeClass) ) {
                    log("클릭감지(button): class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                    isUiChanged.set(true)
                } else if (textBoxPattern.containsMatchIn(nodeName) ||
                    textBoxPattern.containsMatchIn(nodeClass)){
                        if(node.isEditable){
                            log("클릭감지(editbox클릭): class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                            UIstateBuffer.isTouched.set(true)
                            isUiChanged.set(true)
                        }
                } else if (layOutPattern.containsMatchIn(nodeClass) &&
                    nodeName.contains("dialer", ignoreCase = true)) {
                    log("클릭감지(button): class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                    isUiChanged.set(true)
                } else {
                    log("클릭감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                }
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                UIstateBuffer.isTouched.set(true)
                log("\n사용자 조작 감지: class=${event.className ?: "no class name"} \nisTouched: ${UIstateBuffer.isTouched}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
            }
            /*
           1 번의 예외 - 조작을 하지 않아도 화면 상태 변경 & 이 변경이 중요할 경우
           && 2. UiState 업데이트.
        */
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                log("TYPE_WINDOW_STATE_CHANGED감지: class=${event.className ?: "no class name"}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                UIstateBuffer.isTouched.set(true)
                isUiChanged.set(true)
            } // window_state_changed와 다른, 멀티창
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val changes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    event.windowChanges
                } else {
                    TODO("VERSION.SDK_INT < P")
                }
                when {
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_ADDED) != 0 -> {
                        log("test: 새로운 창 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0 -> {
                        log("test: 사라진 창 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_BOUNDS) != 0 -> {
                        // 💡 창의 크기나 위치가 변했을 때 (예: 팝업이 커지거나, 퀵 뷰 위치 이동)
                        log("test: 창 크기나 위치 변경 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_ACTIVE) != 0 -> {
                        // 💡 다른 창으로 제어권(Active)이 넘어갔을 때
                        log("test: 활성 창 변경 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_FOCUSED) != 0 -> {
                        log("test: 포커싱 창 변경 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_LAYER) != 0 -> {
                        log("test: z-ordering 변경 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_PIP) != 0 -> {
                        log("test: 포커싱 창 변경 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                    (changes and AccessibilityEvent.WINDOWS_CHANGE_TITLE) != 0 -> {
                        log("test: window타이틀 변경 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                }
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                log("test: 새로운 창 감지 ${targetWindow}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                UIstateBuffer.isTouched.set(true)
                isUiChanged.set(true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val changeTypes = event.contentChangeTypes
                // 아래는 홈 화면(런처) 행동 이벤트 감지 로직
                if (pkg.contains("home", ignoreCase = true) ||
                    pkg.contains("launcher", ignoreCase = true)) {
                    // 홈 화면에서 앱드로어 열기 - id내용 기준으로
                    if (appDrawerPattern.containsMatchIn(
                            event.source?.viewIdResourceName ?: ""
                    ) ||
                        appDrawerPattern.containsMatchIn(event.className ?: "")) {
                        log("홈 화면 앱드로어 id 감지: class=${event.className ?: "no class name"}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                } else if (hasPagesPattern.containsMatchIn(getLabelOfNode(node)) &&
                    layOutPattern.containsMatchIn(event.className ?: "no class name")) {
                    log("새로운 페이지 이동감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                    isUiChanged.set(true)
                } else if (node.isSelected) {
                    log("selecting 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                    isUiChanged.set(true)
                } else if (node.isCheckable) {
                    @Suppress("DEPRECATION") val checkedState =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            node.checked
                        } else {
                            if (node.isChecked) 1 else 0
                        }
                    if (checkedState != 0) {
                        log("checking 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    }
                } else if (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_EXPANDED != 0) {
                    log("ui 확장 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                    isUiChanged.set(true)
                } else if (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION != 0) {
                    log("ContentDescription 변경 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                    isUiChanged.set(true)
                } else if (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION != 0) {
                    log("stateDescription 변경 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    UIstateBuffer.isTouched.set(true)
                    isUiChanged.set(true)
                } else if (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUPPLEMENTAL_DESCRIPTION != 0) {
                    log("supplementDescription 변경 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                    isUiChanged.set(true)
                } else {
                    if (stateOnOffPattern.containsMatchIn(nodeName)) {
                        log("state 변경 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                        UIstateBuffer.isTouched.set(true)
                        isUiChanged.set(true)
                    } else {
                        log(
                            "test용 - content_changed 감지\nclass=${event.className}\n" +
                                    "view: ${event.source?.viewIdResourceName ?: "ID가 없음."}\""
                        )
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                log("테스트용 - view scrolled 이때 감지")
            }
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                log("AdaptorView에서 Selecting 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                UIstateBuffer.isTouched.set(true)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                log("writing 감지: class=${event.className}\nview: ${event.source?.viewIdResourceName ?: "ID가 없음."}")
                UIstateBuffer.isTouched.set(true)
                isUiChanged.set(true)
            }
            else -> return
        }
        if (isUiChanged.compareAndSet(true, false)) {
            val currentRunnable = object : Runnable {
                override fun run() {
                    if (pendingCheckRunnable !== this) {
                        log("return됐습니다.")
                        return
                    }
                    sendLatestState(context)
                    pendingCheckRunnable = null
                }
            }
            pendingCheckRunnable = currentRunnable
            handler.postDelayed(currentRunnable, DEBOUNCE_DELAY)
        }
    }
    private fun sendLatestState(context: EventContext){
        //var init
        val event = context.event
        val pkg = context.pkg
        val rootNode = context.rootNode
        /*
            2. isUiChanged라면, UiState를 업데이트.
         */
        log("sendLatestState에서 UiChanged 통과.")
        val logBuilder = StringBuilder() //java String은 기본적으로 불변. 매번 reference가 새로운 메모리 공간을 가리키게 하므로, 효율을 위해 가변(mutable) 메모리 공간을 할당.
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(
            Date()
        )
        log("An event from $pkg")
        logBuilder.append("--- screen ui Snapshot ---\n") // StringBuilder 사용법은 이렇게 .append, .insert, .delete 하고 맨 마지막에 toString으로.
        traverseNode(rootNode, logBuilder)
        // 1. 방금 만든 새 로그 스냅샷(문자열)을 가져옴.
        val newLogEntry = logBuilder.toString()
        // 2. 새 리스트로 _uiStateFlow 업데이트.
        UIstateBuffer.updateUIState(newLogEntry)
        lineNum =0
        log("lineNum 초기화 완료.")
        log("${currentTime}\nui 스냅샷 UIStateBuffer로 전송완료( \nisTouched:${UIstateBuffer.isTouched}, isMicUsed:${UIstateBuffer.isMicUsed}\n" +
                "event 발생 view: ${event.source?.viewIdResourceName?: "ID가 없음."}\n" +
                "event 발생 class: ${event.className?:"no class name"}\n"+
                "event type: ${event.eventType}")
    }
    // UI 트리를 순회하는 재귀 함수
    private fun traverseNode(nodeInfo: AccessibilityNodeInfo?, logBuilder: StringBuilder, depth: Int =0) {
        nodeInfo ?: return
        // text, description 둘 중 하나라도 있다면 대표 이름(Label)으로 사용
        val label = getLabelOfNode(nodeInfo)
        // 이 앱 이름이면 return
        if(label.contains("어디 눌러")) return
        val isMeaningful = testMeaningful(label,nodeInfo)
        val isActionable = testActionable(nodeInfo)
        // 단순 layout이면 적지 않음.
        val isSimpleContainer = !isMeaningful &&
                (layOutPattern.containsMatchIn(nodeInfo.className?:"")) || (!isActionable && nodeInfo.childCount > 0)

        // 2. 필터링: 글자가 있거나, 글자를 입력할 수 있는 칸(Editable)이거나, 누를 수 있거나 등등의 경우만 필터링해서 넘김
        if (nodeInfo.isVisibleToUser && (isMeaningful || isActionable) && !isSimpleContainer) { //
            val bounds = Rect()
            nodeInfo.getBoundsInScreen(bounds)

            // 3. 상태 정보 수집
            val states = mutableListOf<String>()

            if(!layOutPattern.containsMatchIn(nodeInfo.className?:"")) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (nodeInfo.isClickable ||
                        nodeInfo.isTextSelectable) states.add("(Clickable)")
                } else {
                    if (nodeInfo.isClickable) states.add("(Clickable)")
                }
                if (nodeInfo.isScrollable) states.add("(Scrollable)")
                if (nodeInfo.isEditable) states.add("(Editable)")
                if (nodeInfo.isSelected) states.add("(Selected)")
                if (!nodeInfo.isEnabled) states.add("(this view is now disable)")
                if (nodeInfo.isCheckable) {
                    states.add("(Checkable:")
                    @Suppress("DEPRECATION") val checkedState =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            nodeInfo.checked
                        } else {
                            if (nodeInfo.isChecked) 1 else 0
                        }
                    when (checkedState) {
                        AccessibilityNodeInfo.CHECKED_STATE_FALSE -> states.add("Not checked)")
                        AccessibilityNodeInfo.CHECKED_STATE_TRUE -> states.add("Checked)")         // CHECKED
                        AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> states.add("Partially Checked)") // PARTIAL
                    }
                }
            }
            val stateString = if (states.isNotEmpty()) "${states.joinToString(",")} " else ""
            val resourceId = nodeInfo.viewIdResourceName ?: "No ID"
            val className = nodeInfo.className ?: "No Class"
            val descriptions = getDescriptionOfNode(nodeInfo)
            // 4. 한 줄로 합침
            val indent = "  ".repeat(depth)
            lineNum += 1
            logBuilder.append("$indent-(class: $className) $stateString\"$label\"($descriptions) (ID: ${resourceId}(LN:$lineNum)) $bounds\n")
        }

        // 5. 자식 노드 순회 (depth 없이 재귀 호출)
        for (i in 0 until nodeInfo.childCount) {
            val childNode = nodeInfo.getChild(i) ?: continue
            traverseNode(childNode, logBuilder, depth + 1)
        }
    }
    override fun onInterrupt() {
        log("서비스가 중단된 상태.")
        // 서비스 중단 시
        isActive.set(false)
    }
    override fun onDestroy() {
        super.onDestroy()
        isActive.set(false)
    }
    private fun getLabelOfNode(nodeInfo: AccessibilityNodeInfo?): String{
        nodeInfo?:return ""
        // 1. 텍스트 및 콘텐츠 설명 추출
        val textContent = nodeInfo.text?.toString() ?: ""
        val contentDescription = nodeInfo.contentDescription?.toString() ?: ""

        return when {
            // 둘 다 비었으면 빈 문자열
            textContent.isEmpty() && contentDescription.isEmpty() -> ""

            // 둘 중 하나가 비었거나 내용이 같으면 하나만 반환
            textContent.isEmpty() -> contentDescription
            contentDescription.isEmpty() || textContent == contentDescription -> textContent

            // 둘 다 있고 내용이 다르면 합침 (불필요한 콤마 방지)
            else -> "$textContent, $contentDescription"
        }
    }
    private fun getDescriptionOfNode(nodeInfo: AccessibilityNodeInfo?): String{
        nodeInfo?:return ""
        val stateDescription =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                nodeInfo.stateDescription?.toString()?: ""
            }else{
                ""
            }
        val supplementDescription =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                nodeInfo.supplementalDescription?.toString() ?: ""
            }else {
                ""
            }
        return when {
            // 둘 다 비었으면 빈 문자열
            stateDescription.isEmpty() && supplementDescription.isEmpty() -> ""

            // 둘 중 하나가 비었거나 내용이 같으면 하나만 반환
            stateDescription.isEmpty() -> ""
            supplementDescription.isEmpty() -> stateDescription

            // 둘 다 있고 내용이 다르면 합침 (불필요한 콤마 방지)
            else -> "$stateDescription, $supplementDescription"
        }
    }
    private fun testMeaningful(label:String, nodeInfo: AccessibilityNodeInfo): Boolean{
        return (label.isNotBlank() || nodeInfo.isEditable || nodeInfo.isSelected)
    }
    private fun testActionable(nodeInfo: AccessibilityNodeInfo): Boolean{
        return (nodeInfo.isClickable || nodeInfo.isCheckable || nodeInfo.isLongClickable || nodeInfo.isFocusable)
    }
}