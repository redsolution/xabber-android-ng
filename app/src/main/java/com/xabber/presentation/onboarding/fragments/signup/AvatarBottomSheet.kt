package com.xabber.presentation.onboarding.fragments.signup

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAvatarBinding
import com.xabber.presentation.application.fragments.chat.FileManager.Companion.getFileUri
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.dp
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.contract.navigator
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiAvatarBottomSheet
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class AvatarBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(BottomSheetAvatarBinding::bind)
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()
    private var filePhotoUri: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ::onGotGalleryPermissionResult
    )

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("ppp", "result")
            if (result.resultCode == Activity.RESULT_OK) {
                val imageUri = result.data?.data
                context?.contentResolver?.takePersistableUriPermission(
                    imageUri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d("ppp", "$imageUri")
                if (imageUri != null) onboardingViewModel.setAvatarUri(imageUri)
            }
        }


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
        with(binding) {
            emojiViewGroup.setOnClickListener {
                navigator().openBottomSheetDialogFragment(EmojiAvatarBottomSheet())
                dismiss()
            }
            selfieViewGroup.setOnClickListener {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                dismiss()
            }
            choseImageViewGroup.setOnClickListener {
                chooseImageFromGallery()
                requestGalleryPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
                dismiss()
            }
        }
    }

    private fun onGotCameraPermissionResult(granted: Boolean) {
        if (granted) {
            takePhotoFromCamera()
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                askUserForOpeningAppSettings()
            }
        }
    }

    private fun onGotGalleryPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            chooseImageFromGallery()
        }
    }

    private fun chooseImageFromGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "image/*"
        resultLauncher.launch(intent)
    }


