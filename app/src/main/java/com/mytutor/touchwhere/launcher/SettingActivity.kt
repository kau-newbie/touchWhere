package com.mytutor.touchwhere.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.mytutor.touchwhere.R

class SettingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // res/layout 폴더에 있는 activity_setting.xml 파일과 연결해준다.

        setContentView(R.layout.activity_setting)

        //val powoffButton: Button = findViewById(R.id.btnPowoffinSettings) // 앱종료버튼
       val backButton: ImageButton = findViewById(R.id.btnback_s) // 뒤로가기버튼

        // 종료 버튼을 눌렀을 때 동작 설정
      /*  powoffButton.setOnClickListener {
            finishAffinity()
        }*/
        // 뒤로가기 버튼을 눌렀을 때 동작 설정
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

}