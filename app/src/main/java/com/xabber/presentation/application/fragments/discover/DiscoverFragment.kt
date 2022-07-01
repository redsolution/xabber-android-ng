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
        setCameraProviderListener()
        binding.tvDiscover.setOnClickListener { navigator().showBottomSheetDialog(Test()) }
    }

    fun setCameraProviderListener() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            } catch (e: Exception) {

            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    fun bindPreview(cameraProvider: ProcessCameraProvider) {


        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(binding.previewCamera.display.rotation).build()
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        preview.setSurfaceProvider(binding.previewCamera.surfaceProvider)
        val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).build()
        cameraProvider.bindToLifecycle(this, cameraSelector, preview)


    }


}