package com.xabber.presentation

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.disposables.CompositeDisposable

abstract class BaseFragment (@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId)