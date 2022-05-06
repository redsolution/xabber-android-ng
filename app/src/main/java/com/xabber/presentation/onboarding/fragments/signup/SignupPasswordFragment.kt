package com.xabber.presentation.onboarding.fragments.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.xabber.R
import com.xabber.databinding.FragmentSignupPasswordBinding
import com.xabber.domain.entity.AccountJid
import com.xabber.presentation.onboarding.contract.toolbarChanger
import com.xabber.presentation.onboarding.fragments.BaseFragment
import kotlin.properties.Delegates

class SignupPasswordFragment : BaseFragment() {

    private var binding: FragmentSignupPasswordBinding? = null
    private val viewModel = SignupViewModel()
    private var username by Delegates.notNull<String>()
    private var host by Delegates.notNull<String>()
    private var password by Delegates.notNull<String>()
    var accountJid: AccountJid? = null


    companion object {
        const val PARAMS_USER = "user_params"

        fun newInstance(params: UserParams) = SignupPasswordFragment().apply {
            arguments = Bundle().apply {
                putParcelable(PARAMS_USER, params)
            }
            username = params.username
            host = params.host
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignupPasswordBinding.inflate(inflater)
        return binding?.root


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_password_toolbar_title)
        toolbarChanger().setShowBack(true)
        initEditText()
        initButton()
    }

    private fun initEditText() {
        binding?.passwordEditText?.addTextChangedListener(object : TextWatcher {
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
                binding?.passwordBtnNext?.isEnabled = password.length > 3
            }
        })

    }

    private fun initButton() {
        with(binding!!) {
            passwordBtnNext.setOnClickListener {
                //  progressBar.isVisible = true
                //   passwordBtnNext.isEnabled = false
                //   passwordEditText.isEnabled = false
                //   passwordBtnNext.text = ""
                //  compositeDisposable.clear()
                //  compositeDisposable.add(
                //     viewModel.registerAccount(username, host, password)
                //       .subscribeOn(Schedulers.io())
                //       .observeOn(AndroidSchedulers.mainThread())
                //      .doAfterSuccess {
                //         passwordEditText.isEnabled = true
                //         accountJid = AccountJid.from(
                //           Localpart.from(it.username),
                //          domainBareFrom(
                //              Domainpart.from(it.domain)
                //         ),
                //          Resourcepart.EMPTY
                //     )

                //   }
                //   .doOnDispose {
                //  navigator().startSignupAvatarFragment()
                findNavController().navigate(R.id.action_signupPasswordFragment_to_signupAvatarFragment)
                //    }
                //     .subscribe({}, {
                //         logError(it)
                //     })
                //  )
            }
        }
    }
}