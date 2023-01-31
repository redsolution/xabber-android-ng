package com.xabber.presentation

import android.app.Application
import android.content.Context
import android.util.Log

class XabberApplication : Application() {

    companion object {
        private var instance: XabberApplication? = null

        private var isActive: Boolean = false

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }


    override fun onCreate() {
        Log.d("all", "onCreate")
        super.onCreate()
        instance = this
        val context: Context = XabberApplication.applicationContext()

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