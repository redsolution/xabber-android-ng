package com.xabber.presentation.onboarding.fragments.signin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.databinding.FragmentSigninBinding
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import io.realm.kotlin.Realm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SigninFragment : Fragment(R.layout.fragment_signin) {
    private val binding by viewBinding(FragmentSigninBinding::bind)
    private val viewModel = SigninViewModel()
    private var host: String = "xabber.com"
    private val realm = Realm.open(defaultRealmConfig())
    private var accountCheckJob: Job? = null
    private var fakeProgressJob: Job? = null
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().goBack()
        }
    }
    private var animationJob: Job? = null
    private var loginJob: Job? = null


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize AccountManager with application context
        AccountManager.initialize(requireContext())
        toolbarChanger().showArrowBack(true)
        toolbarChanger().setTitle(R.string.signin_toolbar_title_1)
        initEditText()
        initButton()
        binding.signinSubtitle1.text = getSubtitleClickableSpan(true)
        binding.signinSubtitle1.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    private fun initEditText() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                var jidText = binding.editTextLogin.text.toString()
                if (!jidText.contains('@')) {
                    jidText += "@$host"
                }
                binding.btnConnect.isEnabled =
                    p0.toString().isNotEmpty() &&
                            viewModel.isJidValid(jidText) &&
                            binding.editTextPassword.text.toString().isNotEmpty()
                binding.errorSubtitle.isVisible = false
                binding.signinSubtitle1.isInvisible = false
                binding.signinSubtitle1.setText(
                    getSubtitleClickableSpan(true),
                    TextView.BufferType.SPANNABLE
                )
                binding.signinSubtitle1.movementMethod = LinkMovementMethod.getInstance()

                // Debounced account existence check
                accountCheckJob?.cancel()
                accountCheckJob = lifecycleScope.launch {
                    delay(500) // Wait 500ms after typing stops
                    if (viewModel.isJidValid(jidText)) {
                        val exists = realm.query(AccountStorageItem::class, "jid = $0", jidText).count().find() > 0
                        binding.errorSubtitle.isVisible = exists
                        binding.errorSubtitle.text = if (exists) "Account already exists" else ""
                        Log.d("SigninFragment", "Account check for jid $jidText: exists = $exists")
                    }
                }
            }
        }

        binding.editTextLogin.addTextChangedListener(textWatcher)
        binding.editTextPassword.addTextChangedListener(textWatcher)

        binding.editTextPassword.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnConnect.isEnabled) {
                    binding.btnConnect.performClick()
                }
                closeKeyboard()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun initButton() {
        with(binding) {
            btnConnect.setOnClickListener {
                btnConnect.isEnabled = false

                val jid = editTextLogin.text?.trim().toString().let {
                    if (!it.contains('@')) "$it@$host" else it
                }
                val username = jid.split("@")[0]
                val password = editTextPassword.text?.trim().toString()

                if (!viewModel.isJidValid(jid)) {
                    showError("Invalid JID format")
                    btnConnect.isEnabled = true
                    return@setOnClickListener
                }

                // Hide the button and show progress container
                btnConnect.visibility = View.GONE
                progressStepsContainer.visibility = View.VISIBLE

                // Start the step animation
                lifecycleScope.launch {
                    // Step 1: Communicating with server
                    fadeIn(step1Container)
                    delay(1000)
                    fadeIn(step1Check)
                    delay(200)

                    // Step 2: Checking credentials
                    fadeIn(step2Container)
                    delay(1000)
                    fadeIn(step2Check)
                    delay(200)

                    // Step 3: Requesting server capabilities
                    fadeIn(step3Container)
                    delay(1000)
                    fadeIn(step3Check)

                    // Short pause then navigate
                    delay(500)
                    navigator().openConnectionProgressFragment(jid, username, password)
                }            }
        }
    }
    private fun showError(message: String) {
        binding.errorSubtitle.text = message
        binding.errorSubtitle.isVisible = true
        binding.signinSubtitle1.isInvisible = true
        binding.btnConnect.isEnabled = true
    }


    private fun closeKeyboard() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.editTextPassword.windowToken, 0)
    }

    fun getSubtitleClickableSpan(clickable: Boolean): SpannableString {
        val spannable = SpannableString(
            "${resources.getString(R.string.signin_subtitle_label_1_start)} " +
                    "${resources.getString(R.string.press_here)} " +
                    "${resources.getString(R.string.signin_subtitle_label_1_end)}"
        )

        if (clickable) {
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
                resources.getString(R.string.signin_subtitle_label_1_start).length +
                        resources.getString(R.string.press_here).length + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    override fun onPause() {
        super.onPause()
        onBackPressedCallback.remove()
    }

    private fun fadeIn(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(400)
            .setListener(null)
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.signinSubtitle1.movementMethod = null
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }
}