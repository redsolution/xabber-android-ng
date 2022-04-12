package com.xabber.onboarding.fragments.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.databinding.FragmentSignupPasswordBinding
import com.xabber.domain.entity.AccountJid
import com.xabber.domain.entity.Domainpart
import com.xabber.domain.entity.JidCreate.domainBareFrom
import com.xabber.domain.entity.Localpart
import com.xabber.domain.entity.Resourcepart
import com.xabber.onboarding.contract.navigator
import com.xabber.onboarding.contract.toolbarChanger
import com.xabber.onboarding.fragments.BaseFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
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
        toolbarChanger().setTitle(R.string.signup_toolbar_title_3)
        toolbarChanger().setShowBack(true)
        initEditText()
        initButton()
    }

    private fun initEditText() {


    }

    private fun initButton() {
        with(binding!!) {
            passwordBtnNext.setOnClickListener {
                progressBar.isVisible = true
                passwordBtnNext.isEnabled = false
                passwordEditText.isEnabled = false
                passwordBtnNext.text = ""
                compositeDisposable.clear()
                compositeDisposable.add(
                    viewModel.registerAccount(username, host, password)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doAfterSuccess {
                            passwordEditText.isEnabled = true
                            accountJid = AccountJid.from(
                                Localpart.from(it.username),
                                domainBareFrom(
                                    Domainpart.from(it.domain)
                                ),
                                Resourcepart.EMPTY
                            )

                        }
                        .doOnDispose {
                            navigator().startSignupAvatarFragment()
                        }
                        .subscribe({}, {
                            logError(it)
                        })
                )
            }
        }
    }
}