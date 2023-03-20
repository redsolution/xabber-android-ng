package com.xabber.presentation.application.fragments.account

import android.os.Bundle
import android.util.Log
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.models.dto.AccountDto
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.activity.MaskManager
import io.realm.kotlin.Realm

class AccountViewHolder(
    private val binding: ItemAccountForPreferenceBinding,
    private val listener: AccountAdapter.Listener
) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(account: AccountDto) {
        binding.tvItemAccountName.text = account.nickname
        binding.tvItemAccountJid.text = account.jid
        binding.shapeView.setDrawable(MaskManager.mask)
        if (!account.hasAvatar) loadAvatarWithInitials(
            account.nickname,
            account.colorKey
        ) else loadAvatar(account.jid)
        binding.root.setOnClickListener { listener.onClick(account.id) }
        binding.switchAccountEnable.isChecked = account.enabled
        binding.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
            listener.setEnabled(account.id, isChecked)
        }
    }

    private fun loadAvatarWithInitials(name: String, colorKey: String) {
        val color = ColorManager.convertColorLightNameToId(colorKey)
        binding.imAvatarItemAccount.setImageResource(color)
        var initials =
            name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.reduce { acc, s -> acc + s }
        if (initials.length > 2) initials = initials.substring(0, 2)
        binding.tvInitials.isVisible = true
        binding.tvInitials.text = initials
    }

    private fun loadAvatar(id: String) {
        binding.tvInitials.isVisible = false
        val realm = Realm.open(defaultRealmConfig())
        var uri: String? = null
        realm.writeBlocking {
            val avatar = this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
            if (avatar != null) uri = avatar.fileUri
        }
        Glide.with(binding.root.context).load(uri).into(binding.imAvatarItemAccount)
    }


    fun bind(
        account: AccountDto,
        payloads: List<Any>
    ) {
        val bundle = payloads.last() as Bundle
        for (key in bundle.keySet()) {
            when (key) {
                "enabled" -> {
                    Log.d("iii", "en")
                    val enable = bundle.getBoolean("enabled")
                    binding.switchAccountEnable.setOnCheckedChangeListener(null)
                    binding.switchAccountEnable.isChecked = enable
                    binding.switchAccountEnable.setOnCheckedChangeListener { buttonView, isChecked ->
                        listener.setEnabled(account.id, isChecked)
                    }

                }
                "avatar" -> {

                }
            }
        }
    }


}
