package com.xabber.onboarding.fragments

import android.util.Log
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.disposables.CompositeDisposable


abstract class BaseFragment() : Fragment() {

    protected val compositeDisposable = CompositeDisposable()

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }


    protected fun logError(e: Throwable) {
        // Toast.makeText(context, "Произошла ошибка", Toast.LENGTH_SHORT).show()
        Log.e("ERR", e.stackTraceToString())
    }
}
