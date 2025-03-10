package com.xabber.presentation.application.dialogs

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.dto.AccountDto
import com.xabber.dto.AvatarDto
import com.xabber.presentation.AppConstants

import com.xabber.presentation.application.CloudStorage.CloudStorageSettingsDialog
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.account.AccountViewModel
import com.xabber.presentation.application.fragments.account.color.AccountColorDialog
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.chat.AvatarChangerBottomSheet
import com.xabber.presentation.application.fragments.settings.CloudStorageSettingsFragment
import com.xabber.presentation.application.fragments.settings.DevicesSettingsFragment
import com.xabber.presentation.application.fragments.settings.EncryptionSettingsFragment
import com.xabber.presentation.application.fragments.settings.InterfaceFragment
import com.xabber.presentation.application.fragments.settings.ProfileSettingsFragment
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.presentation.application.manage.MaskManager
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.setFragmentResultListener
import com.xabber.utils.toAccountDto
import com.xabber.utils.toAvatarDto
import io.realm.kotlin.Realm
import kotlinx.coroutines.launch

class AccountDialog : DialogFragment(R.layout.fragment_account), SharedPreferences.OnSharedPreferenceChangeListener {
    private val binding by viewBinding(FragmentAccountBinding::bind)
    private val viewModel: AccountViewModel by viewModels()
    private var hasAvatar = false
    private var popupMenu: PopupMenu? = null
    private val realm = Realm.open(defaultRealmConfig())
    private var shapeView: ShapeOfView? = null
    private lateinit var sh: SharedPreferences
    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
            val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
            dialog.window?.setLayout(width, height)
            dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
        }
    }
    companion object {
        fun newInstance(jid: String?): AccountDialog {
            val args = Bundle().apply {
                putString(AppConstants.PARAMS_ACCOUNT_DIALOG, jid) // Ensure the key matches
            }
            val dialog = AccountDialog()
            dialog.arguments = args
            return dialog
        }
    }

    private fun getJid(): String =
        requireArguments().getString(AppConstants.PARAMS_ACCOUNT_DIALOG)!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //setupTitle()
        setupSwitch()
        setColorDialogResultListener()
        changeUiWithAccountData()
        initToolbarActions()
        createAvatarPopupMenu()
        initAccountSettingsActions()
        subscribeToViewModelData()
        binding.accountAppbar.shapeView?.setDrawable(MaskManager.mask)
        viewModel.avatarBitmap.observe(viewLifecycleOwner) {
            setAvatar(it)
        }
        viewModel.avatarUri.observe(viewLifecycleOwner) {
            Glide.with(binding.accountAppbar.avatarGr.imAccountAvatar).load(it)
                .into(binding.accountAppbar.avatarGr.imAccountAvatar)
            viewModel.saveAvatar(getJid(), it.toString())
        }
    binding.accountAppbar.left.setOnClickListener {dismiss()}
        sh = activity?.getSharedPreferences(AppConstants.SHARED_PREF_MASK, Context.MODE_PRIVATE)!!
        sh.registerOnSharedPreferenceChangeListener(this)
    }

    private fun setAvatar(bitmap: Bitmap) {
        Glide.with(requireContext())
            .load(bitmap)
            .skipMemoryCache(true)
            .into(binding.accountAppbar.avatarGr.imAccountAvatar)
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        binding.accountAppbar.shapeView?.setDrawable(MaskManager.mask)
    }
    private fun setupTitle() {
//        binding.accountAppbar.tvTitle.isSelected = true
//        if (!DisplayManager.isDualScreenMode() && DisplayManager.getWidthDp() > 600) {
//            val params = CollapsingToolbarLayout.LayoutParams(
//                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
//                CollapsingToolbarLayout.LayoutParams.WRAP_CONTENT
//            )
//            params.gravity = Gravity.CENTER
//
//            binding.accountAppbar.linText.layoutParams = params
//        }
    }

    private fun setupSwitch() {
//        binding.accountAppbar.switchAccountEnable.isVisible = true
//        val colorStateList =
//            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
//        binding.accountAppbar.switchAccountEnable.thumbTintList = colorStateList
//        binding.accountAppbar.switchAccountEnable.isChecked =
//            viewModel.getAccount(getJid())!!.enabled
//        binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
//            viewModel.setEnabled(
//                getJid(),
//                isChecked
//            )
//        }
    }

    private fun setColorDialogResultListener() {
        setFragmentResultListener(AppConstants.COLOR_REQUEST_KEY) { _, bundle ->
            val result = bundle.getString(AppConstants.COLOR_BUNDLE_KEY)
            if (result != null) viewModel.setColor(getJid(), result)
        }
    }

    private fun changeUiWithAccountData() {
        val account = viewModel.getAccount(getJid())
        hasAvatar = account?.hasAvatar ?: false
        val colorKey = account?.colorKey ?: resources.getString(R.string.blue)
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        loadBackground(colorRes)
        defineColor(colorRes)
        if (account != null) loadAvatar(account)
        with(binding.accountAppbar) {
            if (account != null) {
                tvTitle.text = account.getAccountName()
                tvSubtitle.text = account.jid
            }
        }

    }

    private fun loadBackground(colorRes: Int) {
        binding.accountAppbar.appbar.setBackgroundResource(colorRes)
//        Glide.with(requireContext())
//            .load(AccountManager.getAvatar())
//            .transform(
//                BlurTransformation(
//                    25,
//                    6,
//                    ContextCompat.getColor(
//                        requireContext(),
//                        colorRes
//                    )
//                )
//            ).placeholder(colorRes).transition(
//                DrawableTransitionOptions.withCrossFade()
//            )
//            .into(binding.accountAppbar.imBackdrop)
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
        lifecycleScope.launch {
            val account = getPrimaryAccount()
            val avatar = account?.let { getAvatar(it.id) }
            val uri = avatar?.fileUri
            Glide.with(binding.root.context).load(uri)
                .into(binding.accountAppbar.avatarGr.imAccountAvatar)
        }
    }
    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { T -> T.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
        }
        return accountDto
    }

    private fun getAvatar(id: String): AvatarDto? {
        var avatarDto: AvatarDto? = null
        realm.writeBlocking {
            val realmAvatar =
                this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
            if (realmAvatar != null)
                avatarDto = realmAvatar.toAvatarDto()
        }
        return avatarDto
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
        popupMenu?.inflate(R.menu.popup_menu_account_avatar)
        if (popupMenu != null) popupMenu?.menu?.findItem(R.id.delete_avatar)?.isVisible =
            viewModel.getAccount(getJid())!!.hasAvatar
        popupMenu?.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.change_avatar -> showAvatarBottomSheet()
                R.id.delete_avatar -> deleteAvatar()
            }
            true
        }
        binding.accountAppbar.avatarGr.imAvatarGroup.setOnClickListener { popupMenu!!.show() }
    }

    private fun showAvatarBottomSheet() {
        val dialog = AvatarChangerBottomSheet.newInstance(getJid())
        dialog.show(childFragmentManager, AppConstants.AVATAR_BOTTOM_SHEET_TAG)
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
                        val dialog = AccountColorDialog.newInstance(
                            viewModel.getAccount(getJid())?.colorKey
                                ?: resources.getString(R.string.blue)
                        )
                        navigator().showDialogFragment(dialog, "")
                    }

                    R.id.generate_qr_code -> {
                        val color = viewModel.getAccount(getJid())?.colorKey ?: resources.getString(
                            R.string.blue
                        )
                        val name = viewModel.getAccount(getJid())?.getAccountName() ?: ""
                        navigator().showQRCode(
                            QRCodeParams(
                                name,
                                getJid(),
                                color
                            )
                        )
                    }
                }
                return true
            }
        })

        var isShow = true
        var scrollRange = -1
        with(binding.accountAppbar) {
            appbar.addOnOffsetChangedListener { bar, verticalOffset ->
                if (scrollRange == -1) {
                    scrollRange = bar.totalScrollRange
                }
                if (scrollRange + verticalOffset < 20) {
                    val anim =
                        android.view.animation.AnimationUtils.loadAnimation(context, com.xabber.R.anim.disappearance_300)
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

                if (scrollRange + verticalOffset > 20) {
                    val anim = android.view.animation.AnimationUtils.loadAnimation(context, com.xabber.R.anim.appearance)
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
            }
        }
    }

    private fun subscribeToViewModelData() {
        viewModel.initDataListener(getJid())
        viewModel.accounts.observe(viewLifecycleOwner) {
            loadAvatar(it[0])
            if (hasAvatar != it[0].hasAvatar) {
                loadAvatar(it[0])
                popupMenu?.menu?.findItem(R.id.delete_avatar)?.isVisible = it[0].hasAvatar
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
                binding.accountAppbar.avatarGr.imAccountAvatar.setImageResource(colorLight)
            }
        }
    }


    private fun initAccountSettingsActions() {
        with(binding) {
            if (DisplayManager.getWidthDp() > 600) {
                profile.setOnClickListener {
                    val profileSettings = ProfileSettingsDialog()
                    profileSettings.show(childFragmentManager, "settings")
                }
                cloudStorage.setOnClickListener {
                    val cloudStorage = CloudStorageSettingsDialog()
                    cloudStorage.show(childFragmentManager, "Cloud Storage")
                }
                encryptionAndKeys.setOnClickListener {
                    val encryptionSettings = EncryptionSettingsDialog()
                    encryptionSettings.show(childFragmentManager, "Encryption and Keys")
                }
                devices.setOnClickListener {
                    val devicesSettings = DevicesSettingsDialog()
                    devicesSettings.show(childFragmentManager, "Devices")
                }
                settings.interfaceSettings.setOnClickListener {
                    val interfaceD = InterfaceDialog()
                    interfaceD.show(childFragmentManager, "Interface")
                    }
                settings.notifications.setOnClickListener {
                    val notifyButton = NotificationsFragment()
                    notifyButton.show(childFragmentManager, "Notifications")
                }
                settings.dataAndStorage.setOnClickListener {
                    val storage = CloudStorageSettingsDialog()
                    storage.show(childFragmentManager, "data and storage")
                }
//                settings.interfaceSettings.setOnClickListener {
//                    val interfaceD = InterfaceDialog()
//                    interfaceD.show(childFragmentManager, "confidential")
//                }
//                settings.interfaceSettings.setOnClickListener {
//                    val interfaceD = InterfaceDialog()
//                    interfaceD.show(childFragmentManager, "connection")
//                }
//                settings.interfaceSettings.setOnClickListener {
//                    val interfaceD = InterfaceDialog()
//                    interfaceD.show(childFragmentManager, "debug")
//                }
//                settings.interfaceSettings.setOnClickListener {
//                    val interfaceD = InterfaceDialog()
//                    interfaceD.show(childFragmentManager, "language")
//                }

            } else {
                profile.setOnClickListener {
                childFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(com.xabber.R.id.application_container, ProfileSettingsFragment())
                    .addToBackStack(null) // Add to back stack for back navigation
                    .commit()
                }
                cloudStorage.setOnClickListener {
                    childFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(com.xabber.R.id.application_container, CloudStorageSettingsFragment())
                    .addToBackStack(null) // Add to back stack for back navigation
                    .commit()
                }
                encryptionAndKeys.setOnClickListener {
                    childFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(com.xabber.R.id.application_container, EncryptionSettingsFragment())
                    .addToBackStack(null) // Add to back stack for back navigation
                    .commit()
                }
                devices.setOnClickListener {
                    childFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(com.xabber.R.id.application_container, DevicesSettingsFragment())
                    .addToBackStack(null) // Add to back stack for back navigation
                    .commit()
                }
                settings.interfaceSettings.setOnClickListener {
                    childFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(com.xabber.R.id.application_container, InterfaceFragment())
                    .addToBackStack(null) // Add to back stack for back navigation
                    .commit()
                }
                settings.dataAndStorage.setOnClickListener {
                    childFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(com.xabber.R.id.application_container, CloudStorageSettingsDialog())
                        .addToBackStack(null) // Add to back stack for back navigation
                        .commit()
                }
            }

            }

//                cloudStorage.setOnClickListener { navigator().showCloudStorageSettings() }
//                encryptionAndKeys.setOnClickListener { navigator().showEncryptionAndKeysSettings() }
//                devices.setOnClickListener { navigator().showDevicesSettings() }
//                settings.interfaceSettings.setOnClickListener { navigator().showInterfaceSettings(true) }



        }
    override fun onDestroy() {
        super.onDestroy()
        if (::sh.isInitialized) {
            sh.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    }

