package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentAlertsBinding
import com.xabber.presentation.application.contract.navigator

class AlertsFragment : Fragment() {
    private var binding: FragmentAlertsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.alertsToolbar?.setNavigationIcon(R.drawable.ic_arrow_left)
        binding?.alertsToolbar?.setNavigationOnClickListener {
            navigator().goBack()
        }
    }
}