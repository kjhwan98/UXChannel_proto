package com.example.uxchannel_proto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)
            result?.transitionEvents?.forEach { event ->
                handleActivityTransitionEvent(context, event)
            }
        }
    }

    private fun handleActivityTransitionEvent(context: Context, event: ActivityTransitionEvent) {
        val activityType = getActivityString(event.activityType)
        // 전역 변수 업데이트
        val serviceIntent = Intent(context, UsageStatsService::class.java).apply {
            action = "com.example.app.UPDATE_ACTIVITY"
            putExtra("activityType", activityType)
        }
        context.startService(serviceIntent)

        Log.d("ActivityTransition", "Activity: $activityType")
    }

    private fun getActivityString(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            DetectedActivity.ON_BICYCLE -> "On Bicycle"
            DetectedActivity.ON_FOOT -> "On Foot"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.TILTING -> "Tilting"
            DetectedActivity.UNKNOWN -> "Unknown"
            DetectedActivity.WALKING -> "Walking"
            else -> "Other"
        }
    }
}