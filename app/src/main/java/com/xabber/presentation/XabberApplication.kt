package com.xabber.presentation

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.xabber.account.AccountManager
import com.xabber.di.dataModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        registerForegroundReconnectObserver()
    }

    private fun registerForegroundReconnectObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d("XabberApplication", "App moved to foreground – checking accounts")
                AccountManager.users.forEach { account ->
                    if (!account.isConnected()) {
                        Log.d("XabberApplication", "Account ${account.jid} is disconnected, scheduling reconnect")
                        CoroutineScope(Dispatchers.IO).launch {
                            account.performReconnect()
                        }
                    }
                }
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
