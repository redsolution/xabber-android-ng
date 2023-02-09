package com.xabber.presentation.onboarding.fragments.signup

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.FragmentSignupAvatarBinding
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import io.realm.kotlin.internal.platform.RealmInitializer.Companion.filesDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class SignupAvatarFragment : Fragment(R.layout.fragment_signup_avatar) {
    private val binding by viewBinding(FragmentSignupAvatarBinding::bind)
    private val viewModel: OnboardingViewModel by activityViewModels()
    private val fileName = "avatar"
    private var isImageSaved = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_avatar_toolbar_title)
        toolbarChanger().showArrowBack(false)
        initButton()
        subscribeOnViewModelData()
    }

    private fun initButton() {
        binding.profileImage.setOnClickListener {
            navigator().openBottomSheetDialogFragment(AvatarBottomSheet())
        }
        binding.profilePhotoBackground.setOnClickListener {
            navigator().openBottomSheetDialogFragment(AvatarBottomSheet())
        }

        binding.avatarBtnNext.isEnabled = isImageSaved
        binding.avatarBtnNext.setOnClickListener {
            saveAvatar()
            viewModel.registerAccount()
            navigator().goToApplicationActivity()
        }
    }

    private fun subscribeOnViewModelData() {
        viewModel.avatarBitmap.observe(viewLifecycleOwner) {
            setAvatar(it)
        }
        viewModel.avatarUri.observe(viewLifecycleOwner) {
            setAvatar(it)
        }
    }

    private fun setAvatar(uri: Uri?) {
        val multiTransformation = MultiTransformation(CircleCrop())
        val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)

        Glide.with(requireContext())
            .load(bitmap)
            .apply(
                RequestOptions.bitmapTransform(multiTransformation)
                    .placeholder(R.drawable.avatar_place_holder)
                    .skipMemoryCache(true)
            )
            .into(binding.profileImage)
        binding.avatarBtnNext.isEnabled = true
    }

    private fun setAvatar(bitmap: Bitmap) {
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(requireContext())
            .load(bitmap)
            .apply(
                RequestOptions.bitmapTransform(multiTransformation)
                    .skipMemoryCache(true)
            )
            .into(binding.profileImage)
        binding.avatarBtnNext.isEnabled = true
    }

    private fun saveAvatar() {
        val bitmap = (binding.profileImage.drawable as BitmapDrawable).bitmap
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)

        val bytesArray = stream.toByteArray()
        val file = File(filesDir, fileName)
        FileOutputStream(file).use {
            it.write(bytesArray)
        }
        val avatarUri = Uri.fromFile(file)
        viewModel.setSavedAvatarUri(avatarUri.toString())
    }

}
