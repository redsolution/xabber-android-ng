package com.xabber.presentation.onboarding.fragments.signup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.FragmentSignupAvatarBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.Mask
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignupAvatarFragment : BaseFragment(R.layout.fragment_signup_avatar) {
    private val binding by viewBinding(FragmentSignupAvatarBinding::bind)
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()
    val maskedDrawable = MaskedDrawableBitmapShader()


    private var avatarData: ByteArray? = null
    private var isImageSaved = false

    private var onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().finishActivity()
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_avatar_toolbar_title)
        toolbarChanger().showArrowBack(false)
        prepareMask()
        initButton()
        onboardingViewModel.avatarBitmap.observe(viewLifecycleOwner) {
            setAvatar(it)
        }
        onboardingViewModel.avatarUri.observe(viewLifecycleOwner) {
            setAvatar(it)
        }
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    private fun setAvatar(uri: Uri?) {
        val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        maskedDrawable.setPictureBitmap(bitmap)
        with(binding) {
            Glide.with(this@SignupAvatarFragment)
                .load(maskedDrawable)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.avatar_place_holder)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .dontAnimate()
                )
                .into(profileImage)
            avatarBtnNext.isEnabled = true
        }
    }

    private fun setAvatar(bitmap: Bitmap) {
        maskedDrawable.setPictureBitmap(bitmap)
        with(binding) {
            Glide.with(requireContext())
                .load(maskedDrawable)
                .apply(
                    RequestOptions()

                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .dontAnimate()
                )
                .into(profileImage)
            avatarBtnNext.isEnabled = true
        }
    }


    private fun prepareMask() {
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, Mask.Circle.size176).extractAlpha()
        maskedDrawable.setMaskBitmap(mMaskBitmap)
    }

    private fun initButton() {
        binding.profileImage.setOnClickListener {
            navigator().openBottomSheetDialogFragment(AvatarBottomSheet())
        }
        binding.profilePhotoBackground.setOnClickListener {
            navigator().openBottomSheetDialogFragment(AvatarBottomSheet())
        }

        binding.avatarBtnNext.setOnClickListener {
            saveAvatar()
        }

        binding.avatarBtnNext.isEnabled = isImageSaved
        binding.avatarBtnNext.setOnClickListener {
            saveAvatarInInternalStorage()
            navigator().goToApplicationActivity(true)
        }
    }

    private fun saveAvatar() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (avatarData != null) {
             onboardingViewModel.avatarUri
                isImageSaved = true
            }
        }
    }

    private fun saveAvatarInInternalStorage(){
        Log.d("avatar", "$avatarData")
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }
}
