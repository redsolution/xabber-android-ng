//package com.xabber.presentation.application.fragments.accountmenu
//
//import android.util.Size
//import android.view.Gravity
//import android.view.LayoutInflater
//import android.view.View
//import android.widget.PopupWindow
//import com.xabber.R
//
//import com.xabber.presentation.application.contract.navigator
//import com.xabber.presentation.application.fragments.contacts.ContactAccountFragment
//import com.xabber.presentation.application.fragments.contacts.ContactAccountParams
//
//
//fun showPopupWindow(anchor: View) {
//    val context = anchor.context
//    val inflater = LayoutInflater.from(context)
//    val popupView = inflater.inflate(R.layout.fragment_contact_account, null).apply {
//        measure(
//            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
//            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
//        )
//    }
//
//        //popupView.
//    val newWidth = (popupView.measuredWidth * 2).toInt()
//    val newHeight = (popupView.measuredHeight * 2).toInt()
//
//    val popupWindow = PopupWindow(popupView, newWidth, newHeight, true).apply {
//        isOutsideTouchable = true
//    }
//
//    // Get the screen dimensions
//    val displayMetrics = context.resources.displayMetrics
//    val screenWidth = displayMetrics.widthPixels
//    val screenHeight = displayMetrics.heightPixels
//
//    // Calculate center position
//    val xPos = (screenWidth - newWidth) / 2
//    val yPos = (screenHeight - newHeight) / 2
//
//    // Show popup at the center of the screen
//    popupWindow.showAtLocation(anchor, Gravity.TOP or Gravity.START, xPos, yPos)
//
//
//}