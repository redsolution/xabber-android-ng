package com.xabber.presentation.application.fragments.calls

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator

class CallsFragment : BaseFragment(R.layout.fragment_calls) {
    private val binding by viewBinding(FragmentCallsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
         binding.appbarCalls.setPadding(0, DisplayManager.getHeightStatusBar(),0, 0)
        loadAvatarWithMask()
        binding.tvAdt.movementMethod = LinkMovementMethod.getInstance()
        binding.imAvatar.setOnClickListener {
            navigator().showAccount(
                Account(
                    "Natalia Barabanshikova",
                    "Natalia Barabanshikova",
                    "natalia.barabanshikova@redsolution.com",
                    R.color.blue_100,
                    R.drawable.img, 1
                )
            )
        }
    }

    private fun loadAvatarWithMask() {
        val mPictureBitmap = BitmapFactory.decodeResource(resources, R.drawable.img)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, UiChanger.getMask().size32).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        binding.imAvatar.setImageDrawable(maskedDrawable)
    }

}