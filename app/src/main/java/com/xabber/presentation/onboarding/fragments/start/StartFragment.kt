package com.xabber.presentation.onboarding.fragments.start

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.xabber.databinding.FragmentStartBinding
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers

class StartFragment : Fragment() {
    private var _binding: FragmentStartBinding? = null
    private val binding get() = _binding!!
    private val viewModel = StartViewModel()
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStartBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().clearTitle()
        toolbarChanger().setShowBack(false)
        initButton()
    }


    private fun initButton() {
        with(binding) {
            btnSkip.setOnClickListener {
                navigator().goToApplicationActivity(true)
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
                        }, @StartFragment ::logError)
                )
            }


        }

    }

    private fun logError(e: Throwable) {
        Log.e("ERROR", e.stackTraceToString())
    }

    override fun onDestroy() {
        _binding = null
        compositeDisposable.clear()
        super.onDestroy()
    }
}