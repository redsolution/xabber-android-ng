package com.xabber.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText

class FillTextWatcher(private val editTextList: ArrayList<EditText>, private val button: Button) :
    TextWatcher {
    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

    }

    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

    }

    override fun afterTextChanged(p0: Editable?) {
        for (editText in editTextList) {
            if (editText.text.toString().trim().isEmpty()) {
                button.isEnabled = false
                break
            } else button.isEnabled = true
        }
    }
}