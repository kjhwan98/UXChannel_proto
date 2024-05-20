package com.example.uxchannel_proto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "RestartService") {
            context.startService(Intent(context, NotificationListener::class.java))
        }
    }
}