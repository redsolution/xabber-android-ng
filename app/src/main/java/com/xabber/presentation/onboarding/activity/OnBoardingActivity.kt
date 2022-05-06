package com.xabber.presentation.onboarding.activity

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.soundcloud.android.crop.Crop
import com.xabber.BuildConfig
import com.xabber.R
import com.xabber.data.util.AppConstants.REQUEST_TAKE_PHOTO
import com.xabber.data.util.AppConstants.TEMP_FILE_NAME
import com.xabber.databinding.ActivityOnboardingBinding
import com.xabber.presentation.onboarding.contract.ResultListener
import com.xabber.presentation.onboarding.fragments.signup.*
import java.io.File
import java.io.IOException

class OnBoardingActivity : AppCompatActivity() {
    private var binding: ActivityOnboardingBinding? = null
    private val navHost: NavHostFragment
        get() = supportFragmentManager.findFragmentById(R.id.onboarding_container) as NavHostFragment
lateinit var navController : NavController
    private var filePhotoUri: Uri? = null
    private var newAvatarImageUri: Uri? = null


    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotGalleryPermissionResult
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        navController = navHost.navController
        NavigationUI.setupActionBarWithNavController(this, navController)

    }

    override fun onSupportNavigateUp(): Boolean {
        return super.onSupportNavigateUp()
     //  return navController.navigateUp() || super.onSupportNavigateUp()
    }

   fun openCamera() {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun openGallery() {
        requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }


    private fun onGotCameraPermissionResult(granted: Boolean) {
        if (granted) {
            takePhoto()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                askUserForOpeningAppSettings()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            createTempImageFile(TEMP_FILE_NAME).let {
                filePhotoUri = getFileUri(it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, filePhotoUri)
                startActivityForResult(
                    takePictureIntent,
                    REQUEST_TAKE_PHOTO
                )
            }
        } catch (e: IOException) {
            Log.e("qwe", e.stackTraceToString())
        }
    }


    fun createTempImageFile(name: String): File {
        return File.createTempFile(
            name,
            ".jpg",
            application.getExternalFilesDir(null)
        )
    }


    private fun askUserForOpeningAppSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        if (packageManager.resolveActivity(
                appSettingsIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            ) == null
        ) {
            Toast.makeText(this, "Permissions denied forever", Toast.LENGTH_SHORT).show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permission denied")
                .setMessage("Would you like to open app settings if change your mind?")
                .setPositiveButton("Open") { _, _ ->
                    startActivity(appSettingsIntent)
                }
                .create()
                .show()
        }
    }

    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) {
            chooseFromGallery()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                askUserForOpeningAppSettings()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun chooseFromGallery() {
        Crop.pickImage(this)
    }

    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            application, BuildConfig.APPLICATION_ID + ".provider",
            file
        )
    }
}
