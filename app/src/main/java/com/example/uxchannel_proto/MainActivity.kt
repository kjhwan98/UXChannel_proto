package com.example.uxchannel_proto

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 권한 요청 버튼
        val btnEnableAccess = findViewById<Button>(R.id.btnEnableNotificationAccess)

        btnEnableAccess.setOnClickListener {
            if (!isNotificationServiceEnabled()) {
                // 알림 접근 권한이 활성화되어 있지 않다면 설정 화면으로 이동
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                // 권한이 이미 활성화되어 있으면 사용자에게 안내
                Toast.makeText(this, "Notification access is already enabled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 알림 서비스 접근 권한 확인
    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val packageName = packageName

        return enabledListeners != null && enabledListeners.contains(packageName)
    }
}