// starting to pick the image

    private fun askUserForOpeningAppSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireActivity().packageName, null)
        )
        if (requireActivity().packageManager.resolveActivity(
                appSettingsIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            ) != null
        ) {
            val dialog = AlertDialog.Builder(requireContext())
            dialog.setTitle(R.string.dialog_title_permission_denied)
                .setMessage(R.string.offer_to_open_settings)
                .setPositiveButton(R.string.dialog_button_open) { _, _ ->
                    startActivity(appSettingsIntent)
                }
                .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }
    }


    private fun takePhotoFromCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            createTempImageFile(AppConstants.TEMP_FILE_NAME).let {
                filePhotoUri = getFileUri(requireContext(), it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, filePhotoUri)
                startActivityForResult(
                    takePictureIntent,
                    1
                )
                Log.d("ppp", "ttt")
            }
        } catch (e: IOException) {
            Log.e(AppConstants.LOG_TAG_EXCEPTION, e.stackTraceToString())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("ppp", "ooo")
        if (requestCode == 1) Log.d("ppp", "get")
        // val bitmap = ImagePi
        //  Log.d("ppp", "$bitmap")
        //   onboardingViewModel.setAvatar(bitmap)

    }

    private fun createTempImageFile(name: String): File {
        return File.createTempFile(
            name,
            ".jpg",
            requireActivity().application.getExternalFilesDir(null)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        requestCameraPermissionLauncher.unregister()
        requestGalleryPermissionLauncher.unregister()
    }

//    private fun isImageNeedRotation(srcUri: Uri?): Boolean {
//        val srcPath: String = getPath(applicationContext, srcUri) ?: return false
//        val exif: ExifInterface = try {
//            ExifInterface(srcPath)
//        } catch (e: IOException) {
//            Log.e("TAG", e.stackTraceToString())
//            return false
//        }
//        val orientation =
//            exif.getAttributeInt(
//                ExifInterface.TAG_ORIENTATION,
//                ExifInterface.ORIENTATION_NORMAL
//            )
//        return when (orientation) {
//            ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270 -> true
//            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> false
//            else -> false
//        }
//    }
//
//    private fun beginCropProcess(source: Uri) {
//        newAvatarImageUri = Uri.fromFile(File(this.cacheDir, TEMP_FILE_NAME))
//        lifecycleScope.launch(Dispatchers.IO) {
//            val isImageNeedPreprocess = (isImageSizeGreater(source, 256)
//                    || isImageNeedRotation(source))
//            launch(Dispatchers.Main) {
//                if (isImageNeedPreprocess) {
//                    preprocessAndStartCrop(source)
//                } else {
//                    startCrop(source)
//                }
//            }
//        }
//    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

//    @SuppressLint("NewApi")
//    fun getPath(context: Context?, uri: Uri?): String? {
//        if (uri == null) {
//            return null
//        }
//        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
//
//        // DocumentProvider
//        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
//            // ExternalStorageProvider
//            if (isExternalStorageDocument(uri)) {
//                val docId = DocumentsContract.getDocumentId(uri)
//                val split = docId.split(":").toTypedArray()
//                val type = split[0]
//                if ("primary".equals(type, ignoreCase = true)) {
//                    return Environment.getExternalStorageDirectory()
//                        .toString() + "/" + split[1]
//                }
//
//                // TODO handle non-primary volumes
//            } else if (isDownloadsDocument(uri)) {
//                val id = DocumentsContract.getDocumentId(uri)
//                return if (id.startsWith("msf:")) {
//                    val split = id.split(":").toTypedArray()
//                    val selection = "_id=?"
//                    val selectionArgs = arrayOf(split[1])
//                    getDataColumn(
//                        applicationContext,
//                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
//                        selection,
//                        selectionArgs
//                    )
//                } else {
//                    try {
//                        val contentUri = ContentUris.withAppendedId(
//                            PUBLIC_DOWNLOADS,
//                            java.lang.Long.valueOf(id)
//                        )
//                        getDataColumn(applicationContext, contentUri, null, null)
//                    } catch (e: NumberFormatException) {
//                        val arr = id.split(":").toTypedArray()
//                        if (arr.size > 1) arr[1] else arr[0]
//                    }
//                }
//            } else if (isMediaDocument(uri)) {
//                val docId = DocumentsContract.getDocumentId(uri)
//                val split = docId.split(":").toTypedArray()
//                val type = split[0]
//                var contentUri: Uri? = null
//                if ("image" == type) {
//                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//                } else if ("video" == type) {
//                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
//                } else if ("audio" == type) {
//                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
//                }
//                val selection = "_id=?"
//                val selectionArgs = arrayOf(
//                    split[1]
//                )
//                return getDataColumn(
//                    applicationContext,
//                    contentUri,
//                    selection,
//                    selectionArgs
//                )
//            }
//        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
//            return getDataColumn(applicationContext, uri, null, null)
//        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
//            return uri.path
//        }
//        return null
//    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )
        try {
            cursor =
                context.contentResolver.query(
                    uri!!,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            return null
        } finally {
            cursor?.close()
        }
        return null
    }


//    private fun isImageSizeGreater(srcUri: Uri?, maxSize: Int): Boolean {
//        val srcPath: String = getPath(application, srcUri) ?: return false
//        val fis: FileInputStream = try {
//            FileInputStream(srcPath)
//        } catch (e: FileNotFoundException) {
//            return false
//        }
//        val options = BitmapFactory.Options()
//        options.inJustDecodeBounds = true
//        BitmapFactory.decodeStream(fis, null, options)
//        try {
//            fis.close()
//        } catch (e: IOException) {
//            Log.e("TAG", e.stackTraceToString())
//        }
//        return options.outHeight > maxSize || options.outWidth > maxSize
//    }
//
//    private fun startCrop(srcUri: Uri) {
//        val cR = applicationContext.contentResolver
//        if (cR.getType(srcUri)!! == "image/png")
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
////                            CropImage.activity(srcUri).setAspectRatio(1, 1)
////                                .setOutputCompressFormat(Bitmap.CompressFormat.PNG)
////                                .setOutputUri(newAvatarImageUri)
////                                .start(this)
//            else Crop.of(srcUri, newAvatarImageUri)
//                .asSquare()
//                .start(this)
//        else
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
////                            CropImage.activity(srcUri).setAspectRatio(1, 1)
////                                .setOutputCompressFormat(Bitmap.CompressFormat.JPEG)
////                                .setOutputUri(newAvatarImageUri)
////                                .start(this)
//            else
//                Crop.of(srcUri, newAvatarImageUri)
//                    .asSquare()
//                    .start(this)
//    }
//
//
//    private fun preprocessAndStartCrop(source: Uri) {
//        Glide.with(this).asBitmap().load(source).diskCacheStrategy(DiskCacheStrategy.NONE)
//            .skipMemoryCache(true)
//            .into(object : CustomTarget<Bitmap?>() {
//                override fun onResourceReady(
//                    resource: Bitmap,
//                    transition: Transition<in Bitmap?>?
//                ) {
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        val cR = applicationContext.contentResolver
//                        val imageFileType = cR.getType(source)
//                        val stream = ByteArrayOutputStream()
//                        if (imageFileType == "image/png") {
//                            resource.compress(Bitmap.CompressFormat.PNG, 100, stream)
//                        } else {
//                            resource.compress(Bitmap.CompressFormat.JPEG, 100, stream)
//                        }
//                        val data = stream.toByteArray()
//                        resource.recycle()
//                        try {
//                            stream.close()
//                        } catch (e: IOException) {
//                            Log.e("TAG", e.stackTraceToString())
//                        }
//                        val rotatedImage: Uri? = if (imageFileType == "image/png") {
//                            savePNGImage(data, AppConstants.ROTATE_FILE_NAME)
//                        } else {
//                            saveImage(data, AppConstants.ROTATE_FILE_NAME)
//                        }
//                        if (rotatedImage == null)
//                            return@launch
//                        launch(Dispatchers.Main) { startCrop(rotatedImage) }
//                    }
//                }
//
//              //  override fun onLoadFailed(errorDrawable: Drawable?) {
//                 //   super.onLoadFailed(errorDrawable)
////                    Toast.makeText(
////                        baseContext,
////                        R.string.error_during_image_processing,
////                        Toast.LENGTH_SHORT
////                    ).show()
//                }
//
//             //   override fun onLoadCleared(placeholder: Drawable?) {}
//            })
//
//    }

    fun savePNGImage(data: ByteArray?, fileName: String): Uri? {
        val rotateImageFile: File
        var bos: BufferedOutputStream? = null
        try {
            rotateImageFile =
                createTempPNGImageFile(fileName)
            bos = BufferedOutputStream(FileOutputStream(rotateImageFile))
            bos.write(data)
        } catch (e: IOException) {
            Log.e("TAG", e.stackTraceToString())
            return null
        } finally {
            if (bos != null) {
                try {
                    bos.flush()
                    bos.close()
                } catch (e: IOException) {
                    Log.e("TAG", e.stackTraceToString())
                }
            }
        }
        return getFileUri(rotateImageFile)
    }


    fun saveImage(data: ByteArray?, fileName: String): Uri? {
        val rotateImageFile: File
        var bos: BufferedOutputStream? = null
        try {
            rotateImageFile = createTempImageFile(fileName)
            bos = BufferedOutputStream(FileOutputStream(rotateImageFile))
            bos.write(data)
        } catch (e: IOException) {
            Log.e("TAG", e.stackTraceToString())
            return null
        } finally {
            if (bos != null) {
                try {
                    bos.flush()
                    bos.close()
                } catch (e: IOException) {
                    Log.e("TAG", e.stackTraceToString())
                }
            }
        }
        return getFileUri(rotateImageFile)
    }

    @Throws(IOException::class)
    fun createTempPNGImageFile(name: String): File {
        return File.createTempFile(
            name,
            ".png",
            activity?.application?.getExternalFilesDir(null)
        )
    }

    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            activity?.application!!, "Xabber" + ".provider",
            file
        )
    }


}