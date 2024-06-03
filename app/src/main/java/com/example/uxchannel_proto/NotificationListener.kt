package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.app.AlarmManager
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
import android.os.Build.VERSION_CODES.R
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.*
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toDrawable
import com.google.firebase.database.FirebaseDatabase
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class NotificationListener : NotificationListenerService() {
   // 각 알림의 데이터를 저장
    private val notificationsMap = mutableMapOf<Int, NotificationData>()
    private lateinit var deviceId: String // 기기의 고유 ID
    private val notificationChannelId = "NotiServiceChannel" // 알림 채널 ID
    private val delayedNotifications = mutableMapOf<String, StatusBarNotification>() // 지연된 알림을 저장하는 맵
    private lateinit var notificationManager: NotificationManager // 알림 관리자
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper()) // 핸들러 정의
    private val seenNotifications = mutableMapOf<String, StatusBarNotification>() // 노티바를 본 알림을 저장하는 맵
    private var screenOnTime: Long = 0
    private var screenOffTime: Long = 0
    private val pendingNotifications = mutableMapOf<String, StatusBarNotification>()
    private lateinit var screenOnOffReceiver: BroadcastReceiver
    private var screenOnFlag = false


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        registerScreenOnOffReceiver()
        deviceId = generateUniqueDeviceId() // 기기 ID 생성
        createNotificationChannel() // 알림 채널 생성
        startForegroundService() // 포그라운드 서비스 시작
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // 알림 서비스 접근
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    private fun registerScreenOnOffReceiver() {
        screenOnOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOnTime = System.currentTimeMillis()
                        handleScreenOn()
                        Log.d("ScreenReceiver", "Screen ON")

                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOffTime = System.currentTimeMillis()
                        handleScreenOff()
                        Log.d("ScreenReceiver", "Screen OFF")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenOnOffReceiver, filter)
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
            .setSmallIcon(com.example.uxchannel_proto.R.drawable.khu)
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
        if (packageName == "com.kakao.talk") {
            Log.d("NotificationListener", "KakaoTalk Notification: Title=$title, Text=$text")
            handler.postDelayed({
                checkNotificationSeen(sbn)
            }, 60000)
        }
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
        // 알림이 제거될 때 호출
        val packageName = sbn.packageName
        val notificationId = sbn.id
        val key = sbn.key
        val removalReason = parseRemovalReason(reason) // 제거 이유
        val removalTime = formatDate(System.currentTimeMillis())
        val notificationData = notificationsMap[notificationId] // 기존 알림 데이터 참조
        val title = notificationData?.title
        val text = notificationData?.text

        if (sbn.packageName == "com.kakao.talk" && reason == REASON_GROUP_SUMMARY_CANCELED) {
            // 제거된 알림의 정보를 저장
            delayedNotifications[key] = sbn
            handler.postDelayed({
                delayedNotifications[key]?.let {
                    sendDelayedNotification(it)
                    delayedNotifications.remove(key)
                }
            }, 30000)  // 30초 딜레이
        }
        // 파이어베이스에 제거된 알림 데이터 전송
        sendRemovalDataToFirebase(notificationId, packageName, title, text, removalReason, removalTime)
        notificationsMap.remove(notificationId) // 맵에서 제거
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

    private fun sendDelayedNotification(sbn: StatusBarNotification) {
        val channelID = "delayed_channel_id"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelID, "Delayed Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val originalNotification = sbn.notification
        val packageName = sbn.packageName
        val smallIcon = sbn.notification.smallIcon
        val smallIconCompat = IconCompat.createFromIcon(this, smallIcon)


        val title = originalNotification.extras.getString(Notification.EXTRA_TITLE) ?: "Notification"
        val text = originalNotification.extras.getString(Notification.EXTRA_TEXT) ?: "No details available"
        val timestamp = DateFormat.getTimeInstance().format(Date(sbn.postTime))
        // 알림을 클릭할 때 액션 정의
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } ?: Intent() // Fallback to an empty intent if no launch intent is found


        val contentIntent = PendingIntent.getActivity(
            this, sbn.id, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 새로운 알림 생성 및 발송
        val newNotification = smallIconCompat?.let {
            NotificationCompat.Builder(this, channelID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(it) // 아이콘 설정
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .addExtras(Bundle().apply { putBoolean("isDelayedNotification", true) })
                .build()
        }
        notificationManager.notify(sbn.id, newNotification)
    }

    private fun checkNotificationSeen(sbn: StatusBarNotification) {
        // 이벤트 검사를 위해 화면 꺼짐 시간을 미래의 시간으로 예측하여 설정
        val beginTime = sbn.postTime
        val endTime = System.currentTimeMillis()

        Log.d("NotificationListener", "Checking for seen notifications between ${formatDate(beginTime)} and ${formatDate(endTime)} for ${sbn.packageName}")
        val stats = usageStatsManager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()

        while (stats.hasNextEvent()) {
            stats.getNextEvent(event)
            if (event.packageName == sbn.packageName &&
                event.eventType == 10) {
                seenNotifications[sbn.key] = sbn
                Log.d("NotificationListener", "Notification seen: ID=${sbn.id}, Title=${sbn.notification.extras.getString(Notification.EXTRA_TITLE)}, Time=${formatDate(event.timeStamp)}")
                break
            }
        }
    }

    private fun handleScreenOff() {
        screenOnFlag = false

        Log.d("NotificationListener", "Handling screen off. Total seen notifications: ${seenNotifications.size}")
        seenNotifications.forEach { (_, sbn) ->
            if (sbn.packageName == "com.kakao.talk") {
                cancelNotification(sbn.key)
                Log.d("NotificationListener", "KakaoTalk notification canceled and pending for renotification: ${sbn.packageName}, ID: ${sbn.id}")
            }
        }
    }

    private fun handleScreenOn() {
        // 화면이 켜졌을 때만 알림 재전송을 트리거하기 위해 플래그 설정
        if (!screenOnFlag) {
            screenOnFlag = true
            // 모든 보류 중인 알림을 재전송
            pendingNotifications.forEach { (key, sbn) ->
                handler.postDelayed({
                    sendDelayedNotification(sbn)  // StatusBarNotification 객체를 직접 전달
                    pendingNotifications.remove(key)
                }, 10000)  // 10초 딜레이로 설정
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Handler에 예약된 모든 콜백과 메시지 제거
        handler.removeCallbacksAndMessages(null)
        // 포그라운드 서비스 중지
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        // 재시작 인텐트 준비
        unregisterReceiver(screenOnOffReceiver)
    }

}

