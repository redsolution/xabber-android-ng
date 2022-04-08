package com.xabber.onboarding.fragments

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.xabber.ChoiceTitleToolbar
import com.xabber.R
import com.xabber.util.FillTextWatcher
import com.xabber.databinding.FragmentSigninBinding
import com.xabber.navigate

class SignInFragment() : Fragment(), ChoiceTitleToolbar {
    private var binding: FragmentSigninBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSigninBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initEditText()
        initButton()
        binding?.signinSubtitle1?.text = getSubtitleClickableSpan()
        binding?.signinSubtitle1?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun initEditText() {

        with(binding!!) {
            val editTextList = arrayListOf<EditText>(etLogin, etPassword)
            val textWatcher = FillTextWatcher(editTextList, btnConnect)
            for (editText in editTextList) editText.addTextChangedListener(textWatcher)
        }
        binding?.etLogin?.setOnFocusChangeListener { _, hasFocused ->
            if (hasFocused) {
                binding?.etLogin?.hint = ""
             //   !hasFocused && binding?.etLogin?.text!!.isNotEmpty() ->
             //   binding?.etLogin?.hint = ""
                binding?.etLogin?.background = resources.getDrawable(R.drawable.frame_blue)
            } else {
                binding?.etLogin?.hint =
                    resources.getString(R.string.signin_edit_text_jid_label)
                binding?.etLogin?.background = resources.getDrawable(R.drawable.frame_normal)
            }
        }

        binding?.etPassword?.setOnFocusChangeListener { _, hasFocused ->
            if (hasFocused) {
                binding?.etPassword?.background = resources.getDrawable(R.drawable.frame_blue)
                binding?.etPassword?.hint = ""
            }
            //  !hasFocused && binding?.etPassword?.text!!.isNotEmpty() ->
            //      binding?.etPassword?.hint = ""
            else {
                binding?.etPassword?.background =
                    resources.getDrawable(R.drawable.frame_normal)
                binding?.etPassword?.hint =
                    resources.getString(R.string.signin_edit_text_password_label)
            }

        }
    }


    private fun initButton() {
        with(binding!!) {
            btnConnect.setOnClickListener {
                btnConnect.isEnabled = false
                binding?.btnConnect!!.text = "Connecting..."
                rvFeature.visibility = View.VISIBLE
                closeKeyboard()


                // if invalid username/password
                //   signinSubtitle1.setTextColor(
                //   ResourcesCompat.getColor(
                //      resources,
                //      R.color.red_600,
                //      requireContext().theme
                //   )
                //  )
                //   signinSubtitle1.text =
                //       resources.getString(R.string.signin_subtitle_error_message)
                //  }
                btnRock.setOnClickListener {
                    navigate().goMainActivity()
                }
            }
        }
    }


    private fun closeKeyboard() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            binding?.etPassword?.windowToken,
            0
        )
    }


    private fun getSubtitleClickableSpan(): Spannable {
        val spannable =
            SpannableStringBuilder(resources.getString(R.string.signin_subtitle_label_1))
        spannable.setSpan(
            ForegroundColorSpan(
                ResourcesCompat.getColor(
                    resources,
                    R.color.blue_600,
                    requireContext().theme
                )
            ),
            34,
            44,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(p0: View) {
                    navigate().startSignUpFragment()
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            },
            34,
            44,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        return spannable
    }

    override fun getTitle(): String = "Sign in"

}
