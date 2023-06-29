package com.xabber.presentation

import android.app.Application
import android.content.Context
import com.xabber.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

class XabberApplication : Application() {

    companion object {
        private lateinit var instance: XabberApplication
        fun applicationContext(): Context {
            return instance.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        GlobalContext.startKoin {
            androidContext(applicationContext())
            modules(dataModule)
        }
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
