package com.xabber.presentation.onboarding.fragments.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import kotlin.properties.Delegates

/** Here the user selects a jid name
 *
 */

class SignupUserNameFragment : Fragment(R.layout.fragment_signup_username) {
    private val binding by viewBinding(FragmentSignupUsernameBinding::bind)
    private val viewModel: OnboardingViewModel by activityViewModels()
    private var host by Delegates.notNull<String>()
    private val minUserNameLength = 3

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_username_toolbar_title)
        toolbarChanger().showArrowBack(true)
        initEditText()
        initButton()
        host = "@xabber.com"
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
                    usernameBtnNext.isEnabled = p0.toString().length >= minUserNameLength
                    if (p0.toString().length < 3) {
                        binding.resultSubtitle.isVisible = false
                        binding.usernameSubtitle.isInvisible = false
                    } else {
                        binding.usernameSubtitle.isInvisible = true
                        binding.resultSubtitle.isVisible = true
                        if (viewModel.checkIsNameAvailable(p0.toString(), host)) {
                            resultSubtitle.text =
                                resources.getString(R.string.signup_username_success_subtitle)
                            changeSubtitleColor(R.color.blue_600)
                        } else {
                            resultSubtitle.text =
                                resources.getString(R.string.signup_username_error_subtitle)
                            changeSubtitleColor(R.color.red_600)
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
            val userName = binding.usernameEditText.text.toString() + host
            viewModel.setJid(userName)
            navigator().openSignupPasswordFragment()
        }
    }

}
