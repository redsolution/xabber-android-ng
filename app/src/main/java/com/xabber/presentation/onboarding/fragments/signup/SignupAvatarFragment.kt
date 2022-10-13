package com.xabber.presentation.onboarding.fragments.signup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import com.xabber.presentation.application.fragments.chat.FileManager
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import com.xabber.utils.mask.Mask
import com.xabber.utils.mask.MaskedDrawableBitmapShader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


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
            //   saveImage()
            //    navigator().goToApplicationActivity(true)
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

    private fun saveAvatarInInternalStorage() {
        val file = FileManager.generatePicturePath()
        //      saveToInternalStorage(avatarData)
        Log.d(
            "avatar",
            "ttttt ${onboardingViewModel.avatarUri.value}, ${onboardingViewModel.nickName.value}, ${onboardingViewModel.username.value}, ${onboardingViewModel.password.value}"
        )
    }

    fun SaveBitmapToInternalStorage(bitmap: Bitmap) {
        var directory = "com.xabber.android.ng.avatar"
        //can change directory as per need
        if (directory != null || directory != "") directory = "/" + directory
        var imagesDir =
            requireContext().getExternalFilesDirs(Environment.DIRECTORY_PICTURES + directory)

        var jFile = File("")
    }


    private fun saveImage() {
        val finalBitmap = MediaStore.Images.Media.getBitmap(
            requireContext().getContentResolver(),
            onboardingViewModel.avatarUri.value
        );
        val myDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .toString() + File.separator + "AppName" + File.separator + "photos"
        );
        myDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date());
        val fname = onboardingViewModel.nickName.value + timeStamp + ".jpg";
        Log.i("Image path: ", fname);

        val file = File(myDir, fname);
        if (file.exists()) file.delete();
        try {
            val out = FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (e: Exception) {
            e.printStackTrace();
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }

}
