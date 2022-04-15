package com.xabber.application.fragments.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.application.contract.applicationToolbarChanger
import com.xabber.databinding.FragmentDiscoverBinding

class DiscoverFragment : Fragment() {
    private var binding: FragmentDiscoverBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applicationToolbarChanger().setTitle(R.string.bottom_nav_discover_label)

    }
}