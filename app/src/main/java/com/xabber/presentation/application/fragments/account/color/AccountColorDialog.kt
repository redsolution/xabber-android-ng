package com.xabber.presentation.application.fragments.account.color

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.activity.UiChanger
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query

class AccountColorDialog : DialogFragment(), AccountColorPickerAdapter.Listener {
    val realm = Realm.open(defaultRealmConfig())
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(context)
        dialog.setTitle(getString(R.string.account_color))
        val view = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colors =
            resources.obtainTypedArray(R.array.account_500)

        var colorKey: String? = null

        realm.writeBlocking {
            val account = this.query(AccountStorageItem::class).first().find()
            colorKey = account?.colorKey
        }
Log.d("color", "colorkey = $colorKey")
        val number = if (colorKey != null) ColorManager.convertColorNameToIndex(colorKey!!) else 10
Log.d("number", "$number")
        val adapter = AccountColorPickerAdapter(this,
            resources.getStringArray(R.array.account_color_names),
            colors, number
        )
        val colorList = view.findViewById<RecyclerView>(R.id.color_list)
        colorList.layoutManager = LinearLayoutManager(context)
        colorList.adapter = adapter
        dialog.setView(view)
        dialog.setNegativeButton(android.R.string.cancel, null)
        return dialog.create()
    }

    override fun onClick(color: String) {
    Log.d("color", "$color")
       realm.writeBlocking {
        val item =   this.query(AccountStorageItem::class).first().find()
           item?.colorKey = color
       }
        dismiss()
    }

}
