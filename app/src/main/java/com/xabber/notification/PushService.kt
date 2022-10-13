package com.xabber.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.xabber.R
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.utils.mask.Mask
import com.xabber.utils.mask.MaskPrepare
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(
            "keyintent",
            " message =${message}, message.data.values =${message.data.values}, message.notification =${message.notification?.body}, ${message.data["Nick"]}"
        )
        val title = message.data["Nick"]
        val body = message.data["body"]
//        val intent = Intent(INTENT_FILTER)
//        message.data.forEach { entity ->
//            intent.putExtra(entity.key, entity.value)
//        }
//        sendBroadcast(intent)
        var bit: Bitmap? = null
         try {
        val url = URL("https://akspic.ru/image/169092-sipuha-neyasyt-ptica-klyuv-naturalnyj_material")
       val connection = url.openConnection()
        connection.setDoInput(true);
        connection.connect();
       val input = connection.getInputStream();
       val bit = BitmapFactory.decodeStream(input);

    } catch (e: IOException ) {
        // Log exception
    }
val resultIntent = Intent(this, ApplicationActivity::class.java)
        resultIntent.putExtra("redirect", "homefragment")
val resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent,
               PendingIntent.FLAG_UPDATE_CURRENT)

        Log.d("keyintent", "$bit")
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.img)
        createNotificationChannel()
        val builder = NotificationCompat.Builder(applicationContext, "1")
            .setSmallIcon(R.drawable.ic_id_outline).setColor(Color.RED).setContentTitle(title)
            .setContentText(body).setLargeIcon(bitmap).setContentIntent(resultPendingIntent).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
        val notification = builder.build()
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.project_id)
            val descriptionText = getString(R.string.description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("1", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    companion object {
        const val INTENT_FILTER = "PUSH_EVENT"
        const val KEY_ACTION = "action"
        const val KEY_MESSAGE = "message"
        const val ACTION_SHOW_MESSAGE = "show message"
    }

}