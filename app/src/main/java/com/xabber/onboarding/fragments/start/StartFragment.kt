package com.xabber.onboarding.fragments.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.xabber.databinding.FragmentStartBinding
import com.xabber.onboarding.contract.navigator
import com.xabber.onboarding.contract.toolbarChanger
import com.xabber.onboarding.fragments.BaseFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

class StartFragment : BaseFragment() {
    private var binding: FragmentStartBinding? = null
    private val viewModel = StartViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentStartBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setShowBack(false)
        toolbarChanger().clearTitle()
        initButton()
    }

    private fun initButton() {
        with(binding!!) {
            btnSkip.setOnClickListener {
                navigator().goToApplicationActivity()
            }

            btnLogin.setOnClickListener {
                navigator().startSigninFragment()
            }

            btnSignup.setOnClickListener {
                progressBar.isVisible = true
                btnLogin.visibility = View.GONE
                btnSignup.isVisible = false
                compositeDisposable.add(
                    viewModel.getHost()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ host ->
                            progressBar.isVisible = false
                            navigator().startSignupNicknameFragment()
                        }, this@StartFragment::logError)
                )
            }


        }

    }
}