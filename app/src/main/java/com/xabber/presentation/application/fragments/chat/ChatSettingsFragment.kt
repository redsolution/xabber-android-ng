package com.xabber.presentation.application.fragments.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentChatSettingsBinding
import com.xabber.presentation.application.contract.navigator

class ChatSettingsFragment : Fragment() {
    private var binding : FragmentChatSettingsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
       binding = FragmentChatSettingsBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.chatSettingsToolbar?.setNavigationIcon(R.drawable.ic_arrow_left)
        binding?.chatSettingsToolbar?.setNavigationOnClickListener { navigator().goBack() }
    }
}