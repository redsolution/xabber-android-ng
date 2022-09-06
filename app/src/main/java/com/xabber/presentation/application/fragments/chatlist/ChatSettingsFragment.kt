package com.xabber.presentation.application.fragments.chatlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentChatSettingsBinding
import com.xabber.presentation.application.contract.navigator

class ChatSettingsFragment : Fragment() {
    private var _binding : FragmentChatSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
       _binding = FragmentChatSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chatSettingsToolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        binding.chatSettingsToolbar.setNavigationOnClickListener { navigator().goBack() }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
