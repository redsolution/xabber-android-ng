package com.xabber.presentation.application.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.fragment.app.DialogFragment
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.presentation.AppConstants
import com.xabber.utils.dp
import com.xabber.utils.setFragmentResult
import io.realm.kotlin.Realm

class DeletingMessageDialog : DialogFragment() {
    private val realm = Realm.open(defaultRealmConfig())
    private var checked = false
    private var checkBox: CheckBox? = null

    companion object {
        fun newInstance(name: String?, id: String?) = DeletingMessageDialog().apply {
            arguments = Bundle().apply {
                putString(AppConstants.DELETING_MESSAGE_NAME_KEY, name)
                putString(AppConstants.MESSAGE_ID, id)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = arguments?.getString(AppConstants.DELETING_MESSAGE_NAME_KEY)
            ?: resources.getString(R.string.contact_name_default)
        checked = savedInstanceState?.getBoolean(AppConstants.DIALOG_CHECKBOX_VALUE, false) ?: false
        val checkBoxLayout = LinearLayout(activity)
        checkBoxLayout.orientation = LinearLayout.HORIZONTAL
        checkBoxLayout.gravity = Gravity.CENTER_VERTICAL

        checkBox = CheckBox(activity)
        val checkboxText =
            SpannableStringBuilder().append(resources.getString(R.string.deleting_message_dialog_checkbox_text))
                .bold { append(" $name") }.append("?")
        checkBox?.text = checkboxText
        checkBox?.setPadding(12, 0, 0, 0)
        checkBox?.isChecked = checked
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(16.dp, 0, 0, 0)
        checkBoxLayout.addView(checkBox, layoutParams)

        val dialog = AlertDialog.Builder(context, R.style.MyAlertDialogStyle)
            .setView(checkBoxLayout)
            .setTitle(R.string.deleting_message_dialog_title)
            .setMessage(R.string.deleting_message_dialog_subtitle)
            .setPositiveButton(resources.getString(R.string.dialog_button_delete)) { _, _ ->
                setFragmentResult(
                    AppConstants.DELETING_MESSAGE_DIALOG_KEY,
                    bundleOf(
                        AppConstants.DELETING_MESSAGE_BUNDLE_KEY to true,
                        AppConstants.DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY to checkBox?.isChecked
                    )
                )
                dismiss()
            }.setNegativeButton(resources.getString(R.string.dialog_button_cancel), null)
        return dialog.create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (checkBox != null) outState.putBoolean(AppConstants.DIALOG_CHECKBOX_VALUE, checkBox!!.isChecked)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

}
