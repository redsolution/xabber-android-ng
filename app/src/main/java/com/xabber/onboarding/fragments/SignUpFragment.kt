package com.xabber.onboarding.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.ChoiceTitleToolbar
import com.xabber.databinding.FragmentSignupBinding

class SignUpFragment : Fragment(), ChoiceTitleToolbar {
private var binding : FragmentSignupBinding? = null

    companion object {
       fun newInstance() = SignUpFragment()
   }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignupBinding.inflate(inflater)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun getTitle(): String = "Sign Up"
}