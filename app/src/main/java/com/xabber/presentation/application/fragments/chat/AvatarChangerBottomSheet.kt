package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAvatarBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.account.AccountViewModel
import com.xabber.presentation.application.fragments.chat.message.EmojiBottomSheet
import com.xabber.utils.askUserForOpeningAppSettings
import com.xabber.utils.dp
import com.xabber.utils.isPermissionGranted

class AvatarChangerBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(BottomSheetAvatarBinding::bind)
    private val accountViewModel: AccountViewModel by viewModels()

    companion object {
        fun newInstance(accountId: String) = AvatarChangerBottomSheet().apply {
            arguments = Bundle().apply {
                putString("oo", accountId)
            }
        }
    }

    override fun getTheme(): Int = R.style.Theme_NoWiredStrapInNavigationBar
    private fun getAccountId(): String =
        requireArguments().getString("oo")!!

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotGalleryPermissionResult
    )

    private val cropImage = registerForActivityResult(CropImageContract()) {
        when {
            it.isSuccessful -> {
                if (it.uriContent != null) accountViewModel.saveAvatar(getAccountId(), it.uriContent.toString())
            }
            it is CropImage.CancelledResult -> Log.d(
                "Avatar",
                "cropping image was cancelled by the user"
            )
            else -> {
            }
        }
        dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.bottom_sheet_avatar, container, false)


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { setupBottomSheet(it) }
        return dialog
    }

    private fun setupBottomSheet(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(
            bottomSheet
        )
        bottomSheet.updateLayoutParams {
            this.height = 180.dp
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            emojiViewGroup.setOnClickListener {
                ChatSettingsManager.color = R.color.blue_100
                val dialog = EmojiBottomSheet.newInstance(getAccountId())
                navigator().showBottomSheetDialog(dialog)
                dismiss()
            }
            selfieViewGroup.setOnClickListener {
                if (isPermissionGranted(Manifest.permission.CAMERA) && isPermissionGranted(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    cropImageFromCamera()
                } else {
                    requestCameraPermissionLauncher.launch(
                        Manifest.permission.CAMERA,
                    )
                }
            }
            choseImageViewGroup.setOnClickListener {
                if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    cropImageFromGallery()
                } else {
                    requestGalleryPermissionLauncher.launch(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }
            }
        }
    }

    private fun onGotCameraPermissionResult(result: Boolean) {
        if (result) {
            cropImageFromCamera()
        } else askUserForOpeningAppSettings()
    }

    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) {
            cropImageFromGallery()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                askUserForOpeningAppSettings()
            }
        }
    }

    private fun cropImageFromCamera() {
        startCrop(includeCamera = true, includeGallery = false)
    }

    private fun cropImageFromGallery() {
        startCrop(includeCamera = false, includeGallery = true)
    }

    private fun startCrop(includeCamera: Boolean, includeGallery: Boolean) {
        cropImage.launch(options {
            setGuidelines(CropImageView.Guidelines.OFF).setImageSource(
                includeGallery, includeCamera
            ).setOutputCompressFormat(Bitmap.CompressFormat.PNG).setAspectRatio(
                1, 1
            ).setMinCropWindowSize(800, 800).setActivityBackgroundColor(Color.BLACK)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        requestCameraPermissionLauncher.unregister()
        requestGalleryPermissionLauncher.unregister()
    }

}
