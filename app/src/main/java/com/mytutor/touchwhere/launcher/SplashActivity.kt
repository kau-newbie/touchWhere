package com.mytutor.touchwhere.launcher

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mytutor.touchwhere.data.buffer.PromptBuffer
import com.mytutor.touchwhere.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 앱이 시작되자마자 인터넷 연결 상태를 확인합니다.
        if (isNetworkConnected()) {
            // 인터넷이 연결되어 있으면: 2초 후 메인 화면으로 이동합니다.
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }, 2000)
        } else {
            // 인터넷이 연결되어 있지 않으면: 경고 팝업창을 띄웁니다.
            showNetworkErrorDialog()
        }

        // 로컬에서 파일을 로드합니다.
        lifecycleScope.launch {
            Toast.makeText(this@SplashActivity, "로딩중....잠시만 기다려주세요.", Toast.LENGTH_SHORT).show()
            // rsc/raw안의 base프롬프트를 promptbuffer로 복사. <-- 이것도 그냥 초기화로 넣음.
            //Object 선언은 c로치면 전역변수이자 객체지향에서 static 클래스.
            withContext(Dispatchers.IO) {  //withContext...IO에 담아둬서 @로 명시안하면 coroutineScope가담김.
                PromptBuffer.loadBasePrompt(this@SplashActivity)
            }
        }
    }//onCreate끝

    //----------------------------------functions---------------------
    // 인터넷 연결 상태를 확인하는 함수
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    // 경고 팝업창을 띄우는 함수
    private fun showNetworkErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("인터넷 연결 오류")
            .setMessage("앱을 사용하려면 데이터 또는 와이파이 연결이 필요합니다.")
            .setCancelable(false) // 사용자가 팝업 바깥을 눌러서 끄는 것을 방지

            // '종료' 버튼 설정
            .setPositiveButton("종료") { _, _ ->
                finish() // 앱을 종료합니다. (정확히는 activity를 종료- 켜진 activity가 spalshactv밖에 없었으니)
            }

            // '설정으로 이동' 버튼 설정
            .setNeutralButton("설정으로 이동") { _, _ ->
                // 와이파이 설정 화면으로 바로 이동시킵니다.
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                finish() // 설정으로 이동 후, 현재 스플래시 화면은 종료합니다. os가 앱을 종료시키진 않음.
            }
            .show()
    }
}