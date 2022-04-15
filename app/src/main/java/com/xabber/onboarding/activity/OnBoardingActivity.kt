package com.xabber.onboarding.activity

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.xabber.*
import com.xabber.application.activity.ApplicationActivity
import com.xabber.onboarding.fragments.start.StartFragment
import com.xabber.onboarding.fragments.signin.SigninFragment
import com.xabber.databinding.ActivityOnboardingBinding
import com.xabber.onboarding.contract.Navigator
import com.xabber.onboarding.contract.ResultListener
import com.xabber.onboarding.contract.ToolbarChanger
import com.xabber.onboarding.fragments.signup.*
import com.xabber.util.AppConstants.REQUEST_PERMISSION_CAMERA
import com.xabber.util.AppConstants.REQUEST_PERMISSION_GALLERY
import com.xabber.util.AppConstants.REQUEST_TAKE_PHOTO
import com.xabber.util.AppConstants.TEMP_FILE_NAME
import java.io.File
import java.io.IOException

class OnBoardingActivity : AppCompatActivity(), Navigator, ToolbarChanger {
    private var binding: ActivityOnboardingBinding? = null
    private var filePhotoUri: Uri? = null
    private var newAvatarImageUri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        clearTitle()
        setSupportActionBar(binding?.onboardingToolbar)
        if (savedInstanceState == null) addStartFragment()
       //    startSignupAvatarFragment()

    }


    private fun addStartFragment() {
        supportFragmentManager.beginTransaction().replace(
            R.id.onboarding_container, StartFragment()
        ).commit()

    }

    private fun launchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().setCustomAnimations(R.animator.appearance, R.animator.disappearance).addToBackStack(null)
            .replace(R.id.onboarding_container, fragment).commit()
    }


    override fun startSignupNicknameFragment() {
        launchFragment(SignupNicknameFragment())
    }

    override fun startSignupUserNameFragment() {
        launchFragment(SignupUserNameFragment())
    }

    override fun startSignupPasswordFragment() {
        launchFragment(SignupPasswordFragment.newInstance(UserParams("cat", "hhh")))
    }

    override fun startSignupAvatarFragment() {
        launchFragment(SignupAvatarFragment())
    }

    override fun startSigninFragment() {
        launchFragment(SigninFragment())
    }


    override fun goToApplicationActivity(userName: String) {
        val intent = Intent(this, ApplicationActivity::class.java)
        intent.putExtra("key", userName)
        startActivity(intent)

        finish()
        overridePendingTransition(R.animator.appearance, R.animator.disappearance)
    }

    override fun goBack() {
        onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun <T : Parcelable> showResult(result: T) {

    }

    override fun <T : Parcelable> giveResult(
        clazz: Class<T>,
        owner: LifecycleOwner,
        listener: ResultListener<T>
    ) {

    }

    override fun setTitle(titleResId: Int) {
        binding?.onboardingToolbar?.setTitle(titleResId)
    }

    override fun clearTitle() {
        binding?.onboardingToolbar?.setTitle("")
    }

    override fun setShowBack(isVisible: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(isVisible)
        supportActionBar?.setDisplayShowHomeEnabled(isVisible)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSION_CAMERA -> {
                if (ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.CAMERA
                    )
                    == PackageManager.PERMISSION_GRANTED
                )
                    takePhoto()

            }
            REQUEST_PERMISSION_GALLERY -> {
                if (ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    == PackageManager.PERMISSION_GRANTED
                )
                    chooseFromGallery()
            }
        }
    }

    private fun chooseFromGallery() {
     //   Crop.pickImage(this)
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

    @Throws(IOException::class)
    fun createTempImageFile(name: String): File {
        return File.createTempFile(
            name,
            ".jpg",
            application.getExternalFilesDir(null)
        )
    }

    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            application, BuildConfig.APPLICATION_ID + ".provider",
            file
        )
    }
}