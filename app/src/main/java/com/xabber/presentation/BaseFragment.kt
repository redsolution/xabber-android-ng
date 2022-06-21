package com.xabber.presentation

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

abstract class BaseFragment (@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId)