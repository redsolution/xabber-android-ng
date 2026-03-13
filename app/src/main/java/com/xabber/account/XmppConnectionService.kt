package com.xabber.account

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.xabber.R
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.stream.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class XmppConnectionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "xmpp_connection"
        private const val CHANNEL_NAME = "XMPP Connection"
    }

    private val supervisorJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var activeAccounts = mutableSetOf<String>() // JIDs of connected accounts

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val jid = intent.getStringExtra("jid") ?: return START_NOT_STICKY
                if (activeAccounts.add(jid)) {
                    updateNotification(activeAccounts.size)
                    startReconnectLoop(jid)
                }
            }
            "STOP" -> {
                val jid = intent.getStringExtra("jid")
                if (jid != null) activeAccounts.remove(jid)
                if (activeAccounts.isEmpty()) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification(activeAccounts.size)
                }
            }
        }
        return START_STICKY // Restart if killed
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(accountCount: Int) {
        val contentText = if (accountCount == 1) "1 account connected" else "$accountCount accounts connected"

        val intent = Intent(this, ApplicationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xabber connected")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_lightbulb) // replace with your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onTimeout(startId: Int) {
        Log.w("XmppConnectionService", "Foreground service timed out, stopping")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startReconnectLoop(jid: String) {
        serviceScope.launch {
            val account = AccountManager.find(jid) ?: return@launch
            while (jid in activeAccounts) {
                if (account.stream == null || account.stream?.state == StreamState.NOT_CONNECTING) {
                    val success = account.connectStream()
                    if (!success) {
                        delay(10_000) // wait 10 seconds before retry
                    }
                }
                delay(5_000) // check every 5 seconds
            }
        }
    }
}