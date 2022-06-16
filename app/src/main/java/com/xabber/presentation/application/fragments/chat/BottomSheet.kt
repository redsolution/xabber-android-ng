package com.xabber.presentation.application.fragments.chat

import android.app.Dialog
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.data.util.dp

class BottomSheet : BottomSheetDialogFragment() {
    lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheet = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        val view = View.inflate(context, R.layout.bottom_sheet_attach, null)
        bottomSheet.setContentView(view)
        val v = view.findViewById<LinearLayout>(R.id.li)
        val appBar = view.findViewById<AppBarLayout>(R.id.appBarLayout)
        bottomSheetBehavior = BottomSheetBehavior.from(v)

        //setting Peek at the 16:9 ratio keyline of its parent.
        bottomSheetBehavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO;


        //setting max height of bottom sheet
        view.setMinimumHeight((Resources.getSystem().getDisplayMetrics().heightPixels) / 2);

        val bottomSheetBehaviorCallback =
            object : BottomSheetBehavior.BottomSheetCallback() {

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    dismiss()

                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (BottomSheetBehavior.STATE_EXPANDED == newState) {
                       showView(appBar)



                    }
                    if (BottomSheetBehavior.STATE_COLLAPSED == newState) {
                        hideAppbar(appBar)
                    }

                    if (BottomSheetBehavior.STATE_HIDDEN == newState) {
                        dismiss()
                    }
                }
            }
        bottomSheetBehavior.setBottomSheetCallback(bottomSheetBehaviorCallback)
        return bottomSheet

    }

    override fun onStart() {
        super.onStart()

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private fun showView(view: View) {
        val params = view.layoutParams
        params.height = 100.dp
        view.layoutParams = params
    }

    private fun hideAppbar(view: View) {
        val params = view.layoutParams
        params.height = 0
        view.layoutParams = params
    }

    private fun getActionBarSize(): Int {
        val typedArray = context?.theme?.obtainStyledAttributes(intArrayOf())
        val size = typedArray?.getDimension(0, 0F)
        return size!!.toInt()

    }
}