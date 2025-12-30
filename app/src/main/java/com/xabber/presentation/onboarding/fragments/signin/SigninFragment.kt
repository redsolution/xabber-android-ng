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
import android.widget.Toast
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

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().goBack()
        }
    }

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
                btnConnect.isEnabled = false  // Disable immediately to prevent multiple clicks

                val jid = editTextLogin.text?.trim().toString().let {
                    if (!it.contains('@')) "$it@$host" else it
                }
                val username = jid.split("@")[0]
                val password = editTextPassword.text?.trim().toString()

                Log.d("SigninFragment", "Sign-in attempt with jid: $jid, username: $username")

                if (!viewModel.isJidValid(jid)) {
                    binding.signinSubtitle1.isInvisible = true
                    binding.errorSubtitle.isVisible = true
                    binding.errorSubtitle.text = "Invalid JID format"
                    btnConnect.isEnabled = true  // Re-enable on quick validation failure
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    try {
                        val success = AccountManager.login(jid, username, password)
                        if (success) {
                            Log.d("SigninFragment", "Account creation (login) successful for jid $jid, navigating to ApplicationActivity")
                            navigator().goToApplicationActivity()
                            // No re-enable needed: navigation occurs, fragment lifecycle ends
                        } else {
                            Log.w("SigninFragment", "Account creation (login) failed for jid $jid")
                            binding.signinSubtitle1.isInvisible = true
                            binding.errorSubtitle.isVisible = true
                            binding.errorSubtitle.text = "Failed to create account"
                            Toast.makeText(requireContext(), "Failed to create account", Toast.LENGTH_LONG).show()
                            btnConnect.isEnabled = true  // Re-enable on failure
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.w("SigninFragment", "Account creation error for jid $jid: ${e.message}")
                        binding.signinSubtitle1.isInvisible = true
                        binding.errorSubtitle.isVisible = true
                        when (e.message) {
//                            "Account already exists" -> {
//                                binding.errorSubtitle.text = "Account already exists"
//                                Toast.makeText(requireContext(), "Account already exists, please choose another JID", Toast.LENGTH_LONG).show()
//                            }
                            "Invalid credentials" -> {
                                binding.errorSubtitle.text = "Invalid JID, username, or password"
                                Toast.makeText(requireContext(), "Invalid JID, username, or password", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                binding.errorSubtitle.text = "Error: ${e.message}"
                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        btnConnect.isEnabled = true  // Re-enable on exception
                    } catch (e: Exception) {
                        Log.e("SigninFragment", "Unexpected error during account creation for jid $jid: ${e.message}", e)
                        binding.signinSubtitle1.isInvisible = true
                        binding.errorSubtitle.isVisible = true
                        binding.errorSubtitle.text = "Unexpected error: ${e.message}"
                        Toast.makeText(requireContext(), "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
                        btnConnect.isEnabled = true  // Re-enable on unexpected error
                    }
                }
            }
        }
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

    override fun onDestroy() {
        super.onDestroy()
        binding.signinSubtitle1.movementMethod = null
        realm.close()
    }
}