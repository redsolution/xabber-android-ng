package com.xabber.onboarding.fragments.signup

import android.net.Uri
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.xabber.util.AppConstants
import java.io.File

class SignupAvatarFragment : Fragment() {


    private val newAvatarImageUri: Uri by lazy {
        File(requireContext().cacheDir, AppConstants.TEMP_FILE_NAME).toUri()
    }

    private var avatarData: ByteArray? = null
    private var imageFileType: String? = null

    private val KB_SIZE_IN_BYTES: Int = 1024
    private var FINAL_IMAGE_SIZE: Int = 0
    private var MAX_IMAGE_RESIZE: Int = 256

    private var isImageSaved = false
}