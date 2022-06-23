package com.xabber.presentation.onboarding.fragments.signup

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.core.view.setPadding
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.presentation.application.util.dp
import com.xabber.databinding.FragmentSignupAvatarBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.AppConstants.TEMP_FILE_NAME
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import java.io.File

class SignupAvatarFragment : BaseFragment(R.layout.fragment_signup_avatar) {
    private val binding by viewBinding(FragmentSignupAvatarBinding::bind)

    private val newAvatarImageUri: Uri by lazy {
        File(requireContext().cacheDir, AppConstants.TEMP_FILE_NAME).toUri()
    }


    private var avatarData: ByteArray? = null
    private var imageFileType: String? = null

    private val KB_SIZE_IN_BYTES: Int = 1024
    private var FINAL_IMAGE_SIZE: Int = 0
    private var MAX_IMAGE_RESIZE: Int = 256

    private var isImageSaved = false

      private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
                activity?.finish()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_avatar_toolbar_title)
        toolbarChanger().showArrowBack(false)
        initButton()
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)


        val file = File(requireContext().cacheDir, TEMP_FILE_NAME)
        if (savedInstanceState != null) {
            if (file.exists())
                setAvatar(Uri.fromFile(file))
            else
                binding.profileImage.setPadding(21.dp)
        } else {
            if (file.exists())
                file.delete()
        }
        binding.avatarBtnNext.isEnabled = isImageSaved

        binding.profileImageBackground.setOnClickListener {
            navigator().openBottomSheetDialogFragment(AvatarBottomSheet())
        }
        binding.profilePhotoBackground.setOnClickListener {
            navigator().openBottomSheetDialogFragment(AvatarBottomSheet())
        }

        binding.avatarBtnNext.setOnClickListener {
//                        checkAvatarSizeAndPublish()
            Toast.makeText(
                requireContext(),
                resources.getString(R.string.application_title), Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setAvatar(uri: Uri?) {
        with(binding) {
            profileImage.setPadding(0.dp)
            Glide.with(this@SignupAvatarFragment)
                .load(uri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .dontAnimate()
                )
                .into(profileImage)
            avatarBtnNext.isEnabled = true
        }
    }

    private fun setAvatar(bitmap: Bitmap) {
        with(binding) {
            profileImage.setPadding(0.dp)
            Glide.with(requireContext())
                .load(bitmap)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .dontAnimate()
                )
                .into(profileImage)
            avatarBtnNext.isEnabled = true
        }
    }


    private fun initButton() {
        binding.avatarBtnNext.setOnClickListener {
            navigator().goToApplicationActivity(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }


}