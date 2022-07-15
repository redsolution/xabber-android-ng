package com.xabber.presentation.application.fragments.account

import android.graphics.BitmapFactory
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.presentation.application.activity.MaskedDrawable
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger

class ReorderAccountViewHolder(private val binding: ItemAccountForPreferenceBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(account: Account) {
        binding.imAccountAnchor.isVisible = true
         binding.tvItemAccountName.text = account.name
        binding.tvItemAccountJid.text = account.jid
        binding.imAvatarItemAccount.setImageDrawable(getAvatarWithMask(account.avatar))
        binding.root.setOnClickListener {  }
    }

    private fun getAvatarWithMask(accountResId: Int): MaskedDrawable {
        val mPictureBitmap =
            BitmapFactory.decodeResource(binding.root.context.resources, accountResId)
        val mMaskBitmap =
            BitmapFactory.decodeResource(binding.root.context.resources, UiChanger.getMask().size48)
                .extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        return maskedDrawable
    }
}