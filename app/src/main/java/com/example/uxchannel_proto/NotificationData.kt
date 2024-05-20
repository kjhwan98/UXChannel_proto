package com.example.uxchannel_proto

import android.app.PendingIntent
import android.graphics.drawable.Icon

data class NotificationData(
    var id: Int,
    var title: String?,
    var text: String?,
    val postTime: String,
    val icon: Icon, // 아이콘 리소스 ID를 저장
    val packageName: String,
    val contentIntent: PendingIntent
)