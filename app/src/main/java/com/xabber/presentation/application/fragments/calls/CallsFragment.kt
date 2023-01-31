package com.xabber.presentation.application.fragments.calls

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import io.realm.kotlin.Realm

class CallsFragment : BaseFragment(R.layout.fragment_calls) {
    private val binding by viewBinding(FragmentCallsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAvatarWithMask()
        activeLinks()
        binding.imAvatar.setOnClickListener {
            var jid = ""
            val realm = Realm.open(defaultRealmConfig())
            realm.writeBlocking {
                jid = realm.query(AccountStorageItem::class).first().find()!!.jid
            }
            navigator().showAccount(jid)
        }
    }

    private fun loadAvatarWithMask() {
        val avatar = UiChanger.getAvatar()
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(requireContext()).load(avatar).error(R.drawable.ic_avatar_placeholder)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatar)
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
