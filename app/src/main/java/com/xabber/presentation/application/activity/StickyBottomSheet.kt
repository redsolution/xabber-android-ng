package com.xabber.presentation.application.activity

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.constraintlayout.widget.ConstraintLayout
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.DialogInterface.OnShowListener
import android.content.DialogInterface
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.xabber.presentation.application.activity.StickyBottomSheet
import android.widget.FrameLayout
import com.xabber.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.util.DisplayMetrics
import android.app.Activity
import android.app.Dialog
import android.view.View
import com.xabber.databinding.Test4Binding
import java.util.ArrayList

class StickyBottomSheet : BottomSheetDialogFragment() {
    private var binding: Test4Binding? = null
    private var buttonLayoutParams: ConstraintLayout.LayoutParams? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = Test4Binding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //   Adapter adapter = new Adapter(initString());
        binding!!.sheetRecyclerview.layoutManager =
            LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding!!.sheetRecyclerview.setHasFixedSize(true)
        //  binding.sheetRecyclerview.setAdapter(adapter);
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface: DialogInterface -> setupRatio(dialogInterface as BottomSheetDialog) }
        (dialog as BottomSheetDialog).behavior.addBottomSheetCallback(object :
            BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset > 0) //Sliding happens from 0 (Collapsed) to 1 (Expanded) - if so, calculate margins
                    buttonLayoutParams!!.topMargin =
                        ((expandedHeight - buttonHeight - collapsedMargin) * slideOffset + collapsedMargin).toInt() else  //If not sliding above expanded, set initial margin
                    buttonLayoutParams!!.topMargin = collapsedMargin
                binding!!.sheetButton.layoutParams =
                    buttonLayoutParams //Set layout params to button (margin from top)
            }
        })
        return dialog
    }

    private fun setupRatio(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet =
            bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet) ?: return

        //Retrieve button parameters
        buttonLayoutParams = binding!!.sheetButton.layoutParams as ConstraintLayout.LayoutParams

        //Retrieve bottom sheet parameters
        BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_COLLAPSED
        val bottomSheetLayoutParams = bottomSheet.layoutParams
        bottomSheetLayoutParams.height = bottomSheetDialogDefaultHeight
        expandedHeight = bottomSheetLayoutParams.height
        val peekHeight =
            (expandedHeight / 1.3).toInt() //Peek height to 70% of expanded height (Change based on your view)

        //Setup bottom sheet
        bottomSheet.layoutParams = bottomSheetLayoutParams
        BottomSheetBehavior.from(bottomSheet).skipCollapsed = false
        BottomSheetBehavior.from(bottomSheet).peekHeight = peekHeight
        BottomSheetBehavior.from(bottomSheet).isHideable = true

        //Calculate button margin from top
        buttonHeight =
            binding!!.sheetButton.height + 40 //How tall is the button + experimental distance from bottom (Change based on your view)
        collapsedMargin = peekHeight - buttonHeight //Button margin in bottom sheet collapsed state
        buttonLayoutParams!!.topMargin = collapsedMargin
        binding!!.sheetButton.layoutParams = buttonLayoutParams

        //OPTIONAL - Setting up margins
        val recyclerLayoutParams =
            binding!!.sheetRecyclerview.layoutParams as ConstraintLayout.LayoutParams
        val k =
            (buttonHeight - 60) / buttonHeight.toFloat() //60 is amount that you want to be hidden behind button
        recyclerLayoutParams.bottomMargin =
            (k * buttonHeight).toInt() //Recyclerview bottom margin (from button)
        binding!!.sheetRecyclerview.layoutParams = recyclerLayoutParams
    }

    //Calculates height for 90% of fullscreen
    private val bottomSheetDialogDefaultHeight: Int
        private get() = windowHeight * 90 / 100

    //Calculates window height for fullscreen use
    private val windowHeight: Int
        private get() {
            val displayMetrics = DisplayMetrics()
            (requireContext() as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.heightPixels
        }

    private fun initString(): List<String> {
        val list: MutableList<String> = ArrayList()
        for (i in 0..34) list.add("Item $i")
        return list
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private var instance: StickyBottomSheet? = null
        private var collapsedMargin = 0
        private var buttonHeight = 0
        private var expandedHeight = 0
        fun newInstance(): StickyBottomSheet? {
            if (instance == null) instance = StickyBottomSheet()
            return instance
        }
    }
}