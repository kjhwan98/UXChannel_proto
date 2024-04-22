package com.example.uxchannel_proto

import android.annotation.SuppressLint
import android.app.Notification
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.*
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val packageName = sbn.packageName
        val postTime = sbn.postTime

        // Firebase에 데이터 전송
        sendDataToFirebase(sbn.id, packageName, title, text, postTime)
    }

    @SuppressLint("HardwareIds")
    private fun sendDataToFirebase(notificationId: Int, packageName: String, title: String?, text: String?, postTime: Long) {
        val notificationData = hashMapOf(
            "device_id" to Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
            "package_name" to packageName,
            "notification_id" to notificationId,
            "post_time" to postTime,
            "title" to title,
            "text" to text
        )

        // Firebase Firestore에 데이터 저장
        val database = FirebaseDatabase.getInstance().getReference("notifications")
        database.child(notificationId.toString()).setValue(notificationData)
            .addOnSuccessListener {
                Log.d("Firebase", "Data successfully written to Realtime Database.")
            }
            .addOnFailureListener { e ->
                Log.w("Firebase", "Error writing document to Realtime Database", e)
            }

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val packageName = sbn.packageName
        val notificationId = sbn.id
        val removalReason = parseRemovalReason(reason)
        val removalTime = System.currentTimeMillis()

        // Firebase에 데이터 전송
        sendRemovalDataToFirebase(notificationId, packageName, removalReason, removalTime)
    }

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
    private fun sendRemovalDataToFirebase(notificationId: Int, packageName: String, reason: String, removalTime: Long) {
        val removalData = hashMapOf(
            "device_id" to Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
            "package_name" to packageName,
            "notification_id" to notificationId,
            "removal_reason" to reason,
            "removal_time" to removalTime
        )

        // Firebase Firestore에 데이터 저장
        val database = FirebaseDatabase.getInstance().getReference("notification_removals")
        database.child(notificationId.toString()).setValue(removalData)
            .addOnSuccessListener {
                Log.d("Firebase", "Removal data successfully written to Realtime Database.")
            }
            .addOnFailureListener { e ->
                Log.w("Firebase", "Error writing removal data to Realtime Database", e)
            }
    }

}