package com.xabber.presentation.application.activity

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.xabber.R
import com.xabber.models.xmpp.account.AccountViewModel
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.dialogs.TimeMute
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeDialogFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiAvatarBottomSheet
import com.xabber.utils.askUserForOpeningAppSettings
import com.xabber.utils.parcelable
import com.xabber.utils.setFragmentResult

class AvatarBottomSheetDialog: DialogFragment() {
    lateinit var emojiViewGroup: RelativeLayout
    lateinit var selfieViewGroup: RelativeLayout
    lateinit var choseImageViewGroup: RelativeLayout
    private val viewModel: AccountViewModel by activityViewModels()

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotGalleryPermissionResult
    )

    private val cropImage = registerForActivityResult(CropImageContract()) {
        when {
            it.isSuccessful -> {
                val bitmap = MediaStore.Images.Media.getBitmap(
                    requireContext().contentResolver,
                    it.uriContent
                )
                setFragmentResult(
                    "AA",
                    bundleOf("AA" to it.uriContent.toString())
                )
                dismiss()
            }
            it is CropImage.CancelledResult -> Log.d(
                "Avatar",
                "cropping image was cancelled by the user"
            )
            else -> {
                Log.d("Avatar", "${it.error}")
            }
        }
        dismiss()
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
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(context)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_avatar, null)
        view.setBackgroundColor(Color.WHITE)
        emojiViewGroup = view.findViewById(R.id.emoji_view_group)
        selfieViewGroup = view.findViewById(R.id.selfie_view_group)
        choseImageViewGroup = view.findViewById(R.id.chose_image_view_group)
        emojiViewGroup.setOnClickListener { EmojiAvatarBottomSheet().show(parentFragmentManager, "")
        dismiss() }
        selfieViewGroup.setOnClickListener { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)  }
        choseImageViewGroup.setOnClickListener { requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
        dialog.setView(view)
        return dialog.create()
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
