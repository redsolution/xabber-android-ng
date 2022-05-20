package com.xabber.presentation.onboarding.fragments.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.xabber.R
import com.xabber.databinding.FragmentSignupPasswordBinding
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import kotlin.properties.Delegates


class SignupPasswordFragment : Fragment() {
    private var _binding: FragmentSignupPasswordBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()
    private var password by Delegates.notNull<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupPasswordBinding.inflate(inflater)
        return binding.root
        Log.d("password", "PasswordFragment")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_password_toolbar_title)
        toolbarChanger().setShowBack(true)
        initEditText()
        initButton()
    }

    private fun initEditText() {
        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                p0: CharSequence?,
                p1: Int,
                p2: Int,
                p3: Int
            ) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                password = p0.toString()
                binding.passwordBtnNext.isEnabled = password.length > 3
            }
        })

    }

    private fun initButton() {
        binding.passwordBtnNext.setOnClickListener {
            Log.d("ggg", "hhhh")
            viewModel.setPassword(binding.passwordEditText.text.toString())
            navigator().registerAccount()
            navigator().startSignupAvatarFragment()
        }
    }

}