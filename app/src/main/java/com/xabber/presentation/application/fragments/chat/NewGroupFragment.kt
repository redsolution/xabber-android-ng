package com.xabber.presentation.application.fragments.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentNewGroupBinding
import com.xabber.presentation.application.contract.navigator
import kotlin.properties.Delegates

class NewGroupFragment : Fragment() {
    private var binding : FragmentNewGroupBinding? = null
   var incognito by Delegates.notNull<Boolean>()

    companion object {
        fun newInstance(_incognito: Boolean) = NewGroupFragment().apply {
            incognito = _incognito
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
binding = FragmentNewGroupBinding.inflate(inflater, container, false)
    return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (incognito) initIncognitoUi()
         binding?.newGroupToolbar?.setNavigationIcon(R.drawable.ic_material_close_24)
        binding?.newGroupToolbar?.setNavigationOnClickListener { navigator().goBack() }
    }

    private fun initIncognitoUi() {
        binding?.tvTitle?.text = "Create incognito group"
        binding?.etGroupName?.hint = "Incognito group"
    }
}