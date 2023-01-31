package com.xabber.presentation.application.fragments.account

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.models.dto.AccountDto
import com.xabber.models.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.fragments.chatlist.ChatListAdapter
import com.xabber.utils.mask.MaskedDrawable
import com.xabber.utils.mask.MaskedDrawableBitmapShader

class AccountViewHolder(
    private val binding: ItemAccountForPreferenceBinding,
    private val onItemClick: (AccountDto) -> Unit, private val onSwitchClick: (AccountDto) -> Unit
) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(account: AccountDto) {
        binding.tvItemAccountName.text = account.nickname
        binding.tvItemAccountJid.text = account.jid
        val avatar = UiChanger.getAvatar()
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(binding.root.context).load(avatar).error(R.drawable.ic_avatar_placeholder)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatarItemAccount)
        //   binding.imAvatarItemAccount.setImageDrawable(getAvatarWithMask(account.))
        binding.root.setOnClickListener { onItemClick(account) }
        binding.switchAccountEnable.isChecked = account.enabled
        binding.switchAccountEnable.setOnClickListener {
            onSwitchClick(account)
        }
    }

    fun bind(
       account: AccountDto,
        payloads: List<Any>
    ) {
        val bundle = payloads.last() as Bundle
        for (key in bundle.keySet()) {
            when (key) {
                "enabled" -> {
                    val enable = bundle.getBoolean("enabled")
                    binding.switchAccountEnable.isChecked = enable
                }
                "avatar" -> {
                    val avatar = UiChanger.getAvatar()
                    val multiTransformation = MultiTransformation(CircleCrop())
                    Glide.with(binding.root.context).load(avatar)
                        .error(R.drawable.ic_avatar_placeholder)
                        .apply(RequestOptions.bitmapTransform(multiTransformation))
                        .into(binding.imAvatarItemAccount)
                }
            }
        }
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
