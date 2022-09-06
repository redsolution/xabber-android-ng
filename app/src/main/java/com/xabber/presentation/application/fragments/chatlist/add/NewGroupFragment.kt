package com.xabber.presentation.application.fragments.chatlist.add

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentNewGroupBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import kotlin.properties.Delegates

class NewGroupFragment : DetailBaseFragment(R.layout.fragment_new_group) {
     private val binding by viewBinding(FragmentNewGroupBinding::bind)
    var incognito by Delegates.notNull<Boolean>()

    companion object {
        fun newInstance(_incognito: Boolean) = NewGroupFragment().apply {
            incognito = _incognito
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (incognito) initIncognitoUi()
        binding.newGroupToolbar.setNavigationIcon(R.drawable.ic_close)
        binding.newGroupToolbar.setNavigationOnClickListener { navigator().closeDetail() }
    }

    private fun initIncognitoUi() {
        binding.tvTitle.text = "Create incognito group"
        binding.etGroupName.hint = "Incognito group"
    }


}