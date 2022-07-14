package com.xabber.presentation.application.fragments.account

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.xabber.R
import com.xabber.databinding.DialogColorPickerBinding
import com.xabber.presentation.application.activity.UiChanger

class AccountColorDialog : DialogFragment() {
    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogColorPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("Recycle")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val colors = resources.obtainTypedArray(R.array.account_500)
        val colorIndex = if (UiChanger.getAccountColor() == null) R.color.red_500 else UiChanger.getAccountColor()
        binding.colorList.layoutManager = LinearLayoutManager(context)
        val adapter = AccountColorPickerAdapter(
            resources.getStringArray(R.array.account_color_names),
                 colors, colorIndex!!
        ) { UiChanger.setAccountColor(it) }
        binding.colorList.adapter = adapter
    }



}