package com.xabber.presentation.application.fragments.account

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.xabber.R
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.models.xmpp.account.AccountViewModel
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.AvatarBottomSheetDialog
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.color.AccountColorDialog
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.chat.FileManager
import com.xabber.presentation.onboarding.fragments.signup.AvatarBottomSheet
import com.xabber.utils.askUserForOpeningAppSettings
import com.xabber.utils.blur.BlurTransformation
import com.xabber.utils.dp
import com.xabber.utils.isPermissionGranted
import com.xabber.utils.mask.Mask
import com.xabber.utils.mask.MaskPrepare
import com.xabber.utils.mask.MaskedDrawableBitmapShader
import io.realm.kotlin.internal.platform.RealmInitializer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AccountFragment : DetailBaseFragment(R.layout.fragment_account) {
    private val binding by viewBinding(FragmentAccountBinding::bind)
    private val viewModel: AccountViewModel by activityViewModels()
    private var currentPhotoUri: Uri? = null
private var f = R.color.blue_500
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
        fun newInstance(jid: String): AccountFragment {
            val args =
                Bundle().apply { putString(AppConstants.PARAMS_ACCOUNT_FRAGMENT, jid) }
            val fragment = AccountFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getJid(): String =
        requireArguments().getString(AppConstants.PARAMS_ACCOUNT_FRAGMENT)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.accountAppbar.tvTitle.isSelected = true

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
        initAccountSettingsActions()
        Log.d(
            "params",
            "DisplayManager.getWidthDp() = ${binding.root.width}, text = ${binding.accountAppbar.tvTitle.width}"
        )
        if (!DisplayManager.isDualScreenMode() && DisplayManager.getWidthDp() > 600) {
            val params = CollapsingToolbarLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CollapsingToolbarLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            params.marginStart = 300.dp
            binding.accountAppbar.linText.layoutParams = params
        }
        binding.accountAppbar.switchAccountEnable.isChecked =
            viewModel.getAccount(getJid())!!.enabled
        binding.accountAppbar.switchAccountEnable.setOnClickListener { viewModel.setEnabled(getJid()) }

        viewModel.initDataListener()
        viewModel.accounts.observe(viewLifecycleOwner) {
            binding.accountAppbar.switchAccountEnable.isChecked = it[0].enabled
            loadBackground()
            defineColor()
        }

        viewModel.avatarBitmap.observe(viewLifecycleOwner) {
            val multiTransformation = MultiTransformation(CircleCrop())
            Glide.with(requireContext()).load(it).error(R.drawable.ic_avatar_placeholder)
                .apply(RequestOptions.bitmapTransform(multiTransformation))
                .into(binding.accountAppbar.imPhoto)
            saveAvatar(it)
        }
    }

    private fun saveAvatar(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val bytesArray = stream.toByteArray()
        val name = "avatar + ${System.currentTimeMillis()}"
        val file = File(RealmInitializer.filesDir, name)
        FileOutputStream(file).use {
            val bytes = bytesArray
            it.write(bytes)
        }
        val fileNme = file.path
        Log.d("bbb", "name = $fileNme")
        UiChanger.setAvatar(fileNme)
        super.onDestroy()
        val sh =   activity?.getSharedPreferences("pref", Context.MODE_PRIVATE)
        sh?.edit()?.putString("avatar", UiChanger.getAvatar())?.apply()
        viewModel.setAvatar("", "")
    }

    @SuppressLint("ResourceAsColor")
    private fun changeUiWithAccountData() {
        loadBackground()
        defineColor()
        loadAvatar()
        binding.accountAppbar.switchAccountEnable.isVisible = true
        val account = viewModel.getAccount(getJid())
        with(binding.accountAppbar) {
            if (account != null) {
                tvTitle.text = account.nickname
                tvSubtitle.text = account.jid
            }
        }
    }

    private fun loadBackground() {
        val color = viewModel.getAccount(getJid())?.colorKey
        val fon = if (color != null) ColorManager.convertColorNameToId(color) else R.color.blue_500
        f = fon
        binding.accountAppbar.appbar.setBackgroundResource(fon)
        Glide.with(requireContext())
            .load(R.drawable.img)
            .transform(
                BlurTransformation(
                    25,
                    6,
                    ContextCompat.getColor(
                        requireContext(),
                        fon
                    )
                )
            ).placeholder(fon).transition(
                DrawableTransitionOptions.withCrossFade()
            )
            .into(binding.accountAppbar.imBackdrop)
    }

    private fun loadAvatar() {

        val avatar = UiChanger.getAvatar()
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(requireContext()).load(avatar).error(R.drawable.ic_avatar_placeholder)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.accountAppbar.imPhoto)
    }

    private fun createAvatarPopupMenu() {
        val popup = PopupMenu(requireContext(), binding.accountAppbar.imPhoto, Gravity.TOP)
        popup.inflate(R.menu.popup_menu_account_avatar)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.choose_image -> AvatarBottomSheetDialog().show(parentFragmentManager, "")
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
        //    viewModel.setAvatar(getJid(),null)
        val mPictureBitmap =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_empty_photo)?.toBitmap()

        Log.d("bbb", "$mPictureBitmap")
        val mMaskBitmap =
            BitmapFactory.decodeResource(
                binding.root.context.resources,
                UiChanger.getMask().size176
            )
                .extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)

        Glide.with(requireContext())
            .load(maskedDrawable)
            .into(binding.accountAppbar.imPhoto)
        // val multiTransformation = MultiTransformation(CircleCrop())
