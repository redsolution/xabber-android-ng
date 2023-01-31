package com.xabber.presentation.application.fragments.account.reorder

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.models.xmpp.account.Account
import com.xabber.databinding.ItemAccountForReorderBinding
import com.xabber.utils.mask.MaskedDrawable
import com.xabber.utils.mask.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger

class ReorderAccountViewHolder(private val binding: ItemAccountForReorderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(account: Account) {
        binding.imAccountAnchor.isVisible = true
        binding.tvItemAccountName.text = account.name
        binding.tvItemAccountJid.text = account.jid

        val avatar = UiChanger.getAvatar()
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(binding.root.context).load(avatar).error(R.drawable.ic_avatar_placeholder)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatarItemAccount)
    }

    fun getImAnchor(): ImageView = binding.imAccountAnchor

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
