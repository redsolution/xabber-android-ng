package com.xabber.presentation.application.fragments.account

import android.graphics.BitmapFactory
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.presentation.application.activity.MaskedDrawable
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger

class AccountViewHolder(private val binding: ItemAccountForPreferenceBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun initAccount(account: Account) {
        binding.tvItemAccountName.text = account.name
        binding.tvItemAccountJid.text = account.jid

        binding.imAvatarItemAccount.setImageDrawable(getAvatarWithMask())
    }

    private fun getAvatarWithMask(): MaskedDrawable {
        val mPictureBitmap = BitmapFactory.decodeResource(binding.root.resources, R.drawable.img)
        val mMaskBitmap =
            BitmapFactory.decodeResource(binding.root.resources, UiChanger.getMask().size48)
                .extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        return maskedDrawable
    }
}