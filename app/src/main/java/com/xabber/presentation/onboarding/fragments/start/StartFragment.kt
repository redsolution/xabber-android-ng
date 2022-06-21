package com.xabber.presentation.onboarding.fragments.start

import android.graphics.*
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentStartBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.delay

class StartFragment : BaseFragment(R.layout.fragment_start) {
    private val binding by viewBinding(FragmentStartBinding::bind)
    private val viewModel: OnboardingViewModel by activityViewModels()
    private val compositeDisposable = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().clearTitle()
        toolbarChanger().showArrowBack(false)
        initButton()
    }


    private fun initButton() {
        with(binding) {
            btnSkip.setOnClickListener {
                navigator().goToApplicationActivity(true)
            }

            btnLogin.setOnClickListener {
                navigator().openSigninFragment()
            }

            btnSignup.setOnClickListener {
                progressBar.isVisible = true
                btnLogin.isVisible = false
                btnSignup.isVisible = false
                compositeDisposable.add(
                    viewModel.getHost()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            progressBar.isVisible = false
                            navigator().openSignupNicknameFragment()
                        }, @StartFragment ::showError)
                )
            }


        }

    }

    private fun showError(e: Throwable) {
            val snack = Snackbar.make(
                   binding.root,
                    "There is no internet connection",
                    Snackbar.LENGTH_SHORT
                )
            snack.setTextColor(Color.YELLOW)
            snack.show()
//        with(binding) {
//            progressBar.isVisible = false
//            btnLogin.isVisible = true
//            btnSignup.isVisible = true
//        }
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }
}