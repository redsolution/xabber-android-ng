package com.xabber.presentation.onboarding.fragments.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSignupPasswordBinding
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger


class SignupPasswordFragment : Fragment(R.layout.fragment_signup_password) {
    private val binding by viewBinding(FragmentSignupPasswordBinding::bind)
    private val viewModel: OnboardingViewModel by activityViewModels()
    private val minPasswordLength = 4

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_password_toolbar_title)
        toolbarChanger().showArrowBack(true)
        initEditText()
        initButton()
    }

    private fun initEditText() {
        binding.passwordEditText.clearFocus()
        binding.passwordEditText.requestFocus()
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
                binding.passwordBtnNext.isEnabled = p0.toString().length >= minPasswordLength
            }
        })

    }

    private fun initButton() {
        binding.passwordBtnNext.setOnClickListener {
            val password = binding.passwordEditText.text.toString()
            viewModel.setPassword(password)
            viewModel.registerAccount()
            navigator().openSignupAvatarFragment()
        }
    }

}
