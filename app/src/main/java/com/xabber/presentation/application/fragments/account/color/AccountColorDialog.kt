package com.xabber.presentation.application.fragments.account.color

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R

class AccountColorDialog : DialogFragment(), AccountColorPickerAdapter.Listener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(context)
        dialog.setTitle(getString(R.string.account_color))
        val view = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colors =
            resources.obtainTypedArray(R.array.account_500)

        val adapter = AccountColorPickerAdapter(this,
            resources.getStringArray(R.array.account_color_names),
            colors, 10
        )
        val colorList = view.findViewById<RecyclerView>(R.id.color_list)
        colorList.layoutManager = LinearLayoutManager(context)
        colorList.adapter = adapter
        dialog.setView(view)
        dialog.setNegativeButton(android.R.string.cancel, null)
        return dialog.create()
    }

    override fun onClick(color: Int) {
        Log.d("color", "$color")
        dismiss()
    }

}
