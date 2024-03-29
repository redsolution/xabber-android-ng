package com.xabber.presentation.onboarding.fragments.start

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentStartBinding
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import retrofit2.HttpException
import java.net.ConnectException
import java.net.UnknownHostException

class StartFragment : Fragment(R.layout.fragment_start) {
    private val binding by viewBinding(FragmentStartBinding::bind)
    private val viewModel: OnboardingViewModel by activityViewModels()
    private var compositeDisposable: CompositeDisposable? = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().clearTitle()
        toolbarChanger().showArrowBack(false)
        initButton()
    }

    private fun initButton() {
        with(binding) {
            btnSignin.setOnClickListener {
                navigator().openSigninFragment()
            }
            btnSignup.setOnClickListener {
                progressBar.isVisible = true
                btnSignin.isVisible = false
                btnSignup.isVisible = false
                compositeDisposable?.add(
                    viewModel.getHost()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            progressBar.isVisible = false
                            navigator().openSignupNicknameFragment()
                        }, { showError(it) })
                )
            }
        }
    }

    private fun showError(e: Throwable) {
        val errorMessage = when (e) {
            is ConnectException -> "Нет подключения к интернету"
            is UnknownHostException -> "Неправильный адрес сервера"
            is HttpException -> "Ошибка на сервере: ${e.code()}"
            else -> "Неизвестная ошибка: ${e.message}"
        }
      //  showToast(errorMessage)
        navigator().openSignupNicknameFragment() // пока что все равно переходим к регистрации, т.к. работа с сервером не реализована
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable?.clear()
        compositeDisposable = null
    }

}
