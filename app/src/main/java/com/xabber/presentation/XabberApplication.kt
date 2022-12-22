package com.xabber.presentation

import android.app.Application
import android.util.Log
import com.xabber.presentation.application.activity.MessagesReadMarker


class XabberApplication : Application() {

    companion object {
        fun newInstance() = XabberApplication()



    }

    override fun onCreate() {
        Log.d("all", "onCreate")
        super.onCreate()
        val a = MessagesReadMarker
      //  a.startCounter()
    }
}
     //   FirebaseApp.initializeApp(applicationContext)
////
////        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
////            if (!task.isSuccessful) {
////                return@addOnCompleteListener
////            }
////
////            val token = task.result
////            Log.d("TAG", "token: $token")
////        }
//    }
//}
////