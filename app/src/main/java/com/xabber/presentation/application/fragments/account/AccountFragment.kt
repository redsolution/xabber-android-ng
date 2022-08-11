package com.xabber.presentation.application.fragments.account

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.xabber.R
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.FileManager
import com.xabber.presentation.application.fragments.chat.FileManager.Companion.getFileUri
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.BlurTransformation
import com.xabber.presentation.application.util.askUserForOpeningAppSettings
import com.xabber.presentation.application.util.isPermissionGranted
import java.io.File

class AccountFragment : DetailBaseFragment(R.layout.fragment_account) {
    private val binding by viewBinding(FragmentAccountBinding::bind)
    private var currentPhotoUri: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotGalleryPermissionResult
    )

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(), ::onTakePhotoFromCamera
    )

    private val galleryResultLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
        ::onTakePictureFromGallery
    )

    companion object {
        fun newInstance(account: Account): AccountFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.PARAMS_ACCOUNT_FRAGMENT, account) }
            val fragment = AccountFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getAccount(): Account =
        requireArguments().getParcelable(AppConstants.PARAMS_ACCOUNT_FRAGMENT)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var actionBarHeight = 0
        val tv = TypedValue()
        if (requireActivity().theme.resolveAttribute(
                android.R.attr.actionBarSize,
                tv,
                true
            )
        ) actionBarHeight =
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        val params = CollapsingToolbarLayout.LayoutParams(
            CollapsingToolbarLayout.LayoutParams.MATCH_PARENT,
            actionBarHeight
        )
        params.topMargin = DisplayManager.getHeightStatusBar()
        params.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
        binding.accountAppbar.toolbar.layoutParams = params
        changeUiWithAccountData()
        initToolbarActions()
        createAvatarPopupMenu()
    }

    private fun changeUiWithAccountData() {
        loadBackground()
        loadAvatar()
        defineColor()
        with(binding.accountAppbar) {
            tvTitle.text = getAccount().name
            tvSubtitle.text = getAccount().jid
            collapsingToolbar.setBackgroundColor(getAccount().colorResId)
        }
    }

    private fun loadBackground() {
        Glide.with(requireContext())
            .load(getAccount().avatar)
            .transform(
                MultiTransformation(
                    CenterCrop(),
                    BlurTransformation(
                        25,
                        4,
                        ResourcesCompat.getColor(
                            resources,
                            getAccount().colorResId,
                            view?.context?.theme
                        )
                    )
                )
            )
            .into(binding.accountAppbar.imBackdrop)
    }

    private fun loadAvatar() {
        val mPictureBitmap = BitmapFactory.decodeResource(resources, getAccount().avatar)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, UiChanger.getMask().size176).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        binding.accountAppbar.imPhoto.setImageDrawable(maskedDrawable)
    }

    private fun createAvatarPopupMenu() {
        val popup = PopupMenu(requireContext(), binding.accountAppbar.imPhoto, Gravity.TOP)
        popup.inflate(R.menu.popup_menu_account_avatar)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.choose_from_gallery -> choosePhotoFromGallery()
                R.id.take_photo -> takePhoto()
                R.id.delete_avatar -> deleteAvatar()
            }
            true
        }
        binding.accountAppbar.imPhoto.setOnClickListener { popup.show() }
    }

    private fun choosePhotoFromGallery() {
        if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) galleryResultLauncher.launch(
            "image/*"
        )
        else requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun takePhoto() {
        if (isPermissionGranted(Manifest.permission.CAMERA) && isPermissionGranted(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) takePhotoFromCamera()
        else requestCameraPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    private fun deleteAvatar() {

    }

    private fun initToolbarActions() {
        binding.accountAppbar.toolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_toolbar_account, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.menu_item_color -> {
                        val dialog = AccountColorDialog()
                        navigator().showDialogFragment(dialog)
                    }
                    R.id.menu_item_generate_qr_code -> {
                        navigator().showQRCode(
                            QRCodeParams(
                                "",
                                getAccount().jid!!,
                                getAccount().colorResId
                            )
                        )
                    }
                    R.id.menu_item_add_account -> navigator().showSettings()
                }
                return true
            }
        })

        var isShow = true
        var scrollRange = -1
        with(binding.accountAppbar) {

            appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { bar, verticalOffset ->

                if (scrollRange == -1) {
                    scrollRange = bar.totalScrollRange
                }
                if (scrollRange + verticalOffset < 170) {
                    val anim =
                        AnimationUtils.loadAnimation(context, R.animator.disappearance_long)
                    if (tvTitle.isVisible) {
                        tvTitle.startAnimation(
                            anim
                        )
                        tvSubtitle.startAnimation(anim)
                        imPhoto.startAnimation(anim)
                        imPhoto.isVisible = false
                        tvSubtitle.isVisible = false
                        tvTitle.isVisible = false
                    }
                }

                if (scrollRange + verticalOffset > 170) {
                    val anim = AnimationUtils.loadAnimation(context, R.anim.appearance)
                    if (!tvTitle.isVisible) {
                        tvTitle.startAnimation(anim)
                        tvSubtitle.startAnimation(anim)
                        imPhoto.startAnimation(anim)
                        imPhoto.isVisible = true
                        tvSubtitle.isVisible = true
                        tvTitle.isVisible = true
                    }
                }
                if (scrollRange + verticalOffset == 0) {
                    collapsingToolbar.title = getAccount().name
                    isShow = true
                } else if (isShow) {
                    collapsingToolbar.title =
                        " "
                    isShow = false
                }
            })
        }
    }

    private fun defineColor() {
        binding.accountAppbar.collapsingToolbar.setContentScrimColor(
            ResourcesCompat.getColor(
                resources,
                getAccount().colorResId,
                requireContext().theme
            )
        )
    }

    private fun takePhotoFromCamera() {
        val image: File? = FileManager.generatePicturePath()
        if (image != null) {
            currentPhotoUri = getFileUri(image, requireContext())
            cameraResultLauncher.launch(currentPhotoUri)
        }
    }

    private fun onGotCameraPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            takePhotoFromCamera()
        } else askUserForOpeningAppSettings()
    }

    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) {
            chooseImageFromGallery()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                askUserForOpeningAppSettings()
            }
        }
    }

    private fun chooseImageFromGallery() {
        galleryResultLauncher.launch("image/*")
    }

    private fun onTakePictureFromGallery(result: Uri?) {
        if (result != null) {
            Log.d("ooo", "${result.path}")
            val mPictureBitmap = BitmapFactory.decodeFile(result.path)

            val mMaskBitmap =
                BitmapFactory.decodeResource(resources, UiChanger.getMask().size176).extractAlpha()
            val maskedDrawable = MaskedDrawableBitmapShader()
            maskedDrawable.setPictureBitmap(mPictureBitmap)
            maskedDrawable.setMaskBitmap(mMaskBitmap)
            Glide.with(binding.accountAppbar.imPhoto).load(maskedDrawable)
                .into(binding.accountAppbar.imPhoto)
            binding.accountAppbar.imPhoto.setImageDrawable(maskedDrawable)
            Glide.with(requireContext())
                .load(result)
                .transform(
                    MultiTransformation(
                        CenterCrop(),
                        BlurTransformation(
                            25,
                            4,
                            ResourcesCompat.getColor(
                                resources,
                                getAccount().colorResId,
                                view?.context?.theme
                            )
                        )
                    )
                )
                .into(binding.accountAppbar.imBackdrop)
        }
    }


    private fun onTakePhotoFromCamera(result: Boolean) {
        if (result) {
            if (currentPhotoUri != null) {

            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        requestCameraPermissionLauncher.unregister()
        requestGalleryPermissionLauncher.unregister()
        cameraResultLauncher.unregister()
        galleryResultLauncher.unregister()
    }
}