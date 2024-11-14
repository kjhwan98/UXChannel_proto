package com.example.uxchannel_proto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class FirebaseDataSenderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPreferences = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val serviceOnTime = sharedPreferences.getLong(MainActivity.KEY_SERVICE_ON_TIME, 0)
        val serviceOffTime = sharedPreferences.getLong(MainActivity.KEY_SERVICE_OFF_TIME, 0)
        val deviceId = sharedPreferences.getString("deviceId", "No Device ID")

        // 시간을 포맷하여 전송
        val formattedOnTime = formatDuration(serviceOnTime)
        val formattedOffTime = formatDuration(serviceOffTime)

        // Firebase로 데이터 전송
        sendDataToFirebase(formattedOnTime, formattedOffTime, deviceId)
    }

    private fun sendDataToFirebase(onTime: String, offTime: String, deviceId: String?) {
        val databaseReference = FirebaseDatabase.getInstance().getReference("ServiceTimes")
        val data = hashMapOf(
            "Service_onTime" to onTime,
            "Service_offTime" to offTime,
            "deviceId" to deviceId
        )
        databaseReference.push().setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FirebaseDataSender", "Data successfully sent to Firebase.")
            } else {
                Log.e("FirebaseDataSender", "Failed to send data to Firebase.", task.exception)
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val days = millis / (24 * 3600000)
        val hours = (millis / 3600000) % 24
        val minutes = (millis / 60000) % 60
        return String.format("%d days, %02d:%02d", days, hours, minutes)
    }
}