//        Glide.with(requireContext()).load(R.drawable.ic_empty_photo).error(R.drawable.ic_avatar_placeholder)
//            .apply(RequestOptions.bitmapTransform(multiTransformation))
//            .into(binding.accountAppbar.imPhoto)
        val bitmap =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_empty_photo)?.toBitmap()
        val stream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val bytesArray = stream.toByteArray()
        val name = "avatar + ${System.currentTimeMillis()}"
        val file = File(RealmInitializer.filesDir, name)
        FileOutputStream(file).use {
            val bytes = bytesArray
            it.write(bytes)
        }
        val fileNme = file.path
        Log.d("bbb", "name = $fileNme")
        UiChanger.setAvatar(fileNme)
        val sh =   activity?.getSharedPreferences("pref", Context.MODE_PRIVATE)
        sh?.edit()?.putString("avatar", UiChanger.getAvatar())?.apply()
    }

    private fun initToolbarActions() {
        binding.accountAppbar.toolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_toolbar_account, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.colors -> {
                        val dialog = AccountColorDialog()
                        navigator().showDialogFragment(dialog, "")
                    }
                    R.id.generate_qr_code -> {
                        navigator().showQRCode(
                            QRCodeParams(
                                "",
                                getJid(),
                                f
                            )
                        )
                    }
                    R.id.add_account -> navigator().showSettings()
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
                        AnimationUtils.loadAnimation(context, R.anim.disappearance_300)
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
                    collapsingToolbar.title = viewModel.getAccount(getJid())!!.nickname
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
                f,
                requireContext().theme
            )
        )
    }

    private fun takePhotoFromCamera() {
//        val image: File? = FileManager.generatePicturePath()
//        if (image != null) {
//            currentPhotoUri = getFileUri(image, requireContext())
//            cameraResultLauncher.launch(currentPhotoUri)
//        }
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
            val maskedDrawable =
                MaskedDrawableBitmapShader()
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
                            8,
                            ResourcesCompat.getColor(
                                resources,
                                R.color.blue_500,
                                view?.context?.theme
                            )
                        )
                    )
                )
                .into(binding.accountAppbar.imBackdrop as ImageView)
        }
    }


    private fun onTakePhotoFromCamera(result: Boolean) {
        if (result) {
            if (currentPhotoUri != null) {

            }
        }
    }


    private fun initAccountSettingsActions() {
        with(binding) {
            profile.setOnClickListener { navigator().showProfileSettings() }
            cloudStorage.setOnClickListener { navigator().showCloudStorageSettings() }
            encryptionAndKeys.setOnClickListener { navigator().showEncryptionAndKeysSettings() }
            devices.setOnClickListener { navigator().showDevicesSettings() }
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
