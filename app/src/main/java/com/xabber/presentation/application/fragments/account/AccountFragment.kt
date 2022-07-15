package com.xabber.presentation.application.fragments.account

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.showToast


class AccountFragment : DetailBaseFragment(R.layout.fragment_account) {
    private val binding by viewBinding(FragmentAccountBinding::bind)
    private var account: Account? = null

    companion object {
        fun newInstance(_account: Account) = AccountFragment().apply {
            account = _account
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeUiWithAccountData()
        initToolbarActions()
    }

    private fun changeUiWithAccountData() {
        binding.mainCollapsing?.title = account?.name
        val mPictureBitmap = BitmapFactory.decodeResource(resources, account!!.avatar)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, UiChanger.getMask().size176).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        binding.accountPhoto?.setImageDrawable(maskedDrawable)
    }

    private fun initToolbarActions() {
        binding.accountToolbar?.setNavigationIcon(R.drawable.ic_plus_white)
        binding.accountToolbar?.setNavigationOnClickListener {
            showToast(
                "This feature is not implemented"
            )
        }
        binding.imQrCodeGenerate?.setOnClickListener {
            showToast("This feature is not implemented")
        }
        binding.imAccountColor?.setOnClickListener {
            val dialog = AccountColorDialog()
            navigator().showDialogFragment(dialog)
        }
    }


}