package com.xabber.presentation.onboarding.fragments.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSignupNicknameBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger


class SignupNicknameFragment : BaseFragment(R.layout.fragment_signup_nickname) {
    private val binding by viewBinding(FragmentSignupNicknameBinding::bind)
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_nickname_toolbar_title)
        toolbarChanger().showArrowBack(true)
        initEditText()
        initButton()
    }

    private fun initEditText() {

        val textChangeListener = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                binding.nicknameBtnNext.isEnabled = p0.toString().length > 2
            }

        }
        binding.nicknameEditText.addTextChangedListener(textChangeListener)
    }

    private fun initButton() {
        binding.nicknameBtnNext.setOnClickListener {
            viewModel.setNickName(binding.nicknameEditText.text.toString())
            navigator().openSignupUserNameFragment()
        }
    }

}




