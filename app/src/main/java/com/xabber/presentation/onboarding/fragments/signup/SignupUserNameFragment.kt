package com.xabber.presentation.onboarding.fragments.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.xabber.R
import com.xabber.databinding.FragmentSignupUsernameBinding
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import kotlin.properties.Delegates

class SignupUserNameFragment : Fragment() {
    private var _binding: FragmentSignupUsernameBinding? = null
    private val binding get() = _binding!!
    private val viewModel : OnboardingViewModel by activityViewModels()
    private var host by Delegates.notNull<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupUsernameBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_username_toolbar_title)
        toolbarChanger().setShowBack(true)
        initEditText()
        initButton()
        host = ""
    }

    private fun initEditText() {
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
                    usernameBtnNext.isEnabled = p0.toString().length > 3
                    if (p0.toString().isEmpty()) {
                        usernameSubtitle.text =
                            resources.getString(R.string.signup_username_subtitle)
                        changeSubtitleColor(R.color.grey_text_3)
                    }
                    if (usernameSubtitle.text == resources.getString(R.string.signup_username_error_subtitle)) {
                        usernameSubtitle.text =
                            resources.getString(R.string.signup_username_subtitle)
                        changeSubtitleColor(R.color.grey_text_3)
                    }

                    if (p0.toString() == "маша") {
                        usernameSubtitle.text =
                            resources.getString(R.string.signup_username_error_subtitle)
                        changeSubtitleColor(R.color.red_600)
                    } else if (p0.toString().length > 3) {
                        usernameSubtitle.text =
                            resources.getString(R.string.signup_username_success_subtitle)
                        changeSubtitleColor(R.color.blue_600)
                    }
                }
            })

        }
    }


    private fun changeSubtitleColor(@ColorRes colorId: Int) {
        binding.usernameSubtitle.setTextColor(
            ResourcesCompat.getColor(
                resources,
                colorId,
                requireContext().theme
            )
        )
    }

    private fun initButton() {
        binding.usernameBtnNext.setOnClickListener {
            viewModel.setUserName(binding.usernameEditText.text.toString())
            navigator().startSignupPasswordFragment()
        }
    }


}