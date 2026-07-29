package com.mytutor.touchwhere.data.buffer

import com.mytutor.touchwhere.util.trimCoords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

object UIstateBuffer {
    var isTouched: AtomicBoolean = AtomicBoolean(false)
    var isMicUsed: AtomicBoolean = AtomicBoolean(false)

    private var savedUIState: String = ""
    // 초기값은 비어있는 리스트.
    private val _uiStateFlow = MutableStateFlow("")
    internal val uiStateFlow: StateFlow<String> get() = _uiStateFlow
    internal val logFlowNoLocInfo: String
        get() {
            saveUIState()
            return uiStateFlow.value.trimCoords()
        }


    internal fun updateUIState(newLog: String) {
        _uiStateFlow.value = newLog
    }
    internal fun saveUIState(){
        savedUIState = uiStateFlow.value
    }
    internal fun getSavedUIState():String{
        return savedUIState
    }
}