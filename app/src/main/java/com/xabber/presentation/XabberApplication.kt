package com.xabber.presentation

import android.app.Application
import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class XabberApplication : Application() {
    private var backgroundExecutorForUserActions: ExecutorService? = null

    companion object {
        private var instance: XabberApplication? = null

        private var isActive: Boolean = false

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }


    override fun onCreate() {
        super.onCreate()
        instance = this
        val context: Context = XabberApplication.applicationContext()

      //  a.startCounter()
    }



    fun runInBackgroundUserRequest(runnable: Runnable) {
        backgroundExecutorForUserActions?.submit(Runnable {
            try {
                runnable.run()
            } catch (e: Exception) {

            }
        })
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