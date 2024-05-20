package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.*
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class NotificationListener : NotificationListenerService() {
   // 각 알림의 데이터를 저장
    private val notificationsMap = mutableMapOf<Int, NotificationData>()
    private lateinit var deviceId: String // 기기의 고유 ID
    private val notificationChannelId = "NotiServiceChannel" // 알림 채널 ID
    private val delayedNotifications = mutableMapOf<Int, NotificationData>() // 지연된 알림을 저장하는 맵
    private lateinit var notificationManager: NotificationManager // 알림 관리자
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper()) // 핸들러 정의
    private val seenNotifications = mutableMapOf<Int, NotificationData>() // 노티바를 본 알림을 저장하는 맵
    private val screenOffNotifications = mutableMapOf<Int, NotificationData>() // 화면이 꺼졌을 때 저장하는 맵
    private var lastScreenInteractive = false // 마지막 화면 상태를 저장
    private val checkScreenStateRunnable = object : Runnable {
        override fun run() {
            checkScreenState()
            handler.postDelayed(this, 1000) // 5초마다 상태를 확인
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        deviceId = generateUniqueDeviceId() // 기기 ID 생성
        createNotificationChannel() // 알림 채널 생성
        startForegroundService() // 포그라운드 서비스 시작
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // 알림 서비스 접근
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler.post(checkScreenStateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Handler에 예약된 모든 콜백과 메시지 제거
        handler.removeCallbacksAndMessages(null)
        // 포그라운드 서비스 중지
        stopForeground(Service.STOP_FOREGROUND_REMOVE)        // 시스템에 서비스가 종료되었음을 알리기 위해 브로드캐스트 전송
        val restartServiceIntent = Intent("com.example.ACTION_RESTART_NOTIFICATION_LISTENER_SERVICE")
        sendBroadcast(restartServiceIntent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelName = "Notification Stats Service Channel"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(notificationChannelId, channelName, importance)
        channel.description = "Collecting notification stats"

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel) // 채널 생성
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification: Notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Notification Stats Service")
            .setContentText("Collecting notification stats")
            .setSmallIcon(R.drawable.khu)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        // 포그라운드 서비스로 서비스 시작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun generateUniqueDeviceId(): String { // 기기의 고유 ID 생성
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var id = sharedPreferences.getString("deviceId", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("deviceId", id).apply()
        }
        return id
    }

    //타임 스탬프 문자열로 반환
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault() // 서버의 시간대 설정이 필요하면 이 부분을 조정
        return sdf.format(timestamp)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
        val packageName = sbn.packageName
        val postTime = formatDate(sbn.postTime)
        val icon = notification.smallIcon
        val contentIntent = notification.contentIntent

        val data = NotificationData(sbn.id, title, text, postTime, icon, packageName, contentIntent)
        notificationsMap[sbn.id] = data
        checkNotificationSeen(sbn)
        sendDataToFirebase(sbn.id, packageName, title, text, postTime)
    }

    // Firebase에 데이터 전송
    @SuppressLint("HardwareIds")
    private fun sendDataToFirebase(notificationId: Int, packageName: String, title: String?, text: String?, postTime: String) {
        if (packageName != "com.android.systemui") { // systemui 패키지 제외
            val uniqueKey = "$notificationId-$postTime"
            val notificationData = hashMapOf(
                "deviceId" to deviceId,
                "package_name" to packageName,
                "notification_id" to notificationId,
                "post_time" to postTime,
                "title" to title,
                "text" to text
            )

            // Firebase Firestore에 데이터 저장
            val database = FirebaseDatabase.getInstance().getReference("notifications")
            database.child(uniqueKey).setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("Firebase", "Data successfully written to Realtime Database.")
                }
                .addOnFailureListener { e ->
                    Log.w("Firebase", "Error writing document to Realtime Database", e)
                }
        }
    }

    // 알림이 제거될때 호출
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        try {
            if (!this::notificationManager.isInitialized) {
                Log.w("NotificationListener", "NotificationManager is not initialized.")
                return
            }

            // 알림이 제거될 때 호출
            val packageName = sbn.packageName
            val notificationId = sbn.id
            val removalReason = parseRemovalReason(reason) // 제거 이유
            val removalTime = formatDate(System.currentTimeMillis())
            val notificationData = notificationsMap[notificationId] // 기존 알림 데이터 참조
            val title = notificationData?.title
            val text = notificationData?.text

            if (sbn.packageName == "com.kakao.talk" && reason == REASON_GROUP_SUMMARY_CANCELED) {
                // 제거된 알림의 정보를 저장
                val extractedNotificationData = extractNotificationData(sbn)
                delayedNotifications[sbn.id] = extractedNotificationData

                // 30초 후에 재전송 로직
                handler.postDelayed({
                    delayedNotifications[sbn.id]?.let {
                        sendDelayedNotification(it) // 지연된 알림 재전송
                        delayedNotifications.remove(sbn.id) // 처리 후 삭제
                    }
                }, 30000)  // 30초 딜레이
            }

            // 파이어베이스에 제거된 알림 데이터 전송
            sendRemovalDataToFirebase(notificationId, packageName, title, text, removalReason, removalTime)
            notificationsMap.remove(notificationId) // 맵에서 제거
            seenNotifications.remove(notificationId)
        } catch (e: DeadObjectException) {
            Log.w("NotificationListener", "Unable to notify listener (removed): $e")
            // DeadObjectException을 필요한 만큼 처리하거나 로깅
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in onNotificationRemoved: $e")
            // 필요한 만큼 다른 예외를 처리하거나 로깅
        }
    }
    // 제거 이유를 문자열로 변환
    private fun parseRemovalReason(reason: Int): String {
        return when (reason) {
            REASON_APP_CANCEL -> "App Specific Cancel"
            REASON_APP_CANCEL_ALL -> "App Cancel All Notifications"
            REASON_ASSISTANT_CANCEL -> "Assistant Cancel"
            REASON_CANCEL -> "Notification Swiped"
            REASON_CANCEL_ALL -> "All Notifications Cleared"
            REASON_CHANNEL_BANNED -> "Channel Banned"
            REASON_CHANNEL_REMOVED -> "Channel Removed"
            REASON_CLEAR_DATA -> "Data Cleared"
            REASON_CLICK -> "Notification Clicked"
            REASON_ERROR -> "Error"
            REASON_GROUP_OPTIMIZATION -> "Group Optimization"
            REASON_GROUP_SUMMARY_CANCELED -> "Group Summary Canceled"
            REASON_LISTENER_CANCEL -> "Listener Cancel"
            REASON_LISTENER_CANCEL_ALL -> "Listener Cancel All"
            REASON_LOCKDOWN -> "Lockdown"
            REASON_PACKAGE_BANNED -> "Package Banned"
            REASON_PACKAGE_CHANGED -> "Package Changed"
            REASON_PACKAGE_SUSPENDED -> "Package Suspended"
            REASON_PROFILE_TURNED_OFF -> "Profile Turned Off"
            REASON_SNOOZED -> "Snoozed"
            REASON_TIMEOUT -> "Timeout"
            REASON_UNAUTOBUNDLED -> "Unautobundled"
            REASON_USER_STOPPED -> "User Stopped"
            else -> "Other"
        }
    }

    @SuppressLint("HardwareIds")
    // 지연된 알림을 다시 보내는 메소드
    private fun sendRemovalDataToFirebase(notificationId: Int, packageName: String, title: String?, text: String?, reason: String, removalTime: String) {
        if (packageName != "com.android.systemui") { // systemui 패키지 제외
            val uniqueKey = "$notificationId-$removalTime"
            val removalData = hashMapOf(
                "deviceId" to deviceId,
                "package_name" to packageName,
                "notification_id" to notificationId,
                "title" to title,
                "text" to text,
                "removal_reason" to reason,
                "removal_time" to removalTime
            )

            // Firebase Firestore에 데이터 저장
            val database = FirebaseDatabase.getInstance().getReference("notification_removals")
            database.child(uniqueKey).setValue(removalData)
                .addOnSuccessListener {
                    Log.d("Firebase", "Removal data successfully written to Realtime Database.")
                }
                .addOnFailureListener { e ->
                    Log.w("Firebase", "Error writing removal data to Realtime Database", e)
                }
        }
    }

    private fun extractNotificationData(sbn: StatusBarNotification): NotificationData {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE, "No Title")
        val text = extras.getCharSequence(Notification.EXTRA_TEXT, "No Text").toString()
        val postTime = formatDate(sbn.postTime)
        return NotificationData(sbn.id, title, text, postTime, sbn.notification.smallIcon, sbn.packageName, sbn.notification.contentIntent)
    }

    // 지연된 알림을 다시 보내는 메소드
    private fun sendDelayedNotification(data: NotificationData) {
        val channelID = "delayed_channel_id"
        // 알림 채널이 생성되었는지 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelID, "Delayed Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        // 아이콘을 IconCompat로 변환
        val iconCompat = IconCompat.createFromIcon(this, data.icon)

        // 새 알림 구성 및 보내기
        val newNotification = iconCompat?.let {
            NotificationCompat.Builder(this, channelID)
                .setContentTitle(data.title)
                .setContentText(data.text)
                .setSmallIcon(it)  // 변환된 IconCompat을 작은 아이콘으로 설정
                .setContentIntent(data.contentIntent)
                .setAutoCancel(true)
                .build()
        }

        notificationManager.notify(data.id, newNotification)
    }
    private fun checkNotificationSeen(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.kakao.talk") {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 1000  // 알림이 포스트되고 1초 전부터 현재까지의 이벤트를 확인

            val stats = usageStatsManager.queryEvents(beginTime, endTime)
            val event = UsageEvents.Event()
            while (stats.hasNextEvent()) {
                stats.getNextEvent(event)
                if (event.eventType == 10) { // NOTIFICATION_SEEN event type
                    seenNotifications[sbn.id] = notificationsMap[sbn.id]!!
                    break // Found the event, no need to continue
                }
            }
        }
    }

    private fun checkScreenState() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive
        if (lastScreenInteractive != isScreenOn) {
            if (isScreenOn) {
                handleScreenOn()
            } else {
                handleScreenOff()
            }
            lastScreenInteractive = isScreenOn
        }
    }

    private fun handleScreenOff() {
        // 화면이 꺼지면, 확인된 알림을 screenOffNotifications 맵으로 옮기고 취소
        seenNotifications.forEach { (id, data) ->
            notificationManager.cancel(id)
            screenOffNotifications[id] = data
        }
        seenNotifications.clear()
    }

    private fun handleScreenOn() {
        // 화면이 켜지면 screenOffNotifications에 저장된 알림을 재전송
        screenOffNotifications.forEach { (_, data) ->
            sendDelayedNotification(data)
        }
        screenOffNotifications.clear()
    }

}

