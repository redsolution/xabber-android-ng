package com.xabber.application.fragments.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.databinding.FragmentChatBinding

class ChatFragment() : Fragment() {
    private var binding : FragmentChatBinding? = null
    lateinit var userName : String

    companion object {
        fun newInstance(_userName: String) = ChatFragment().apply {
            userName = _userName
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // binding?.tvUserName?.text = userName



    }
}