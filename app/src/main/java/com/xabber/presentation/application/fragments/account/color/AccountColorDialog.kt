package com.xabber.presentation.application.fragments.account.color

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.xabber.R
import com.xabber.databinding.DialogColorPickerBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.manage.ColorManager

class AccountColorDialog : DialogFragment(), AccountColorPickerAdapter.Listener {
    private lateinit var binding: DialogColorPickerBinding

    companion object {
        fun newInstance(colorKey: String) = AccountColorDialog().apply {
            arguments = Bundle().apply {
                putString(AppConstants.ACCOUNT_COLOR_KEY, colorKey)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogColorPickerBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(context).setView(binding.root)
            .setTitle(getString(R.string.account_color))
            .setNegativeButton(android.R.string.cancel, null)
        initColorList()
        return dialog.create()
    }

    private fun initColorList() {
        val colors =
            resources.obtainTypedArray(R.array.account_500)
        val colorKey = arguments?.getString(AppConstants.ACCOUNT_COLOR_KEY) ?: resources.getString(R.string.blue)
        val number = ColorManager.convertColorNameToIndex(colorKey)
        val adapter = AccountColorPickerAdapter(
            this,
            resources.getStringArray(R.array.account_color_names),
            colors, number
        )
        binding.colorList.layoutManager = LinearLayoutManager(context)
        binding.colorList.adapter = adapter
    }

    override fun onClick(color: String) {
        setFragmentResult(
            AppConstants.COLOR_REQUEST_KEY,
            bundleOf(AppConstants.COLOR_BUNDLE_KEY to color)
        )
        dismiss()
    }

}
