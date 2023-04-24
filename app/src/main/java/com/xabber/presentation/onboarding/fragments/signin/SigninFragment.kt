package com.xabber.presentation.onboarding.fragments.signin

import android.content.Context
import android.os.Bundle
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSigninBinding
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.onboarding.util.PasswordStorageHelper
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import com.xabber.presentation.onboarding.fragments.signin.feature.FeatureAdapter
import com.xabber.presentation.onboarding.fragments.signin.feature.State
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** This fragment is intended for user authorization. If the authorization is successful
 * and all the features are successful, too, the user gets into the application
 */

class SigninFragment : Fragment(R.layout.fragment_signin) {
    private val binding by viewBinding(FragmentSigninBinding::bind)
    private val featureAdapter = FeatureAdapter()
    private val viewModel = SigninViewModel()
    private var host: String = "xabber.com"
    private var compositeDisposable: CompositeDisposable? = CompositeDisposable()

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.btnRock.isVisible && binding.btnRock.isEnabled) {
                navigator().finishActivity()
            } else navigator().goBack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbarChanger().showArrowBack(true)
        toolbarChanger().setTitle(R.string.signin_toolbar_title_1)
        initEditText()
        initButton()
        initRecyclerView()
        binding.signinSubtitle1.text = getSubtitleClickableSpan(true)
        binding.signinSubtitle1.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    private fun initEditText() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                var jidText = binding.editTextLogin.text.toString()
                if (!jidText.contains('@'))
                    jidText += "@$host"
                binding.btnConnect.isEnabled =
                    p0.toString()
                        .isNotEmpty() && viewModel.isJidValid(jidText) && binding.editTextPassword.text.toString()
                        .isNotEmpty()
                binding.errorSubtitle.isVisible = false
                binding.signinSubtitle1.isInvisible = false
                binding.signinSubtitle1.setText(
                    getSubtitleClickableSpan(true),
                    TextView.BufferType.SPANNABLE
                )
                binding.signinSubtitle1.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        binding.editTextLogin.addTextChangedListener(textWatcher)
        binding.editTextPassword.addTextChangedListener(textWatcher)

        binding.editTextPassword.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnConnect.isEnabled)
                    binding.btnConnect.performClick()
                closeKeyboard()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    private fun initButton() {
        with(binding) {
            btnConnect.setOnClickListener {
                val jid = editTextLogin.text?.trim().toString()
                val password = binding.editTextPassword.text?.trim().toString()
                if (password != "12345" || !viewModel.isJidValid(jid)  // viewModel.verifyPassword(password, jid)
                ) {
                    binding.signinSubtitle1.isInvisible = true
                    binding.errorSubtitle.isVisible = true
                } else {
                    prepareUi()
                    compositeDisposable?.add(viewModel.features
                        .doOnNext { list ->
                            if (list.count { it.nameResId == R.string.feature_name_4 } == 1) {
                                toolbarChanger().setTitle(R.string.signin_toolbar_title_2)
                                toolbarChanger().showArrowBack(false)
                                signinTitle.text = String.format(
                                    resources.getString(R.string.signin_title_label_template_2),
                                    host
                                )
                                editTextLogin.isVisible = false
                                editTextPassword.isVisible = false
                                signinSubtitle1.isVisible = false
                                btnConnect.isVisible = false
                            }
                            if (list.all { it.state != State.Error } || viewModel.isServerFeatures) {
                                featureAdapter.submitList(list)
                                featureAdapter.notifyItemChanged(list.lastIndex)
                            }
                            lifecycleScope.launch {
                                delay(150)
                                list[list.lastIndex].state =
                                    State.Success   //    Здесь пока заглушка
                                if (viewModel.isServerFeatures) {
                                    featureAdapter.submitList(list)
                                    featureAdapter.notifyItemChanged(list.lastIndex)
                                }
                                if (list.count { it.nameResId == R.string.feature_name_10 } == 1 && list.all { it.state != State.Error }) {
                                    signinSubtitle2.isVisible = true
                                    btnRock.isVisible = true
                                    btnRock.setOnClickListener {
                                        navigator().goToApplicationActivity()
                                    }
                                }
                                if (viewModel._features.count { it.state == State.Error } <= 1 &&
                                    viewModel._features[list.lastIndex].state == State.Error
                                ) {
                                    featureAdapter.submitList(list)
                                    featureAdapter.notifyItemChanged(list.lastIndex)
                                }
                                if (viewModel._features[list.lastIndex].state == State.Success &&
                                    viewModel._features.count { it.state == State.Error } == 0
                                ) {
                                    featureAdapter.submitList(list)
                                    featureAdapter.notifyItemChanged(list.lastIndex)
                                }
                            }
                        }
                        .subscribe({}, {})
                    )
                }
            }
        }
    }

    private fun prepareUi() {
        binding.btnConnect.isEnabled = false
        binding.btnConnect.text =
            resources.getString(R.string.signin_connect_button_label_2)
        binding.signinSubtitle1.text =
            getSubtitleClickableSpan(false)
        binding.signinSubtitle1.movementMethod = null
        binding.rvFeature.isVisible = true
        closeKeyboard()
    }

    private fun initRecyclerView() {
        binding.rvFeature.adapter = featureAdapter
    }

    private fun closeKeyboard() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            binding.editTextPassword.windowToken,
            0
        )
    }

    fun getSubtitleClickableSpan(clickable: Boolean): SpannableString {
        val spannable =
            SpannableString(
                "${resources.getString(R.string.signin_subtitle_label_1_start)} ${
                    resources.getString(
                        R.string.press_here
                    )
                } ${resources.getString(R.string.signin_subtitle_label_1_end)}"
            )

        if (clickable)
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(p0: View) {
                        navigator().openSignupNicknameFragment()
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false
                    }
                },
                resources.getString(R.string.signin_subtitle_label_1_start).length,
                resources.getString(R.string.signin_subtitle_label_1_start).length + 1 + resources.getString(
                    R.string.press_here
                ).length + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        return spannable
    }

    override fun onPause() {
        super.onPause()
        onBackPressedCallback.remove()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.signinSubtitle1.movementMethod = null
        compositeDisposable?.clear()
        compositeDisposable = null
    }

}
