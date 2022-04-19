package com.xabber.presentation.application.fragments.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.databinding.FragmentNewMessageBinding
import com.xabber.presentation.application.contract.applicationToolbarChanger
import com.xabber.presentation.application.contract.navigator

class NewMessageFragment : Fragment() {
    private var binding: FragmentNewMessageBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentNewMessageBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applicationToolbarChanger().showNavigationView(false)
        initToolbarAction()
    }

    private fun initToolbarAction() {
        binding?.imBack?.setOnClickListener {
            navigator().goBack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        applicationToolbarChanger().showNavigationView(true)
    }
}