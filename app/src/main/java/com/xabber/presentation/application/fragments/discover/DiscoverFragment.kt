package com.xabber.presentation.application.fragments.discover

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDiscoverBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger

class DiscoverFragment : BaseFragment(R.layout.fragment_discover) {
    private val binding by viewBinding(FragmentDiscoverBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appbar.setPadding(0, DisplayManager.getHeightStatusBar(),0, 0)
        updateMaskAvatar()
    }

    private fun updateMaskAvatar() {
        val mPictureBitmap = BitmapFactory.decodeResource(resources, R.drawable.img)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, UiChanger.getMask().size32).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
    }
}
