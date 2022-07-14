package com.xabber.presentation.application.fragments.account

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.util.showToast


class AccountFragment : DetailBaseFragment(R.layout.fragment_account) {
    private val binding by viewBinding(FragmentAccountBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.accountToolbar?.setNavigationIcon(R.drawable.ic_plus_white)
        binding.accountToolbar?.setNavigationOnClickListener { showToast(
            "This feature is not implemented") }
        val mPictureBitmap = BitmapFactory.decodeResource(resources, R.drawable.img)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, UiChanger.getMask().size176).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        binding.accountPhoto?.setImageDrawable(maskedDrawable)
        binding.imAccountColor?.setOnClickListener {
            val dialog = AccountColorDialog()
            navigator().showDialogFragment(dialog)
        }
    }


}