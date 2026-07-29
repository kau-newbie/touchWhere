package com.mytutor.touchwhere.data.repository

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.mytutor.touchwhere.data.api.ATTApiService
import com.mytutor.touchwhere.data.buffer.PromptBuffer
import com.mytutor.touchwhere.data.network.WhisperModelSpec
import com.mytutor.touchwhere.util.BubbleStateChangeable
import com.mytutor.touchwhere.util.log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

class AudioRecordRepository @Inject constructor(
    private val sttApi: ATTApiService,
    @param:ApplicationContext private val context: Context,
    private val audioManager: BubbleStateChangeable
) {

    // Whisper API 및 녹음 관련 변수들
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = ""
    private var isRecording: Boolean = false


    //  auidio 반납 및 처리 함수
    fun cleanUpRecordSetting(){
        isRecording = false
        mediaRecorder?.release()
        mediaRecorder = null
    }
    //  녹음 관련 함수들 + whisper에 쿼리 날리기

    @Throws(IOException::class) // recording 을 실패했을 경우, 그냥 던져버린다.
                                                 // 이렇게 하면, startRecordngAndUpload (이 함수)를 호출한 caller가 받게 된다.
                                                 // 받으려면 미리 try-catch구문의 catch로 준비해둬야 함!
    suspend fun startRecordingAndUpload() {
        log("startRecordingAndUpload 진입직후 시점.")
        //이미 녹음 중에 버튼을 눌렀다면, 바로 return Unit( == void in c)
        //if (isRecording) return
        // <------------------------ overlayService에서 녹음버튼누르면 초기화되도록 수정했음 (25.12.03)
        // startRecordingAndUpload 진입했으니 lock(isRecording = true)을 걸어준다.
        isRecording = true
        log("여기부터 녹음을 시작.\n")

        val file = File(
            context.externalCacheDir,
            "audio_record.m4a"
        ) //File(parent, child) 생성자: File은 (부모 폴더, 자식 파일 이름) 형식으로 경로를 만들 수 있대요.  (cache를 써서 임시이자 pricate 경로.)
        audioFilePath = file.absolutePath //해당 파일 (='file')의 절대경로를 return해서 audioFilePath가 가리키게 한다.
        log("Recording to: $audioFilePath")

        /*
            Build는 안드로이드 시스템 정보를 담고 있는 클래스
             SDK_INT는 이 코드가 실행 중인 스마트폰의 안드로이드 버전 번호(정수)*를 의미.
             annotation 'suppress'는 compiler의 warnning을 끄는 문구. "DEPRECAPTION"은 구버전 방식일 때 뜨는 경고를 지칭.
                아무튼 MediaRecorder 객체를 instance.
                사용법: https://developer.android.com/reference/kotlin/android/media/MediaRecorder
         */
        //withContext(Dispatchers.IO){ 어차피 얘를 만들어야 아랫줄 코드 실행해서....
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(
                context
            ) else @Suppress("DEPRECATION") (MediaRecorder())).apply { //apply는 바로 주체없이 메서드 쓸 수 있게.
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) //format정하기.
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC) //AAC가 효율이나 호환성이 좋대요. 유투브에서도 쓴다네요.
                setAudioEncodingBitRate(128000) //128kbps 정도의 녹음속도면 선명한 편이라고 하네요.
                setAudioSamplingRate(44100) //AAC audio coding standard ranges from 8 to 96 kHz. cd가 44.1k고 방송,영화는 48k
                setOutputFile(audioFilePath)
            }
        //}
        try {
            withContext(Dispatchers.IO) { // prepare과 start 작업은 무겁다네요 + 이미 mainactivity에서 coroutine으로 실행했으니 (launch) 그 안에서 어떤 함수라도 suspend면 맘껏 쓸 수 있음.
                mediaRecorder?.prepare() //앞에서 설정한 parameter들이 실제 기기에서 가능한 스펙인지 체크하고 자동조정.
                mediaRecorder?.start() //Begins capturing and encoding data to the file specified with setOutputFile().
            }
        }
        catch(e: IOException) {
            log("prepare() failed $e")
            audioManager.onError("Error: mediaRecorder failed")
            stopRecording(false)
            return
        }

        //audioManager.onStateChange("5초간 듣는 중입니다.")

        delay(8000) // suspend 함수라 launch {}안에서.
        log("이제 stoprecording으로 넘어감.")
        stopRecording(true)
    }

    private suspend fun stopRecording(upload: Boolean) {
        if (!isRecording) return
        try {
            mediaRecorder?.apply { stop(); release() }
        } catch (e: RuntimeException) { log("AudioRecorder stop() failed : $e") }
        finally {
            mediaRecorder = null
            isRecording = false
            //whisperTestButton.isEnabled = true

            if (upload) {
                audioManager.onStateChange("듣기 완료. 분석중...")
                uploadAudioToWhisper()
            } else {
                audioManager.onStateChange("준비상태입니다.")
            }
        }
    }

    private suspend fun uploadAudioToWhisper() {
        log("uploadAudioToWHisper 진입 시점.")
        val file = audioFilePath?.let { File(it) }
        if (file == null || !file.exists() || file.length() == 0L) {
            audioManager.onError("Error: there's no ma4file.")
            return
        }

        withContext(Dispatchers.IO) { //Dispatchers.IO는 네트워크, 파일 입출력 등 오래 걸리는 일을 전담하는 **'창고(별도의 스레드 풀)'**이라네요.
            try {
                // (ApiClient.instance 사용, file과 model 파라미터만 전달)
                val spec = WhisperModelSpec(file = file)
                val response = sttApi.transcribeAudio( //OpenApiService 타입으로 반환.
                    url = spec.endPoint,
                    file = spec.toFilePart(),
                    params = spec.toMultipartParts()
                )
                withContext(Dispatchers.Main) { // 멀티 스레딩 - 이제 main thread에게 넘겨주는데,
                    // response를 이미 NW 통해 받아왔으니, 이제 main으로 넘겨줌.
                    if (response.isSuccessful) {
                        val openAiResponse = response.body()
                        //음성 인식된 게 없으면 바로 err를 내서 exception으로 빠짐.
                        val transcribedText = openAiResponse?.text ?: error("음성인식된 텍스트가 없습니다.")
                        PromptBuffer.setTheGoal(transcribedText)
                        log("[uploadAndWHipser 내부]set theGoal 완료.")
                        //buildFinalPrompt하기 전에! 한 번 더 job이 취소됐는지 확인해야함. (죽은 job이 prompt를 변경시켜버리면 좀 사고..)
                        coroutineContext.ensureActive()
                        PromptBuffer.buildFinalPrompt()
                        // test : promptbuffer logic
                        log("[uploadAndWHipser 내부] buildFinalPrompt()실행. 녹음인식 결과: $transcribedText")
                        audioManager.onResult("\n인식 결과: $transcribedText")
                    } else {
                        val errorBody = response.errorBody()?.string()
                        audioManager.onError("API 오류: ${response.code()}")
                        log("Error ${response.code()}: $errorBody")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    audioManager.onError("음성인식단계 오류: ${e.message}")
                    log("Exception during API call : $e")
                }
            } finally {
                file.delete()
                audioFilePath = null
            }
            log("upload-whisper 안 scope 끝 시점.")
        } //lifecyclescope 끝.
        log("upload-whisper 끝 시점.")
    }




}