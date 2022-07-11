package com.xabber.presentation.onboarding.fragments.signup

import android.Manifest
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAvatarBinding
import com.xabber.presentation.application.fragments.chat.FileManager
import com.xabber.presentation.application.fragments.chat.FileManager.Companion.getFileUri
import com.xabber.presentation.application.util.askUserForOpeningAppSettings
import com.xabber.presentation.application.util.dp
import com.xabber.presentation.application.util.isPermissionGranted
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiAvatarBottomSheet
import java.io.File


class AvatarBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(BottomSheetAvatarBinding::bind)
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()
    private var currentPhotoUri: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotGalleryPermissionResult
    )

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(), ::onSaveImage
    )

    private val galleryResultLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
        ::onTakePictureFromGallery
    )

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
      //  if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) galleryResultLauncher.launch("image/*")
     //  galleryResultLauncher.launch("image/*")

        with(binding) {
            emojiViewGroup.setOnClickListener {
                navigator().openBottomSheetDialogFragment(EmojiAvatarBottomSheet())
                dismiss()
            }
            selfieViewGroup.setOnClickListener {
                requestCameraPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
            choseImageViewGroup.setOnClickListener {
                requestGalleryPermissionLauncher.launch(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                )
            }
        }
    }

    private fun takePhotoFromCamera() {
        val image: File? = FileManager.generatePicturePath()
        if (image != null) {
            currentPhotoUri = getFileUri(image, requireContext())
           cameraResultLauncher.launch(currentPhotoUri)
        }

    }

    private fun onGotCameraPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            takePhotoFromCamera()
        }
    }

    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) {
            chooseImageFromGallery()
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                askUserForOpeningAppSettings()
            }
        }
    }

    private fun chooseImageFromGallery() {
        galleryResultLauncher.launch("image/*")
    }

    private fun onTakePictureFromGallery(result: Uri?) {
        Log.d("resulturi", "$result")
       onboardingViewModel.setAvatarUri(result!!)
        dismiss()
    }


    private fun onSaveImage(result: Boolean) {
        if (result) {
            if (currentPhotoUri != null) onboardingViewModel.setAvatarUri(currentPhotoUri!!)
            dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
     //   requestCameraPermissionLauncher.unregister()
        requestGalleryPermissionLauncher.unregister()
      //  cameraResultLauncher.unregister()
        galleryResultLauncher.unregister()
    }

}