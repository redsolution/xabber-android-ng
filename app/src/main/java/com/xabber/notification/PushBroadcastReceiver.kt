package com.xabber.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
//import com.xabber.notification.PushService
//import com.xabber.presentation.XabberApplication

//class PushBroadcastReceiver : BroadcastReceiver() {
//    override fun onReceive(p0: Context?, intent: Intent?) {
//        val extras = intent?.extras
//        Log.d("keyintent", "messageReceive")
//        extras?.keySet()?.firstOrNull {
//            it == PushService.KEY_ACTION
//        }?.let { key ->
//            when (extras.getString(key)) {
//                PushService.ACTION_SHOW_MESSAGE -> {
//                    extras.getString(PushService.KEY_MESSAGE)?.let { message ->
//                        Log.d("keyintent", "messageReceive")
//                        Toast.makeText(
//                            XabberApplication.newInstance().baseContext,
//                           " message",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                }
//                else -> {
//                    Log.d("keyintent", "no key")
//                }
//            }
//        }
//
//    }
//
//}
