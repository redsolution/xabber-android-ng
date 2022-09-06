package com.xabber.presentation.application.fragments.discover

import android.os.Bundle
import android.util.Log
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDiscoverBinding
import com.xabber.presentation.BaseFragment
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers.newThread
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

class DiscoverFragment : BaseFragment(R.layout.fragment_discover) {
    private val binding by viewBinding(FragmentDiscoverBinding::bind)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val observable = Observable.just(1, 2, 3)
        val flowable = Flowable.just("y", "o", "i")
        val single = Single.just(1)
        val dispose: Disposable = dataSource().subscribeOn(newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe {
            Log.d(
                "tag",
                "$it"
            )
        }
    }

    private fun dataSource(): Observable<Int> {
        return Observable.create { subscriber ->
            for (i in 0..100) {
                subscriber.onNext(i)
            }
        }
    }
}
