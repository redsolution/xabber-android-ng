package com.xabber.presentation.application.fragments.account

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.xabber.R
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.models.dto.AccountDto
import com.xabber.models.xmpp.account.AccountViewModel
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.AccountManager
import com.xabber.presentation.application.activity.AvatarBottomSheetDialog
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.MaskManager
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.color.AccountColorDialog
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.utils.askUserForOpeningAppSettings
import com.xabber.utils.blur.BlurTransformation
import com.xabber.utils.dp
import com.xabber.utils.setFragmentResult
import com.xabber.utils.setFragmentResultListener
import io.realm.kotlin.internal.RealmInitializer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AccountFragment : DetailBaseFragment(R.layout.fragment_account) {
    private val binding by viewBinding(FragmentAccountBinding::bind)
    private val viewModel: AccountViewModel by activityViewModels()
    private var hasAvatar = false
    private var popupMenu: PopupMenu? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onGotGalleryPermissionResult
    )

    private fun onGotCameraPermissionResult(result: Boolean) {
        if (result) {
            cropImageFromCamera()
        } else askUserForOpeningAppSettings()
    }

    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) {
            cropImageFromGallery()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                askUserForOpeningAppSettings()
            }
        }
    }

    private fun cropImageFromCamera() {
        startCrop(includeCamera = true, includeGallery = false)
    }

    private fun cropImageFromGallery() {
        startCrop(includeCamera = false, includeGallery = true)
    }

    private fun startCrop(includeCamera: Boolean, includeGallery: Boolean) {
        cropImage.launch(options {
            setGuidelines(CropImageView.Guidelines.OFF).setImageSource(
                includeGallery, includeCamera
            ).setOutputCompressFormat(Bitmap.CompressFormat.PNG).setAspectRatio(
                1, 1
            ).setMinCropWindowSize(800, 800).setActivityBackgroundColor(Color.BLACK)
        })
    }
    private val cropImage = registerForActivityResult(CropImageContract()) {
        when {
            it.isSuccessful -> {
                val bitmapi = MediaStore.Images.Media.getBitmap(
                    requireContext().contentResolver,
                    it.uriContent
                )
              binding.accountAppbar.avatarGr.imAccountAvatar.setImageURI(it.uriContent)

                val bitmap =
                    (binding.accountAppbar.avatarGr.imAccountAvatar.drawable as BitmapDrawable).bitmap
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)

                val bytesArray = stream.toByteArray()
                val fileName = "avatar + ${System.currentTimeMillis()}"
                val file = File(RealmInitializer.filesDir, fileName)
                FileOutputStream(file).use {
                    it.write(bytesArray)
                }

                val avatarUri = Uri.fromFile(file)
                viewModel.saveAvatar(getJid(), avatarUri.toString())


            }
            it is CropImage.CancelledResult -> Log.d(
                "Avatar",
                "cropping image was cancelled by the user"
            )
            else -> {
                Log.d("Avatar", "${it.error}")
            }
        }

    }
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
        setupTitle()
        changeUiWithAccountData()
        initToolbarActions()
        createAvatarPopupMenu()
        initAccountSettingsActions()
        setFragmentResultListener("AA") { _, bundle ->
            val uri =
                bundle.getString("AA")
            binding.accountAppbar.avatarGr.imAccountAvatar.setImageURI(uri?.toUri())

            val bitmap =
                (binding.accountAppbar.avatarGr.imAccountAvatar.drawable as BitmapDrawable).bitmap
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)

            val bytesArray = stream.toByteArray()
            val fileName = "avatar + ${System.currentTimeMillis()}"
            val file = File(RealmInitializer.filesDir, fileName)
            FileOutputStream(file).use {
                it.write(bytesArray)
            }

            val avatarUri = Uri.fromFile(file)
            viewModel.saveAvatar(getJid(), avatarUri.toString())
        }

        binding.accountAppbar.switchAccountEnable.isChecked =
            viewModel.getAccount(getJid())!!.enabled
        binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(
                getJid(),
                isChecked
            )
        }

        viewModel.initDataListener(getJid())
        viewModel.accounts.observe(viewLifecycleOwner) {
            if (hasAvatar != it[0].hasAvatar) {
                loadAvatar(it[0])
                popupMenu!!.menu.findItem(R.id.delete_avatar).isVisible = it[0].hasAvatar
                hasAvatar = it[0].hasAvatar
            }
            binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener(null)
            binding.accountAppbar.switchAccountEnable.isChecked = it[0].enabled
            binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setEnabled(
                    getJid(),
                    isChecked
                )
            }
        }

        viewModel.colorKey.observe(viewLifecycleOwner) {
            val color = ColorManager.convertColorNameToId(it)
            defineColor(color)
            loadBackground(color)
            if (!hasAvatar) {
                val colorLight = ColorManager.convertColorLightNameToId(it)
                binding.accountAppbar.avatarGr.imAccountAvatar.setImageResource(colorLight) }
        }

    }

    private fun setupTitle() {
        binding.accountAppbar.tvTitle.isSelected = true
        if (!DisplayManager.isDualScreenMode() && DisplayManager.getWidthDp() > 600) {
            val params = CollapsingToolbarLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CollapsingToolbarLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            params.marginStart = 300.dp
            binding.accountAppbar.linText.layoutParams = params
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
        //  MaskManager.setAvatar(fileNme)
        super.onDestroy()
        val sh = activity?.getSharedPreferences("pref", Context.MODE_PRIVATE)
        //      sh?.edit()?.putString("avatar", MaskManager.getAvatar())?.apply()
        viewModel.setAvatar("", "")
    }

    private fun changeUiWithAccountData() {
        val account = viewModel.getAccount(getJid())
        hasAvatar = account?.hasAvatar ?: false
        val colorKey = account?.colorKey ?: "blue"
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        loadBackground(colorRes)
        defineColor(colorRes)
        if (account != null) loadAvatar(account)
        binding.accountAppbar.switchAccountEnable.isVisible = true

        with(binding.accountAppbar) {
            if (account != null) {
                tvTitle.text = account.nickname
                tvSubtitle.text = account.jid
            }
        }
    }

    private fun loadBackground(colorRes: Int) {
        binding.accountAppbar.appbar.setBackgroundResource(colorRes)
        Glide.with(requireContext())
            .load(AccountManager.getAvatar())
            .transform(
                BlurTransformation(
                    25,
                    6,
                    ContextCompat.getColor(
                        requireContext(),
                        colorRes
                    )
                )
            ).placeholder(colorRes).transition(
                DrawableTransitionOptions.withCrossFade()
            )
            .into(binding.accountAppbar.imBackdrop)
    }

    private fun defineColor(colorRes: Int) {
        binding.accountAppbar.collapsingToolbar.setContentScrimColor(
            ResourcesCompat.getColor(
                resources,
                colorRes,
                requireContext().theme
            )
        )
    }

    private fun loadAvatar(account: AccountDto) {
        if (account.hasAvatar) loadAccountAvatar() else loadAvatarWithInitials(
            account.nickname,
            account.colorKey
        )
    }

    private fun loadAccountAvatar() {
        binding.accountAppbar.avatarGr.tvAccountInitials.isVisible = false
        val avatar = baseViewModel.getAvatar(getJid())
        val uri = avatar?.fileUri
        Log.d("acc", "$uri")
        Glide.with(binding.root.context).load(uri)
            .into(binding.accountAppbar.avatarGr.imAccountAvatar)
    }

    private fun loadAvatarWithInitials(name: String, colorKey: String) {
        val color = ColorManager.convertColorLightNameToId(colorKey)
        binding.accountAppbar.avatarGr.imAccountAvatar.setImageResource(color)
        var initials =
            name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.reduce { acc, s -> acc + s }
        if (initials.length > 2) initials = initials.substring(0, 2)
        binding.accountAppbar.avatarGr.tvAccountInitials.isVisible = true
        binding.accountAppbar.avatarGr.tvAccountInitials.text = initials
    }

    private fun createAvatarPopupMenu() {
        popupMenu =
            PopupMenu(requireContext(), binding.accountAppbar.avatarGr.imAvatarGroup, Gravity.TOP)
        popupMenu!!.inflate(R.menu.popup_menu_account_avatar)
        popupMenu!!.menu.findItem(R.id.delete_avatar).isVisible =
            viewModel.getAccount(getJid())!!.hasAvatar
        popupMenu!!.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.choose_image -> requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                R.id.delete_avatar -> deleteAvatar()
                R.id.take_photo -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            true
        }
        binding.accountAppbar.avatarGr.imAvatarGroup.setOnClickListener { popupMenu!!.show() }
    }

    private fun deleteAvatar() {
        viewModel.deleteAvatar(getJid())
    }

    private fun initToolbarActions() {
        binding.accountAppbar.accountToolbar.addMenuProvider(object : MenuProvider {
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
                        val color = viewModel.getAccount(getJid())?.colorKey ?: "blue"
                        navigator().showQRCode(
                            QRCodeParams(
                                "",
                                getJid(),
                                ColorManager.convertColorNameToId(color)
                            )
                        )
                    }
                 //   R.id.add_account -> navigator().showSettings()
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
                        avatarGr.imAvatarGroup.startAnimation(anim)
                        avatarGr.imAvatarGroup.isVisible = false
                        tvSubtitle.isVisible = false
                        tvTitle.isVisible = false
                    }
                }

                if (scrollRange + verticalOffset > 170) {
                    val anim = AnimationUtils.loadAnimation(context, R.anim.appearance)
                    if (!tvTitle.isVisible) {
                        tvTitle.startAnimation(anim)
                        tvSubtitle.startAnimation(anim)
                        avatarGr.imAvatarGroup.startAnimation(anim)
                        avatarGr.imAvatarGroup.isVisible = true
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


    private fun initAccountSettingsActions() {
        with(binding) {
            profile.setOnClickListener { navigator().showProfileSettings() }
            cloudStorage.setOnClickListener { navigator().showCloudStorageSettings() }
            encryptionAndKeys.setOnClickListener { navigator().showEncryptionAndKeysSettings() }
            devices.setOnClickListener { navigator().showDevicesSettings() }
            settings.interfaceSettings.setOnClickListener { navigator().showInterfaceSettings(true) }
        }
    }

}
