package com.xabber.presentation

import android.app.Application
import android.util.Log
import com.xabber.defaultRealmConfig
import io.realm.Realm


class XabberApplication : Application() {

    companion object {
        fun newInstance() = XabberApplication()
        val realm = Realm.open(defaultRealmConfig())
    }

    override fun onCreate() {
        super.onCreate()
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