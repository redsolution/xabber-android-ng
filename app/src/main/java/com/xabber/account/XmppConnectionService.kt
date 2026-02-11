package com.xabber.account

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.stream.StreamState
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XmppConnectionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "xmpp_connection"
        private const val CHANNEL_NAME = "XMPP Connection"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeAccounts = mutableSetOf<String>() // JIDs of connected accounts

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        serviceScope.launch(Dispatchers.IO) {
            val realm = Realm.open(defaultRealmConfig())
            try {
                val enabledAccounts = realm.query<AccountStorageItem>("enabled = true").find()
                enabledAccounts.forEach { accountItem ->
                    val jid = accountItem.jid
                    if (activeAccounts.add(jid)) {
                        withContext(Dispatchers.Main) {
                            updateNotification(activeAccounts.size)
                        }
                        startReconnectLoop(jid)
                    }
                }
                if (activeAccounts.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        updateNotification(activeAccounts.size)
                    }
                } else {
                    stopSelf() // No accounts → no need to run
                }
            } finally {
                realm.close()
            }
        }
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
                    stopForeground(true)
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

        startForeground(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startReconnectLoop(jid: String) {
        serviceScope.launch {
            val account = AccountManager.find(jid) ?: return@launch

            // Monitor connection state
            while (jid in activeAccounts) {
                try {
                    // Check if stream is in a bad state
                    val streamState = account.stream?.state
                    val isConnected = streamState == StreamState.CONNECTED
                    val isConnecting = streamState in listOf(
                        StreamState.STREAM_OPEN,
                        StreamState.START_TLS,
                        StreamState.START_AUTH,
                        StreamState.PROCESS_AUTH,
                        StreamState.BINDING
                    )

                    if (!isConnected && !isConnecting && !account.reconnectionManager.isReconnecting()) {
                        Log.d("Xmppconnectservice", "Account $jid is disconnected, triggering reconnection")
                        account.reconnectionManager.scheduleReconnection("background monitor")
                    }

                    // Send ping to keep connection alive (if connected)
                    if (isConnected) {
                        account.stream?.socket?.sendPing(jid)
                    }

                    delay(30000) // Check every 30 seconds

                } catch (e: Exception) {
                    Log.e("Xmppconnectservice", "Error in reconnect loop for $jid: ${e.message}")
                    delay(10000)
                }
            }
        }
    }
}