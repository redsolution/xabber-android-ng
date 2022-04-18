package com.xabber.presentation.onboarding.fragments.signup

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.FragmentSignupAvatarBinding
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.contract.toolbarChanger
import com.xabber.data.util.AppConstants
import com.xabber.data.util.AppConstants.TEMP_FILE_NAME
import com.xabber.data.util.dp
import java.io.File

class SignupAvatarFragment : Fragment() {
    private var binding : FragmentSignupAvatarBinding? = null


    private val newAvatarImageUri: Uri by lazy {
        File(requireContext().cacheDir, AppConstants.TEMP_FILE_NAME).toUri()
    }

    private var avatarData: ByteArray? = null
    private var imageFileType: String? = null

    private val KB_SIZE_IN_BYTES: Int = 1024
    private var FINAL_IMAGE_SIZE: Int = 0
    private var MAX_IMAGE_RESIZE: Int = 256

    private var isImageSaved = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSignupAvatarBinding.inflate(inflater)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarChanger().setTitle(R.string.signup_avatar_toolbar_title)
        toolbarChanger().setShowBack(false)
        initButton()


        val file = File(requireContext().cacheDir, TEMP_FILE_NAME)
        if (savedInstanceState != null) {
            if (file.exists())
                setAvatar(Uri.fromFile(file))
            else
                binding!!.profileImageEmoji.setPadding(21.dp)
        }
        else {
            if (file.exists())
                file.delete()
        }
       binding?.avatarBtnNext?.isEnabled = isImageSaved

       // btnNextContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
      //      verticalBias = 1.0f
    //    }

        binding?.profileImageBackground?.setOnClickListener {
            AvatarBottomSheet().show(parentFragmentManager, null)
        }
        binding!!.profilePhotoBackground.setOnClickListener {
            AvatarBottomSheet().show(parentFragmentManager, null)
        }

        binding!!.avatarBtnNext.setOnClickListener {
//                        checkAvatarSizeAndPublish()
            Toast.makeText(
                requireContext(),
                resources.getString(R.string.feature_not_created), Toast.LENGTH_SHORT
            ).show()
        }
    }




 private fun setAvatar(uri: Uri?) {
        with(binding!!) {
            profileImageEmoji.setPadding(0.dp)
            Glide.with(this@SignupAvatarFragment)
                .load(uri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .dontAnimate()
                )
                .into(profileImageEmoji)
            avatarBtnNext.isEnabled = true
        }
    }

    private fun setAvatar(bitmap: Bitmap) {
        with(binding!!) {
            profileImageEmoji.setPadding(0.dp)
            Glide.with(this@SignupAvatarFragment)
                .load(bitmap)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .dontAnimate()
                )
                .into(profileImageEmoji)
            avatarBtnNext.isEnabled = true
        }
    }


    private fun initButton() {
        binding?.avatarBtnNext?.setOnClickListener {
            navigator().goToApplicationActivity()
        }
    }
}