package com.xabber.presentation.onboarding.fragments.signup

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.setPadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.FragmentSignupAvatarBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.util.dp
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignupAvatarFragment : BaseFragment(R.layout.fragment_signup_avatar) {
    private val binding by viewBinding(FragmentSignupAvatarBinding::bind)
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

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
        binding.profileImageBackground.setOnClickListener {
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
            navigator().goToApplicationActivity(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }


    private fun saveAvatar() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (avatarData != null) {

                isImageSaved = true
            }
        }

    }
}