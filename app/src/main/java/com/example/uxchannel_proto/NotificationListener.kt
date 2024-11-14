package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.Person
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Build.VERSION_CODES.R
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.*
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Gravity
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.stopForeground
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.drawable.toIcon
import com.google.firebase.database.FirebaseDatabase
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import com.example.uxchannel_proto.R



class NotificationListener : NotificationListenerService() {
    companion object {
        private const val KEY_FEATURE_ENABLED = "featureEnabled"
    }
   // 각 알림의 데이터를 저장
    // private val notificationsMap = mutableMapOf<Int, NotificationData>()
    private lateinit var deviceId: String // 기기의 고유 ID
    private val notificationChannelId = "NotiServiceChannel" // 알림 채널 ID
//    private val delayedNotifications = mutableMapOf<String, StatusBarNotification>() // 지연된 알림을 저장하는 맵
    private lateinit var notificationManager: NotificationManager // 알림 관리자
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper()) // 핸들러 정의
    private val seenNotifications = mutableMapOf<String, StatusBarNotification>() // 노티바를 본 알림을 저장하는 맵
    private var screenOnTime: Long = 0
    private var screenOffTime: Long = 0
    private val pendingNotifications = mutableMapOf<String, StatusBarNotification>()
    private val storedNotifications = mutableMapOf<String, StatusBarNotification>()
    private lateinit var screenOnOffReceiver: BroadcastReceiver
    private var isReceiverRegistered = false
    private var screenOnFlag = false
    private lateinit var launcherIntentReceiver: BroadcastReceiver

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "NewApi")
    private fun registerLauncherIntentReceiver() {
        launcherIntentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.ACTION_LAUNCHER_ENTRY") {
                    Log.d("NotificationListener", "Received ACTION_LAUNCHER_ENTRY broadcast")
                    handleLauncherEntry()
                }
            }
        }
        val filter = IntentFilter("com.example.ACTION_LAUNCHER_ENTRY")
        registerReceiver(launcherIntentReceiver, filter, Context.RECEIVER_EXPORTED)
        Log.d("NotificationListener", "LauncherIntentReceiver registered")
    }

    private fun unregisterLauncherIntentReceiver() {
        if (::launcherIntentReceiver.isInitialized) {
            unregisterReceiver(launcherIntentReceiver)
            Log.d("NotificationListener", "LauncherIntentReceiver unregistered")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("NotificationListener", "Service bound")
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("NotificationListener", "Service unbound")
        return super.onUnbind(intent)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        try {
            Log.d("NotificationListener", "onCreate called")
            registerScreenOnOffReceiver()
            registerLauncherIntentReceiver()
            deviceId = generateUniqueDeviceId() // 기기 ID 생성
            createNotificationChannel() // 알림 채널 생성
            startForegroundService() // 포그라운드 서비스 시작
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // 알림 서비스 접근
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onCreate: ${e.message}")
        }
    }

    private fun registerScreenOnOffReceiver() {
        screenOnOffReceiver = object : BroadcastReceiver() {
            @RequiresApi(Build.VERSION_CODES.P)
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOnTime = System.currentTimeMillis()
                        handleScreenOn()
                        Log.d("NotificationListener", "Screen ON")

                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOffTime = System.currentTimeMillis()
                        handleScreenOff()
                        Log.d("NotificationListener", "Screen OFF")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenOnOffReceiver, filter)
        isReceiverRegistered = true
        Log.d("NotificationListener", "ScreenOnOffReceiver registered")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelName = "Notification Stats Service Channel"
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel = NotificationChannel(notificationChannelId, channelName, importance)
        channel.description = "Collecting notification stats"

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel) // 채널 생성
        Log.d("NotificationListener", "Notification channel created")
    }

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
            .build()

        // 포그라운드 서비스로 서비스 시작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
        Log.d("NotificationListener", "Foreground service started")
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

    private fun anonymizeText(text: String): String {
        // 텍스트 길이가 10글자 미만인 경우 전체 텍스트 반환
        if (text.length < 13) {
            return text
        }
        // 앞 5글자, 뒤 5글자 유지, 나머지는 별표 처리
        val prefix = text.substring(0, 8)
        val suffix = text.substring(text.length - 5)
        val masked = "*".repeat(text.length - 13)
        return "$prefix$masked$suffix"
    }

    @SuppressLint("NewApi")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
        val packageName = sbn.packageName
        val postTime = formatDate(sbn.postTime)
        val key = sbn.key
        val notificationId = sbn.id
        // Firebase로 데이터 전송
        sendDataToFirebase(sbn.id, packageName, title, text, postTime)


        Log.d("NotificationListener", "Notification posted: Title='$title', Text='$text', Package='$packageName', Time='$postTime'")

        // 특정 채널 또는 시스템 UI에서 오는 알림을 예외 처리
        if (sbn.notification.channelId != "UsageStatsServiceChannel" &&
            sbn.notification.channelId != "NotiServiceChannel" &&
            packageName != "com.google.android.googlequicksearchbox" &&
            packageName != "com.samsung.android.app.routines" &&
            packageName != "com.android.systemui" &&
            packageName != "com.sec.android.demonapp" &&
            packageName != "com.sec.android.app.shealth" &&
            packageName != "com.sec.android.systemui" &&
            packageName != "com.ahnlab.v3mobileplus" &&
            packageName != "viva.republica.toss" &&
            packageName != "com.xiaomi.wearable" &&
            packageName != "com.todayissue.io" &&
            packageName != "com.estsoft.alyac" &&
            packageName != "com.samsung.android.spay") {
            val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)

            // 알림을 저장
            if (isFeatureEnabled && notificationId != 0) {
                pendingNotifications.entries.removeIf { it.value.notification.extras.getString(Notification.EXTRA_TITLE) == title }
                if (storedNotifications.containsKey(key)) {
                    Log.d("NotificationListener", "Updating existing notification for sender: $title")
                    storedNotifications[key] = sbn
                } else {
                    Log.d("NotificationListener", "Storing new notification for sender: $title")
                    storedNotifications[key] = sbn
                }
            } else {
                Log.d("NotificationListener", "Feature not enabled, not storing notification from: $packageName")
            }
        } else {
            Log.d("NotificationListener", "Notification from excluded channel or system UI, not sending to Firebase: $packageName")
        }
    }

    // Firebase에 데이터 전송
    @SuppressLint("HardwareIds")
    private fun sendDataToFirebase(notificationId: Int, packageName: String, title: String?, text: String?, postTime: String) {
        if (packageName != "com.android.systemui") { // systemui 패키지 제외
            val anonymizedText = text?.let { anonymizeText(it) } ?: "No Text"
            val uniqueKey = "$notificationId-$postTime"
            val notificationData = hashMapOf(
                "deviceId" to deviceId,
                "package_name" to packageName,
                "notification_id" to notificationId,
                "post_time" to postTime,
                "title" to title,
                "text" to anonymizedText
            )

            // Firebase Firestore에 데이터 저장
            val database = FirebaseDatabase.getInstance().getReference("notifications")
            database.child(uniqueKey).setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("NotificationListener", "Data successfully written to Realtime Database.")
                }
                .addOnFailureListener { e ->
                    Log.w("NotificationListener", "Error writing document to Realtime Database", e)
                }
        }
    }

    // 알림이 제거될때 호출
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        try {
            val packageName = sbn.packageName
            val notificationId = sbn.id
            val key = sbn.key
            val removalReason = parseRemovalReason(reason) // 제거 이유
            val removalTime = formatDate(System.currentTimeMillis())
            // val notificationData = notificationsMap[notificationId] // 기존 알림 데이터 참조
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
            val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
            Log.d("NotificationListener", "Notification removed: ID=$notificationId, Title='$title', Text='$text', Package='$packageName', Reason='$removalReason', Removal Time='$removalTime'")

            sendRemovalDataToFirebase(notificationId, packageName, title, text, removalReason, removalTime)
            // notificationsMap.remove(notificationId) // 맵에서 제거
            if (reason != REASON_LISTENER_CANCEL || storedNotifications.values.any { it.id == notificationId }) {
                storedNotifications.remove(key)
                seenNotifications.remove(key)
                // 알림 데이터를 seenNotifications와 storedNotifications 맵에서 제거
                Log.d("NotificationListener", "Removed notification from stored and seen lists: $key")
            }
            else{
                Log.d("NotificationListener", "??: $key")

            }
        } catch (e: DeadObjectException) {
            Log.e("NotificationListener", "DeadObjectException: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onNotificationRemoved: ${e.message}")
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
            val anonymizedText = text?.let { anonymizeText(it) } ?: "No Text"
            val uniqueKey = "$notificationId-$removalTime"
            val removalData = hashMapOf(
                "deviceId" to deviceId,
                "package_name" to packageName,
                "notification_id" to notificationId,
                "title" to title,
                "text" to anonymizedText,
                "removal_reason" to reason,
                "removal_time" to removalTime
            )

            // Firebase Firestore에 데이터 저장
            val database = FirebaseDatabase.getInstance().getReference("notification_removals")
            database.child(uniqueKey).setValue(removalData)
                .addOnSuccessListener {
                    Log.d("NotificationListener", "Removal data successfully written to Realtime Database.")
                }
                .addOnFailureListener { e ->
                    Log.w("NotificationListener", "Error writing removal data to Realtime Database", e)
                }
        }
    }

