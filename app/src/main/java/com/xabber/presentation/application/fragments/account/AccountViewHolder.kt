package com.xabber.presentation.application.fragments.account

import android.graphics.BitmapFactory
import androidx.recyclerview.widget.RecyclerView
import com.xabber.model.xmpp.account.Account
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.utils.mask.MaskedDrawable
import com.xabber.utils.mask.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger

class AccountViewHolder(
    private val binding: ItemAccountForPreferenceBinding,
    private val onAccountClick: (Account) -> Unit
) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(account: Account) {
        binding.tvItemAccountName.text = account.name
        binding.tvItemAccountJid.text = account.jid
        binding.imAvatarItemAccount.setImageDrawable(getAvatarWithMask(account.avatar))
        binding.root.setOnClickListener { onAccountClick(account) }
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
