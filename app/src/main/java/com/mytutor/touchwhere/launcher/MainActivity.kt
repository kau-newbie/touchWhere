package com.mytutor.touchwhere.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mytutor.touchwhere.feature.overlay.MyAccessibilityService
import com.mytutor.touchwhere.feature.overlay.OverlayService
import com.mytutor.touchwhere.R
import com.mytutor.touchwhere.util.log
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(){

    // overlay 권한확인
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            startOverlay()
        } else {
            // 권한이 거부됨
        }
    }
    // accessibilityService 권한확인
    private val serviceClass = MyAccessibilityService::class.java

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    // --- UI 변수 선언 (findViewById로 연결할 변수들) ---

    // 다이얼로그를 전역 변수로 관리해야 나중에 닫을(dismiss) 수 있음.
    private var accessibilityDialog: AlertDialog? = null
// 초기
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //res.layout 폴더의 activity_main.xml을 이용해 this.activity를 만듦.
        setContentView(R.layout.activity_main)

        //버튼들 - onCreate()시 초기화
        val settingButton: ImageButton = findViewById(R.id.btn_settings)
        val powoffButton: ImageButton = findViewById(R.id.btn_exit)
        val startButton: Button = findViewById(R.id.btn_start)


        // 수정 - 중복 호출 제거
        // ui log 띄울 accessibilityService 권한확인
        checkAccessibilityPermission()

        // auduiorecord 권한확인 (마찬가지로 중복 제거)
        if (!checkPermissions()) {
            requestPermissions()
        }
    // 접근성서비스 활성화
        MyAccessibilityService.Companion.isActive.compareAndSet(false,true)
        //버튼들 눌렀을 때 동작
        startButton.setOnClickListener {
            checkAndStartOverlay()
        }
        settingButton.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        }
        powoffButton.setOnClickListener {
            finishAffinity()
            exitProcess(0) // mainactivity에서 종료버튼 누르면 서비스까지 죽임.
        }

    }// oncreate끝!
    override fun onResume() {
        super.onResume()
        MyAccessibilityService.Companion.isActive.compareAndSet(false,true)
        // 다시 돌아왔을 때 권한이 있으면 다이얼로그를 닫아줍니다.
        if (isAccessibilityServiceEnabled(this, serviceClass)) {
            if (accessibilityDialog != null && accessibilityDialog!!.isShowing) {
                accessibilityDialog!!.dismiss() // 다이얼로그 닫기
                Toast.makeText(this, "접근성 권한이 확인되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    } // onreasume끝!
    override fun onDestroy() {
        MyAccessibilityService.Companion.isActive.set(false)
        super.onDestroy()
    }

    /**
     * 접근성 권한을 확인하고, 없으면 '다이얼로그'를 띄우는 함수
     */
    private fun checkAccessibilityPermission() {
        if (isAccessibilityServiceEnabled(this, serviceClass)) {
            log("접근성 서비스가 활성화되어 있습니다.")
            // 권한이 있으니 필요한 작업 수행
        } else {
            log("접근성 서비스가 비활성화되어 있습니다. 다이얼로그 표시.")

            showPermissionDialog()
        }
    }

    /**
     * 사용자에게 권한 설정 화면으로 이동할지 물어보는 다이얼로그 표시
     */
    private fun showPermissionDialog() {
        // 이미 다이얼로그가 떠 있으면 또 띄우지 않음
        if (accessibilityDialog != null && accessibilityDialog!!.isShowing) return

        // 1. 여기서 'builder'라는 변수를 만듦.
        val builder = AlertDialog.Builder(this)
            .setTitle("권한 안내")
            .setMessage("서비스 제공을 위해 '접근성 권한'이 필요합니다.\n설정 화면으로 이동하시겠습니까?")
            .setPositiveButton("이동") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("취소") { dialog, _ ->
                Toast.makeText(this, "기능 사용이 제한될 수 있습니다.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setCancelable(false)

        // 2. 이제 'builder' 변수를 사용.
        accessibilityDialog = builder.create()
        accessibilityDialog?.show()
    }

    /**
     * 접근성 설정 화면을 여는 함수 (토스트 메시지 포함)
     */
    private fun openAccessibilitySettings() {
        Toast.makeText(this, "활성화할 서비스를 찾아 켜주세요.", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)

        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == context.packageName &&
                service.resolveInfo.serviceInfo.name == serviceClass.name) {
                return true
            }
        }
        return false
    }


    // --- record & whisper permission 관련 함수들
    private fun checkPermissions(): Boolean {
        val recordAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val writePermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        return recordAudioPermission == PackageManager.PERMISSION_GRANTED &&
                storagePermission == PackageManager.PERMISSION_GRANTED &&
                writePermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "녹음 권한이 승인되었습니다. 버튼을 다시 눌러주세요.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "녹음 및 저장 권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }// audio record를 위한 permission 끝.




    // --- 오버레이 부분 ---
    private fun checkAndStartOverlay() {
        if (checkOverlayPermission()) {
            startOverlay()
        } else {
            requestOverlayPermission()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        overlayPermissionLauncher.launch(intent)

    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // 이 코드가 앱을 백그라운드(뒤)로 보내줍니다.
        moveTaskToBack(true)

        // (만약 앱을 아예 종료시키고 싶다면 finish()를 쓰면 되지만,
        //  다시 설정을 켜야 할 수도 있으니 moveTaskToBack을 추천합니다.)
    }
}