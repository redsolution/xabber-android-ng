package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAttachBinding
import com.xabber.databinding.Test2Binding
import com.xabber.presentation.application.activity.ApplicationViewModel
import com.xabber.presentation.application.util.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Test2 : BottomSheetDialogFragment() {
    private var _binding: Test2Binding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Test2Binding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogStyle)
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {  }
        return dialog
    }


   }