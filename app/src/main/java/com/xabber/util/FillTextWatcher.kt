package com.xabber.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.xabber.R
import com.xabber.onboarding.fragments.signin.SigninFragment

class FillTextWatcher(private val editTextList: ArrayList<EditText>, private val button: Button, private val subTitle: TextView) :
    TextWatcher {
    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

    }

    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

    }

    override fun afterTextChanged(s: Editable?) {
        for (editText in editTextList) {
            if (editText.text.toString().trim().isEmpty()) {
                button.isEnabled = false
                break
            } else button.isEnabled = true
        }
      //  if (subTitle.text == R.string.signin_subtitle_error_message.toString())
    }
}