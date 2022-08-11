package com.xabber.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import io.reactivex.rxjava3.disposables.CompositeDisposable

abstract class BaseFragment (@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId)