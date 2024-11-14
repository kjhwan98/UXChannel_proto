package com.example.uxchannel_proto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class LauncherAccessibilityService : AccessibilityService() {

    companion object {
        const val PREFS_NAME = "AppPrefs"
        const val LAUNCHER_ENTRY_COUNT_KEY = "launcherEntryCount"
        const val ACTION_LAUNCHER_ENTRY = "com.example.ACTION_LAUNCHER_ENTRY"
    }

    private val sharedPreferences: SharedPreferences
        get() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var lastActivePackage: String? = null

    private val homeScreenPackage = "com.sec.android.app.launcher"
    private val excludedPackages = listOf("com.samsung.android.biometrics.app.setting",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
        "com.samsung.android.spay",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.sidegesturepad",
        "com.samsung.android.app.spage",
        "com.samsung.systemui.notilus",
        "com.navercorp.android.smartboard")


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPackage = event.packageName?.toString()

            // 현재 실행 중인 패키지 이름을 로그에 출력
            Log.d("NotificationListener", "Current package: $currentPackage")

            // 제외할 패키지 목록에 포함되지 않은 경우 처리
            if ((currentPackage != null) && !excludedPackages.contains(currentPackage)) {
                // 현재 패키지가 홈 스크린 패키지와 일치하고, 마지막 활성화된 패키지가 홈 스크린이 아닌 경우
                if (currentPackage == homeScreenPackage && lastActivePackage != null && lastActivePackage != homeScreenPackage) {
                    incrementLauncherEntryCount()
                    sendBroadcast(Intent(ACTION_LAUNCHER_ENTRY))
                    Log.d("NotificationListener", "Broadcast sent for app exit: $lastActivePackage")
                }

                // 마지막 활성화된 앱 패키지 이름을 업데이트
                lastActivePackage = currentPackage
            }
        }
    }

    private fun incrementLauncherEntryCount() {
        val currentCount = sharedPreferences.getInt(LAUNCHER_ENTRY_COUNT_KEY, 0) + 1
        sharedPreferences.edit().putInt(LAUNCHER_ENTRY_COUNT_KEY, currentCount).apply()
        Log.d("NotificationListener", "Launcher entry count updated: $currentCount")
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
        Log.d("NotificationListener", "Service connected")
    }
}

