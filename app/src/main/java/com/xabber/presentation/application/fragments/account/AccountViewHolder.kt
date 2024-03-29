package com.xabber.presentation.application.fragments.account

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.ItemAccountForPreferenceBinding
import com.xabber.dto.AccountDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.MaskManager
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
        setEnable(account.id, account.enabled)
    }

    private fun setEnable(accountId: String, enable: Boolean) {
        binding.switchAccountEnable.setOnCheckedChangeListener(null)
        binding.switchAccountEnable.isChecked = enable
        binding.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
            listener.setEnabled(accountId, isChecked)
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
            val avatar = this.query(
                com.xabber.data_base.models.avatar.AvatarStorageItem::class,
                "primary = '$id'"
            ).first().find()
            if (avatar != null) uri = avatar.fileUri
        }
        Glide.with(binding.root.context).load(uri).into(binding.imAvatarItemAccount)
        realm.close()
    }


    fun bind(
        account: AccountDto,
        payloads: List<Any>
    ) {
        val bundle = payloads.last() as Bundle
        for (key in bundle.keySet()) {
            when (key) {
                AppConstants.PAYLOAD_ACCOUNT_ENABLED -> {
                    val enable = bundle.getBoolean(AppConstants.PAYLOAD_ACCOUNT_ENABLED)
                    setEnable(account.id, enable)
                }
                AppConstants.PAYLOAD_ACCOUNT_COLOR -> {
                    if (!account.hasAvatar) {
                        val colorKey = bundle.getString(AppConstants.PAYLOAD_ACCOUNT_COLOR)
                        if (colorKey != null) loadAvatarWithInitials(account.nickname, colorKey)
                    }
                }
                AppConstants.PAYLOAD_ACCOUNT_HAS_AVATAR -> {
                    val hasAvatar = bundle.getBoolean(AppConstants.PAYLOAD_ACCOUNT_HAS_AVATAR)
                    if (!hasAvatar) loadAvatarWithInitials(account.nickname, account.colorKey)
                    else loadAvatar(account.id)
                }
            }
        }
    }

}
