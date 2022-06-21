package com.xabber.presentation.onboarding.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.soundcloud.android.crop.Crop
import com.soundcloud.android.crop.CropImageActivity
import com.xabber.R
import com.xabber.databinding.ActivityOnboardingBinding
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.application.fragments.chat.FileManager.Companion.getFileUri
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.AppConstants.PUBLIC_DOWNLOADS
import com.xabber.presentation.application.util.AppConstants.REQUEST_TAKE_PHOTO
import com.xabber.presentation.application.util.AppConstants.TEMP_FILE_NAME
import com.xabber.presentation.onboarding.contract.Navigator
import com.xabber.presentation.onboarding.contract.ToolbarChanger
import com.xabber.presentation.onboarding.fragments.signin.SigninFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupAvatarFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupNicknameFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupPasswordFragment
import com.xabber.presentation.onboarding.fragments.signup.SignupUserNameFragment
import com.xabber.presentation.onboarding.fragments.start.StartFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*

/** OnBoarding Activity allows the user to log in or register in the application
 *
 */

class OnBoardingActivity : AppCompatActivity(), Navigator, ToolbarChanger {
    private val binding: ActivityOnboardingBinding by lazy {
        ActivityOnboardingBinding.inflate(layoutInflater)
    }

    private val viewModel: OnboardingViewModel by viewModels()
    private var newAvatarImageUri: Uri? = null
    private var nickName: String? = null
    private var userName: String? = null
    private var password: String? = null
    private var filePhotoUri: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotCameraPermissionResult
    )
    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotGalleryPermissionResult
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.onboardingToolbar.title = ""
        setSupportActionBar(binding.onboardingToolbar)
        subscribeToDataFromFragments()
        if (savedInstanceState == null) addStartFragment()
    }

    private fun subscribeToDataFromFragments() {
        viewModel.nickName.observe(this) {
            nickName = it
        }
        viewModel.username.observe(this) {
            userName = it
        }
        viewModel.password.observe(this) {
            password = it
        }
    }

    private fun addStartFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.onboarding_container, StartFragment()
        ).commit()
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.appearance, R.animator.disappearance)
            .addToBackStack(null)
            .replace(R.id.onboarding_container, fragment).commit()
    }

    override fun openSignupNicknameFragment() {
        openFragment(SignupNicknameFragment())
    }

    override fun openSignupUserNameFragment() {
        openFragment(SignupUserNameFragment())
    }

    override fun openSignupPasswordFragment() {
        openFragment(SignupPasswordFragment())
    }

    override fun openSignupAvatarFragment() {
        openFragment(SignupAvatarFragment())
    }

    override fun openSigninFragment() {
        openFragment(SigninFragment())
    }


    override fun goToApplicationActivity(isSignedIn: Boolean) {
        val intent = Intent(this, ApplicationActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(R.animator.appearance, R.animator.disappearance)
    }

    override fun openBottomSheetDialogFragment(dialog: BottomSheetDialogFragment) {
        dialog.show(supportFragmentManager, AppConstants.DIALOG_TAG)
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun openCamera() {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun openGallery() {
        requestGalleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun registerAccount() {
        CoroutineScope(Dispatchers.Main).launch {
            if (userName != null) viewModel.registerAccount(userName!!)
        }
    }


    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            createTempImageFile(TEMP_FILE_NAME).let {
                filePhotoUri = getFileUri(this, it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, filePhotoUri)
                startActivityForResult(
                    takePictureIntent,
                    REQUEST_TAKE_PHOTO
                )
            }
        } catch (e: IOException) {
            Log.e(AppConstants.LOG_TAG_EXCEPTION, e.stackTraceToString())
        }
    }


    private fun createTempImageFile(name: String): File {
        return File.createTempFile(
            name,
            ".jpg",
            application.getExternalFilesDir(null)
        )
    }


    private fun onGotCameraPermissionResult(granted: Boolean) {
        if (granted) {
            takePhoto()
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                askUserForOpeningAppSettings()
            }
        }
    }


    override fun setTitle(titleResId: Int) {
        binding.onboardingToolbar.setTitle(titleResId)
    }

    override fun clearTitle() {
        binding.onboardingToolbar.title = ""
    }

    override fun showArrowBack(isVisible: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(isVisible)
        supportActionBar?.setDisplayShowHomeEnabled(isVisible)
    }

    override fun onDestroy() {
        super.onDestroy()
        requestGalleryPermissionLauncher.unregister()
        requestCameraPermissionLauncher.unregister()
    }


    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) {
            chooseFromGallery()
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                askUserForOpeningAppSettings()
            }
        }
    }

    private fun chooseFromGallery() {
        Crop.pickImage(this)
    }

    private fun askUserForOpeningAppSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        if (packageManager.resolveActivity(
                appSettingsIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            ) != null
        ) {
            val dialog = AlertDialog.Builder(this)
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

    override fun clearBackStack() {
        for (i: Int in 0..supportFragmentManager.backStackEntryCount)
            supportFragmentManager.popBackStack(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
    }

      private fun isImageNeedRotation(srcUri: Uri?): Boolean {
                    val srcPath: String = getPath(applicationContext, srcUri) ?: return false
                    val exif: ExifInterface = try {
                        ExifInterface(srcPath)
                    } catch (e: IOException) {
                        Log.e("qwe", e.stackTraceToString())
                        return false
                    }
                    val orientation =
                        exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    return when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270 -> true
                        ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> false
                        else -> false
                    }
                }

    private fun beginCropProcess(source: Uri) {
        newAvatarImageUri = Uri.fromFile(File(this.cacheDir, TEMP_FILE_NAME))
        lifecycleScope.launch(Dispatchers.IO) {
            val isImageNeedPreprocess = (isImageSizeGreater(source, 256)
                    || isImageNeedRotation(source))
            launch(Dispatchers.Main) {
                if (isImageNeedPreprocess) {
                    preprocessAndStartCrop(source)
                } else {
                    startCrop(source)
                }
            }
        }
    }

     private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

       @SuppressLint("NewApi")
                fun getPath(context: Context?, uri: Uri?): String? {
                    if (uri == null) {
                        return null
                    }
                    val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

                    // DocumentProvider
                    if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
                        // ExternalStorageProvider
                        if (isExternalStorageDocument(uri)) {
                            val docId = DocumentsContract.getDocumentId(uri)
                            val split = docId.split(":").toTypedArray()
                            val type = split[0]
                            if ("primary".equals(type, ignoreCase = true)) {
                                return Environment.getExternalStorageDirectory()
                                    .toString() + "/" + split[1]
                            }

                            // TODO handle non-primary volumes
                        } else if (isDownloadsDocument(uri)) {
                            val id = DocumentsContract.getDocumentId(uri)
                            return if (id.startsWith("msf:")) {
                                val split = id.split(":").toTypedArray()
                                val selection = "_id=?"
                                val selectionArgs = arrayOf(split[1])
                                getDataColumn(
                                    applicationContext,
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    selection,
                                    selectionArgs
                                )
                            } else {
                                try {
                                    val contentUri = ContentUris.withAppendedId(
                                        PUBLIC_DOWNLOADS,
                                        java.lang.Long.valueOf(id)
                                    )
                                    getDataColumn(applicationContext, contentUri, null, null)
                                } catch (e: NumberFormatException) {
                                    val arr = id.split(":").toTypedArray()
                                    if (arr.size > 1) arr[1] else arr[0]
                                }
                            }
                        } else if (isMediaDocument(uri)) {
                            val docId = DocumentsContract.getDocumentId(uri)
                            val split = docId.split(":").toTypedArray()
                            val type = split[0]
                            var contentUri: Uri? = null
                            if ("image" == type) {
                                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            } else if ("video" == type) {
                                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            } else if ("audio" == type) {
                                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            }
                            val selection = "_id=?"
                            val selectionArgs = arrayOf(
                                split[1]
                            )
                            return getDataColumn(
                                applicationContext,
                                contentUri,
                                selection,
                                selectionArgs
                            )
                        }
                    } else if ("content".equals(uri.scheme, ignoreCase = true)) {
                        return getDataColumn(applicationContext, uri, null, null)
                    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                        return uri.path
                    }
                    return null
                }

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



    private fun isImageSizeGreater(srcUri: Uri?, maxSize: Int): Boolean {
        val srcPath: String = getPath(application, srcUri) ?: return false
        val fis: FileInputStream = try {
            FileInputStream(srcPath)
        } catch (e: FileNotFoundException) {
            return false
        }
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(fis, null, options)
        try {
            fis.close()
        } catch (e: IOException) {
            Log.e("qwe", e.stackTraceToString())
        }
        return options.outHeight > maxSize || options.outWidth > maxSize
    }

    private fun startCrop(srcUri: Uri) {
                    val cR = applicationContext.contentResolver
                    if (cR.getType(srcUri)!! == "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
//                            CropImage.activity(srcUri).setAspectRatio(1, 1)
//                                .setOutputCompressFormat(Bitmap.CompressFormat.PNG)
//                                .setOutputUri(newAvatarImageUri)
//                                .start(this)
                        else Crop.of(srcUri, newAvatarImageUri)
                            .asSquare()
                            .start(this)
                    else
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
//                            CropImage.activity(srcUri).setAspectRatio(1, 1)
//                                .setOutputCompressFormat(Bitmap.CompressFormat.JPEG)
//                                .setOutputUri(newAvatarImageUri)
//                                .start(this)
                        else
                            Crop.of(srcUri, newAvatarImageUri)
                                .asSquare()
                                .start(this)
                }



    private fun preprocessAndStartCrop(source: Uri) {
        Glide.with(this).asBitmap().load(source).diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(object : CustomTarget<Bitmap?>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap?>?
                ) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val cR = applicationContext.contentResolver
                        val imageFileType = cR.getType(source)
                        val stream = ByteArrayOutputStream()
                        if (imageFileType == "image/png") {
                            resource.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        } else {
                            resource.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        }
                        val data = stream.toByteArray()
                        resource.recycle()
                        try {
                            stream.close()
                        } catch (e: IOException) {
                            Log.e("qwe", e.stackTraceToString())
                        }
                        val rotatedImage: Uri? = if (imageFileType == "image/png") {
                            savePNGImage(data, AppConstants.ROTATE_FILE_NAME)
                        } else {
                            saveImage(data, AppConstants.ROTATE_FILE_NAME)
                        }
                        if (rotatedImage == null)
                            return@launch
                        launch(Dispatchers.Main) { startCrop(rotatedImage) }
                    }
                }
                  override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    Toast.makeText(
                        baseContext,
                        R.string.error_during_image_processing,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })

                }

                fun savePNGImage(data: ByteArray?, fileName: String): Uri? {
                    val rotateImageFile: File
                    var bos: BufferedOutputStream? = null
                    try {
                        rotateImageFile =
                            createTempPNGImageFile(fileName)
                        bos = BufferedOutputStream(FileOutputStream(rotateImageFile))
                        bos.write(data)
                    } catch (e: IOException) {
                        Log.e("qwe", e.stackTraceToString())
                        return null
                    } finally {
                        if (bos != null) {
                            try {
                                bos.flush()
                                bos.close()
                            } catch (e: IOException) {
                                Log.e("qwe", e.stackTraceToString())
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
                        Log.e("qwe", e.stackTraceToString())
                        return null
                    } finally {
                        if (bos != null) {
                            try {
                                bos.flush()
                                bos.close()
                            } catch (e: IOException) {
                                Log.e("qwe", e.stackTraceToString())
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
                        application.getExternalFilesDir(null)
                    )
                }

                private fun getFileUri(file: File): Uri {
                    return FileProvider.getUriForFile(
                        application, "Xabber" + ".provider",
                        file
                    )
                }













}



