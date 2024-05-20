package com.example.uxchannel_proto

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnableNotificationAccess = findViewById<Button>(R.id.btnEnableNotificationAccess)
        val btnEnableUsageAccess = findViewById<Button>(R.id.btnEnableUsageAccess)

        btnEnableNotificationAccess.setOnClickListener {
            if (!isNotificationServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                Toast.makeText(this, "Notification access is already enabled.", Toast.LENGTH_SHORT).show()
                startNotificationListenerService()
            }
        }

        btnEnableUsageAccess.setOnClickListener {
            if (!isUsageStatsPermissionGranted()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(this, "Please allow usage access for the app.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Usage access is already enabled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        checkAndInformPermissions()
    }

    @SuppressLint("NewApi")
    private fun checkAndInformPermissions() {
        if (isNotificationServiceEnabled()) {
            Toast.makeText(this, "Notification access is enabled.", Toast.LENGTH_SHORT).show()
            startNotificationListenerService()
        } else {
            Toast.makeText(this, "Please enable notification access.", Toast.LENGTH_SHORT).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isUsageStatsPermissionGranted()) {
                Toast.makeText(this, "Usage access is enabled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enable usage stats access.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, NotificationListener::class.java).flattenToString()
        return enabledListeners?.contains(componentName) ?: false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startNotificationListenerService() {
        val serviceIntent = Intent(this, NotificationListener::class.java)
        startForegroundService(serviceIntent)
        Toast.makeText(this, "Starting Notification Listener Service...", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = applicationContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        appOps?.let {
            val mode = it.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
        return false
    }
}