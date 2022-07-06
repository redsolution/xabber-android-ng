package com.xabber.presentation.application.fragments.discover

import android.os.Bundle
import android.view.View
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDiscoverBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.chat.Test

class DiscoverFragment : BaseFragment(R.layout.fragment_discover) {
    private val binding by viewBinding(FragmentDiscoverBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDiscover.setOnClickListener { navigator().showBottomSheetDialog(Test()) }
    }






}