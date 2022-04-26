package com.xabber.presentation.application.fragments.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentEditContactBinding
import com.xabber.presentation.application.contract.navigator

class EditContactFragment : Fragment() {
    private var binding : FragmentEditContactBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEditContactBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
          binding?.editContactToolbar?.setNavigationIcon(R.drawable.ic_material_close_24)
        binding?.editContactToolbar?.setNavigationOnClickListener { navigator().goBack() }

    }
}