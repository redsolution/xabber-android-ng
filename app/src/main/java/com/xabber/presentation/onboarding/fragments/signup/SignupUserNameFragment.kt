package com.xabber.presentation.onboarding.fragments.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSignupUsernameBinding
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger

class SignupUserNameFragment : Fragment(R.layout.fragment_signup_username) {
    private val binding by viewBinding(FragmentSignupUsernameBinding::bind)
    private val viewModel: OnboardingViewModel by activityViewModels()
    private var host = "@xabber.com"
    private val minUserNameLength = 3

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_username_toolbar_title)
        toolbarChanger().showArrowBack(true)
        initEditText()
        initButton()
    }

    private fun initEditText() {
        binding.usernameEditText.clearFocus()
        binding.usernameEditText.requestFocus()

        with(binding) {
            usernameEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    p0: CharSequence?,
                    p1: Int,
                    p2: Int,
                    p3: Int
                ) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                override fun afterTextChanged(p0: Editable?) {
                    val username = p0.toString().trim()
                    val isValidLength = username.length >= minUserNameLength
                    usernameBtnNext.isEnabled = isValidLength && viewModel.checkIsNameAvailable(username, host)

                    if (!isValidLength) {
                        binding.resultSubtitle.isVisible = false
                        binding.usernameSubtitle.isInvisible = false
                    } else {
                        binding.usernameSubtitle.isInvisible = true
                        binding.resultSubtitle.isVisible = true
                        if (viewModel.checkIsNameAvailable(username, host)) {
                            resultSubtitle.text = resources.getString(R.string.signup_username_success_subtitle)
                            changeSubtitleColor(R.color.blue_600)
                        } else {
                            resultSubtitle.text = resources.getString(R.string.signup_username_error_subtitle)
                            changeSubtitleColor(R.color.red_600)
                            Toast.makeText(
                                requireContext(),
                                "Username already exists, please choose another",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            })
        }
    }

    private fun changeSubtitleColor(@ColorRes colorId: Int) {
        binding.resultSubtitle.setTextColor(
            ResourcesCompat.getColor(
                resources,
                colorId,
                requireContext().theme
            )
        )
    }

    private fun initButton() {
        binding.usernameBtnNext.setOnClickListener {
            val userName = binding.usernameEditText.text?.trimEnd().toString() + host
            viewModel.setJid(userName)
            navigator().openSignupPasswordFragment()
        }
    }
}