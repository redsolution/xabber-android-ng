package com.xabber.presentation.application.fragments.account.reorder

import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.ItemAccountForReorderBinding
import com.xabber.models.dto.AccountDto
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import com.xabber.presentation.application.activity.ColorManager
import io.realm.kotlin.Realm

class ReorderAccountViewHolder(private val binding: ItemAccountForReorderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(account: AccountDto) {
        binding.imAccountAnchor.isVisible = true
        binding.tvItemAccountName.text = account.nickname
        binding.tvItemAccountJid.text = account.jid
        if (!account.hasAvatar) loadAvatarWithInitials(
            account.nickname,
            account.colorKey
        ) else loadAvatar(account.jid)
    }

    fun getImAnchor(): ImageView = binding.imAccountAnchor

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

}
