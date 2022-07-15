package com.xabber.presentation.application.util

import android.net.Uri

object AppConstants {

    const val LOG_TAG_EXCEPTION = "log tag exception"
    const val REQUEST_EMOJI_KEY = "request emoji key"
    const val RESPONSE_EMOJI_KEY = "response emoji key"
    const val ROTATE_FILE_NAME = "rotated"
    const val TEMP_FILE_NAME = "cropped"

    const val UNREAD_MESSAGES_COUNT = "unread messages count"
    const val CONTACT_NAME = "contact name"
    const val MASK_KEY = "mask key"
    const val PARAMS_ACCOUNT_FRAGMENT = "params account fragment"
    const val SHARED_PREF_MASK_KEY = 2

    const val NO_CAPTCHA_KEY = "a75be9d697c34892b59ebe726dc1b377"

    const val REQUEST_TAKE_PHOTO = 3
    const val IMAGE_PICK_REQUEST_CODE = 10
    const val SELECT_FILE_REQUEST_CODE = 11

    const val DIALOG_TAG = "dialog_tag"

    val PUBLIC_DOWNLOADS = Uri.parse("content://downloads/public_downloads")

}