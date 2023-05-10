package com.xabber.presentation.application.fragments.account.reorder

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.ItemAccountForReorderBinding
import com.xabber.dto.AccountDto
import com.xabber.presentation.application.manage.ColorManager
import io.realm.kotlin.Realm
import java.util.*


class ReorderAccountAdapter(
    val accounts: List<AccountDto>,
    private val onStartDragListener: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ReorderAccountAdapter.ReorderAccountViewHolder>(), ItemTouchHelperAdapter {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReorderAccountViewHolder =
        ReorderAccountViewHolder(
            ItemAccountForReorderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ReorderAccountViewHolder, position: Int) {
        holder.bind(accounts[position])
    }

    override fun getItemCount(): Int = accounts.size

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(accounts, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(accounts, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }


    inner class ReorderAccountViewHolder(private val binding: ItemAccountForReorderBinding) : RecyclerView.ViewHolder(binding.root) {
            @SuppressLint("ClickableViewAccessibility")
            fun bind(account: AccountDto) {
                binding.imAccountAnchor.isVisible = true
                binding.tvItemAccountName.text = account.nickname
                binding.tvItemAccountJid.text = account.jid
                binding.imAccountAnchor.setOnTouchListener { _, event ->
                    if (event.action ==
                        MotionEvent.ACTION_DOWN
                    ) {
                        onStartDragListener(this)
                    }
                    false
                }
                if (!account.hasAvatar) loadAvatarWithInitials(
                    account.nickname,
                    account.colorKey
                ) else loadAvatar(account.jid)
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
                    val avatar = this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
                    if (avatar != null) uri = avatar.fileUri
                }
                Glide.with(binding.root.context).load(uri).into(binding.imAvatarItemAccount)
            }
        }

    }