//    private fun iconToBitmap(icon: Icon, context: Context): Bitmap? {
//        val drawable = icon.loadDrawable(context)
//        if (drawable is BitmapDrawable) {
//            return drawable.bitmap
//        } else {
//            val bitmap = drawable?.let {
//                Bitmap.createBitmap(
//                    it.intrinsicWidth,
//                    drawable.intrinsicHeight,
//                    Bitmap.Config.ARGB_8888
//                )
//            }
//            val canvas = bitmap?.let { Canvas(it) }
//            if (canvas != null) {
//                drawable.setBounds(0, 0, canvas.width, canvas.height)
//            }
//            if (canvas != null) {
//                drawable.draw(canvas)
//            }
//            return bitmap
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendDelayedNotification(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: "Unknown"
        val uniqueNotificationId = title.hashCode()  // TITLE만을 기반으로 고유 ID 생성
        val channelID = "delayed_channel_id"
        //val groupKey = "com.example.notifications.GROUP_KEY"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelID, "Delayed Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val originalNotification = sbn.notification
        val extras = originalNotification.extras
        val smallIcon = originalNotification.smallIcon
        // val largeIcon = originalNotification.getLargeIcon()
        val smallIconCompat = IconCompat.createFromIcon(this, smallIcon)
        // val largeIconBitmap = largeIcon?.let { iconToBitmap(it, this) }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: "No details available"
        // 알림을 클릭할 때 액션 정의
        val contentIntent = originalNotification.contentIntent
        // 발신자 Person 객체 생성
        //val senderIconCompat = IconCompat.createFromIcon(this, largeIcon)
//        val sender = Person.Builder()
//            .setName(senderName)
//            .setIcon(senderIconCompat)
//            .build()

        // MessagingStyle 초기화
//        val messagingStyle = NotificationCompat.MessagingStyle(sender)
//            .setConversationTitle(title)
//            .addMessage(text, System.currentTimeMillis(),sender)

        // 새로운 알림 생성 및 발송
        val newNotification = smallIconCompat?.let {
            NotificationCompat.Builder(this, channelID)
                //.setStyle(messagingStyle)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(it)
                //.setLargeIcon(largeIconBitmap)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addExtras(Bundle().apply { putBoolean("isDelayedNotification", true) })
                .setExtras(Bundle(extras))
                .build()
        }
        notificationManager.notify(uniqueNotificationId, newNotification)
    }

    private fun checkNotificationSeen(sbn: StatusBarNotification) {
        val beginTime = sbn.postTime
        val endTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var seenFlag = false  // 알림이 확인되었는지 추적

        while (stats.hasNextEvent()) {
            stats.getNextEvent(event)
            if (event.packageName == sbn.packageName) {
                when (event.eventType) {
                    10 -> {  // NOTIFICATION_SEEN, 알림이 확인됨
                        seenNotifications[sbn.key] = sbn
                        seenFlag = true
                        Log.d("NotificationListener", "Notification seen: ID=${sbn.id}, Title=${sbn.notification.extras.getString(Notification.EXTRA_TITLE)}, Time=${formatDate(event.timeStamp)}")
                    }
                    7 -> {  // USER_INTERACTION, 사용자 상호작용
                        if (seenFlag) {  // 알림이 이미 확인된 경우
                            storedNotifications.remove(sbn.key)  // 저장된 알림 목록에서 제거
                            seenNotifications.remove(sbn.key)
                            Log.d("NotificationListener", "User interacted with the app, removed from stored: ${sbn.packageName}, ID: ${sbn.id}")
                            break  // 추가 이벤트 확인 중단
                        }
                    }
                }
            }
        }
    }

    private fun handleScreenOff() {
        screenOnFlag = false
        // 기능이 활성화된 경우에만 알림을 처리
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)

        if (isFeatureEnabled) {
            storedNotifications.forEach { (key, sbn) ->
                if (!seenNotifications.containsKey(key)) {  // Only check if not already seen
                    checkNotificationSeen(sbn)
                }
            }
            Log.d("NotificationListener", "Handling screen off. Total seen notifications: ${seenNotifications.size}")

            seenNotifications.forEach { (key, sbn) ->
                cancelNotification(key)  // 모든 확인된 알림을 취소
                Log.d("NotificationListener", "Notification canceled and pending for renotification: ${sbn.packageName}, ID: ${sbn.id}")
                pendingNotifications[key] = sbn  // 모든 확인된 알림을 재전송 준비
            }

            Log.d("NotificationListener", "Current pendingNotifications: ${pendingNotifications.keys.joinToString()}")
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("launcherEntryCount", 0).apply()
            Log.d("NotificationListener", "Launcher entry count reset to 0")
            Log.d("NotificationListener", "Current storedNotifications: ${storedNotifications.keys.joinToString()}")
        }
    }

    private fun handleScreenOn() {
        if (!screenOnFlag) {
            screenOnFlag = true
        }
    }

    @SuppressLint("NewApi")
    private fun handleLauncherEntry() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var launcherEntryCount = prefs.getInt("launcherEntryCount", 0)
        Log.d("NotificationListener", "Launcher entry count: $launcherEntryCount")
        Log.d("NotificationListener", "Pending notifications before processing: ${pendingNotifications.keys.joinToString()}")



        // 보류 중인 알림을 반복 처리
        val iterator = pendingNotifications.entries.iterator()
        while (launcherEntryCount > 0 && iterator.hasNext()) {
            val (key, sbn) = iterator.next()
            val notificationId = sbn.id

            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)

            if (!title.isNullOrEmpty()) { // 타이틀이 있는 알림만 재전송
                Log.d("NotificationListener", "Resending notification: $key with title: $title")
                sendDelayedNotification(sbn)
                iterator.remove() // 제대로 알림을 제거
                launcherEntryCount -= 1 // 카운트 감소
                prefs.edit().putInt("launcherEntryCount", launcherEntryCount).apply()
                Log.d("NotificationListener", "Notification re-sent and launcher entry count decremented")
            } else {
                iterator.remove() // 타이틀 없는 알림 제거
                Log.d("NotificationListener", "Skipping notification without title: $key")
            }
        }

        Log.d("NotificationListener", "Pending notifications after processing: ${pendingNotifications.keys.joinToString()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d("NotificationListener", "Service destroyed")
            // Handler에 예약된 모든 콜백과 메시지 제거
            handler.removeCallbacksAndMessages(null)
            // 포그라운드 서비스 중지
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            // 리시버 등록 여부 확인 후 해제
            if (isReceiverRegistered) {
                unregisterReceiver(screenOnOffReceiver)
                isReceiverRegistered = false
            }
            unregisterLauncherIntentReceiver()
        } catch (e: IllegalArgumentException) {
            Log.e("NotificationListener", "Receiver not registered: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onDestroy: ${e.message}")
        }
    }

}

