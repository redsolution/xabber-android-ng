package androidx.appcompat.widget

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.view.Gravity
import android.view.View


@SuppressLint("RestrictedApi")
class CustomPopupMenu(context: Context, anchor: View, center: Int): PopupMenu(context, anchor, center) {

    init {
        mPopup.setForceShowIcon(true)
    }
}