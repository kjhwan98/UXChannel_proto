package com.example.uxchannel_proto

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    // 앱에서 사용하는 각종 권환 및 서비스에 대한 요청 코드와 알림 채널 ID 정의
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 101
        const val PREFS_NAME = "AppPrefs"
        const val KEY_SERVICE_ON_TIME = "serviceOnTime"
        const val KEY_SERVICE_OFF_TIME = "serviceOffTime"
        private const val KEY_LAST_TIMESTAMP = "lastTimestamp"
        private const val KEY_FEATURE_ENABLED = "featureEnabled"
    }
    private lateinit var handler: Handler
    private lateinit var updateTimesRunnable: Runnable
    // 토글 버튼 정의(서비스 시작/중지)
    private lateinit var btnToggleFeature: Button
    private lateinit var deviceIdTextView: TextView


    // 데이터 전송 상태를 받는 BroadcastReceiver를 정의
    private val transferDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isSuccess = intent.getBooleanExtra("TransferStatus", false)
            if (isSuccess) {
                Toast.makeText(context, "Data Transfer Successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Data Transfer Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "InlinedApi", "MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 위치, 활동 인식, 알림, 배터리 최적화 권한을 요청하는 메소드 호출
        requestLocationPermissions()
        requestActivityRecognitionPermission()
        requestNotificationPermission()
        requestBatteryOptimizationPermission()
        checkNotificationListenerPermission()
        checkAccessibilityServicePermission()
    // // 데이터 전송 버튼을 설정하고 클릭 이벤트 처리
    // val sendDataButton: Button = findViewById(R.id.btnSendDataNow)
    // sendDataButton.setOnClickListener {
    // sendDataNow(it)
    // }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestActivityRecognitionPermission()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, UsageStatsService::class.java))
        } else {
            startService(Intent(this, UsageStatsService::class.java))
        }

        if (areLocationServicesEnabled() && hasUsageStatsPermission()) {
            startService(Intent(this, UsageStatsService::class.java))
        }

        if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            startService(Intent(this, UsageStatsService::class.java))
        }

        val transferDataFilter = IntentFilter("com.example.app.TRANSFER_DATA")
        // val serviceStoppedFilter = IntentFilter("com.example.app.SERVICE_STOPPED")

        // Use RECEIVER_NOT_EXPORTED flag for Android 12 (API level 31) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(transferDataReceiver, transferDataFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(transferDataReceiver, transferDataFilter)
        }

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", null)

        if (deviceId == null) {
            // deviceId가 없을 경우 에러 처리
            Toast.makeText(this, "Device ID not found", Toast.LENGTH_SHORT).show()
        } else {
            // deviceIdTextView를 초기화하고 화면에 표시
            deviceIdTextView = findViewById(R.id.tvDeviceId)
            displayDeviceId(deviceId)
        }

        handler = Handler(Looper.getMainLooper())
        updateTimesRunnable = object : Runnable {
            override fun run() {
                updateServiceTimes()
                handler.postDelayed(this, 60000) // Update every minute
            }
        }
        handler.postDelayed(updateTimesRunnable, 60000)

        // 기능 토글 버튼의 클릭 이벤트 처리
        btnToggleFeature = findViewById(R.id.toggleFeatureButton)
        btnToggleFeature.setOnClickListener {
            toggleFeature()
        }

        startUsageStatsService()
        scheduleDailyFirebaseUpdate()
    }

    @SuppressLint("SetTextI18n")
    private fun displayDeviceId(deviceId: String) {
        deviceIdTextView.text = "Device ID: $deviceId"
    }
    // // 서비스 활성화 상태를 확인하고 설정하는 메소드
    // private fun isServiceEnabled(): Boolean {
    // // 서비스 활성화 여부를 SharedPreferences에서 가져옴
    // val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    // return sharedPreferences.getBoolean("ServiceEnabled", false)
    // }
    //
    // private fun setServiceEnabled(enabled: Boolean) {
    // // 서비스 활성화 상태를 SharedPreferences에 저장
    // val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    // with(sharedPreferences.edit()) {
    // putBoolean("ServiceEnabled", enabled)
    // apply()
    // }
    // }

    private fun updateButtonColor(isFeatureEnabled: Boolean) {
        // 버튼 색상을 상태에 따라 설정
        btnToggleFeature.setBackgroundColor(if (isFeatureEnabled) Color.GREEN else Color.GRAY)
    }

    // 기능 토글 버튼의 상태를 업데이트
    private fun toggleFeature() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val currentTimestamp = System.currentTimeMillis()
        val lastTimestamp = sharedPreferences.getLong(KEY_LAST_TIMESTAMP, currentTimestamp)
        val elapsedTime = currentTimestamp - lastTimestamp

        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)

        if (isFeatureEnabled) {
            val currentOnTime = sharedPreferences.getLong(KEY_SERVICE_ON_TIME, 0)
            editor.putLong(KEY_SERVICE_ON_TIME, currentOnTime + elapsedTime)
            Toast.makeText(this, "기능이 비활성화되었습니다.", Toast.LENGTH_SHORT).show()

            // 기능 비활성화 시 storedNotifications를 비우지 않음
        } else {
            val currentOffTime = sharedPreferences.getLong(KEY_SERVICE_OFF_TIME, 0)
            editor.putLong(KEY_SERVICE_OFF_TIME, currentOffTime + elapsedTime)
            Toast.makeText(this, "기능이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        }

        editor.putBoolean(KEY_FEATURE_ENABLED, !isFeatureEnabled)
        editor.putLong(KEY_LAST_TIMESTAMP, currentTimestamp)

        if (editor.commit()) {
            updateButtonColor(!isFeatureEnabled)
            displayServiceTimes()
        } else {
            Toast.makeText(this, "설정 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationListenerPermission() {
        // 알림 접근 권한이 부여되었는지 확인, 부여되지 않았다면 사용자에게 설정 변경을 요청
        if (!permissionGranted()) {
            AlertDialog.Builder(this)
                .setTitle("알림 서비스 허용")
                .setMessage("앱의 기능을 완전히 사용하기 위해서는 알림 허용이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun checkAccessibilityServicePermission() {
        if (!isAccessibilityServiceEnabled(LauncherAccessibilityService::class.java)) {
            AlertDialog.Builder(this)
                .setTitle("접근성 서비스 허용")
                .setMessage("앱의 기능을 완전히 사용하기 위해서는 접근성 허용이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    // 사용자가 설정으로 이동하길 원할 경우, 액세서빌리티 설정 화면으로 이동
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun isAccessibilityServiceEnabled(service: Class<out AccessibilityService>): Boolean {
        val enabledServicesSetting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(ComponentName(applicationContext, service).flattenToString(), ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun permissionGranted(): Boolean {
        // 알림 접근 권한이 부여되었는지 확인
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationPermission() {
        // 배터리 최적화 무시 권한 요청
        val packageName = packageName
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent()
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestNotificationPermission() {
        // 알림 허용 여부 확인
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("알림 권한 허용")
                .setMessage("앱의 기능을 완전히 사용하기 위해서는 알림 허용이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    // 사용자가 설정으로 이동하길 원할 경우, 앱의 알림 설정 화면으로 이동
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            putExtra(Settings.EXTRA_CHANNEL_ID, applicationInfo.uid)
                        }
                    }
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    // 앱이 다시 활성화될 때 호출
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onResume() {
        super.onResume()
        startUsageStatsService()
        updateServiceTimes() // 앱이 활성화될 때 시간을 업데이트

        val isServiceEnabled = checkServiceEnabled()
        if (isServiceEnabled) {
            // 서비스가 활성화된 경우에만 시간 업데이트를 계속 진행
            handler.post(updateTimesRunnable)
        }
    }

    private fun checkServiceEnabled(): Boolean {
        // 서비스 활성화 여부를 SharedPreferences에서 가져옴
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
    }

    override fun onPause() {
        super.onPause()
        updateServiceTimes() // 앱이 중단되기 전에 시간을 업데이트
        handler.removeCallbacks(updateTimesRunnable) // 핸들러 중단
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
        updateButtonColor(isFeatureEnabled)
    }

    private fun startUsageStatsService() {
        val serviceIntent = Intent(this, UsageStatsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // 위치 권한 요청 베소드
    private fun requestLocationPermissions() {
        // 세밀한 위치권한과 대략적인 위치 권한이 있느지 확인
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val shouldRequestBackgroundLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        // 백그라운드 위치 권한을 별도로 요청해야 하는지 확인
        val hasBackgroundLocationPermission = if (shouldRequestBackgroundLocation) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android Q 미만 버전에서는 항상 true 처리
        }

        val permissionsToRequest = mutableListOf<String>()
        if (!hasFineLocationPermission) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasCoarseLocationPermission) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (shouldRequestBackgroundLocation && !hasBackgroundLocationPermission) {
            // 백그라운드 위치 권한 요청은 Android Q 이상에서만 수행
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    // 위치 서비스 활성화 여부 확인 메소드
    private fun areLocationServicesEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }

    // 활동 인식 권한 요청 메소드
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestActivityRecognitionPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE)
        }
    }

    // 권한 요청 결과 처리 메소드
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 권한이 부여되면 활동인식 시작
                    startUsageStatsService()
                } else {
                    // 권한이 거부되면 사용자에게 필요성 설명
                    Toast.makeText(this, "Activity recognition permission is necessary for this feature to work", Toast.LENGTH_SHORT).show()
                }
            }
            // 위치 권한 요청 결과 처리
            LOCATION_PERMISSION_REQUEST_CODE -> {
                // 정밀위치, 대략적인 위치 및 백그라운드 위치 권한이 모두 부여되었는지 확인
                val fineLocationGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                val coarseLocationGranted = grantResults.size > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED
                val backgroundLocationPermissionIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 2 else -1
                val backgroundLocationGranted = if (backgroundLocationPermissionIndex != -1) {
                    grantResults.size > backgroundLocationPermissionIndex && grantResults[backgroundLocationPermissionIndex] == PackageManager.PERMISSION_GRANTED
                } else {
                    true // 안드로이드 Q미만에서는 항상 true
                }
                if (!(fineLocationGranted && coarseLocationGranted && backgroundLocationGranted)) {
                    // 필요한 위치 권한이 부여되지 않았다면 사용자에게 알림
                    Toast.makeText(this, "Location permission is necessary for this app's functionality", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 사용 통계 권한이 있는지 확인
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = applicationContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        appOps?.let {
            // API 레벨 29 이상에서는 unsafeCheckOpNoThrow를 사용
            val mode = it.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
        return false
    }

    // 데이터를 즉시 전송하는 기능
    // private fun sendDataNow(view: View) {
    // // UsageStatsService 서비스에 'ACTION_SEND_DATA_NOW' 액션을 포함하는 인텐트 전송
    // // 데이터 전송을 즉시 시작하도록 요청
    // Intent(this, UsageStatsService::class.java).also { intent ->
    // intent.action = "com.example.app.ACTION_SEND_DATA_NOW"
    // startService(intent)
    // }
    // }

    private fun updateServiceTimes() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val currentTimestamp = System.currentTimeMillis()
        val lastTimestamp = sharedPreferences.getLong(KEY_LAST_TIMESTAMP, currentTimestamp)
        val elapsedTime = currentTimestamp - lastTimestamp

        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
        if (isFeatureEnabled) {
            val currentOnTime = sharedPreferences.getLong(KEY_SERVICE_ON_TIME, 0)
            editor.putLong(KEY_SERVICE_ON_TIME, currentOnTime + elapsedTime)
        } else {
            val currentOffTime = sharedPreferences.getLong(KEY_SERVICE_OFF_TIME, 0)
            editor.putLong(KEY_SERVICE_OFF_TIME, currentOffTime + elapsedTime)
        }

        editor.putLong(KEY_LAST_TIMESTAMP, currentTimestamp)
        editor.apply()

        displayServiceTimes()
    }


    @SuppressLint("SetTextI18n")
    private fun displayServiceTimes() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serviceOnTime = sharedPreferences.getLong(KEY_SERVICE_ON_TIME, 0)
        val serviceOffTime = sharedPreferences.getLong(KEY_SERVICE_OFF_TIME, 0)

        findViewById<TextView>(R.id.serviceOnTimeTextView).text = "Service ON Time: ${formatDuration(serviceOnTime)}"
        findViewById<TextView>(R.id.serviceOffTimeTextView).text = "Service OFF Time: ${formatDuration(serviceOffTime)}"
    }

    private fun formatDuration(millis: Long): String {
        val days = millis / (24 * 3600000)
        val hours = (millis / 3600000) % 24
        val minutes = (millis / 60000) % 60
        return String.format("%d days, %02d:%02d", days, hours, minutes)
    }

    private fun scheduleDailyFirebaseUpdate() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, FirebaseDataSenderReceiver::class.java)
        // FLAG_IMMUTABLE 추가
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    // 액티비티가 파괴될 때 BroadcastReceiver를 등록 해제
    override fun onDestroy() {
        super.onDestroy()
        updateServiceTimes() // 앱이 완전히 종료되기 전에 시간을 업데이트60
        handler.removeCallbacks(updateTimesRunnable)
        unregisterReceiver(transferDataReceiver)

    }
}