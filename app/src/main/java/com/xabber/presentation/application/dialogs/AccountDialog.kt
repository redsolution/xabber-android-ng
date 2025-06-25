package com.xabber.presentation.application.dialogs

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
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
import java.util.zip.Inflater

@RequiresApi(Build.VERSION_CODES.O)
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
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
                val width = (resources.displayMetrics.widthPixels * 0.8).toInt()
                val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER)
            }
            if (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE) {
                val width = (resources.displayMetrics.widthPixels * 0.48).toInt()
                val height = (resources.displayMetrics.heightPixels * 0.97).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER)
            }
        }
    }

    companion object {
        fun newInstance(jid: String?): AccountDialog {
            val args = Bundle().apply {
                putString(AppConstants.PARAMS_ACCOUNT_DIALOG, jid)
            }
            val dialog = AccountDialog()
            dialog.arguments = args
            return dialog
        }
    }

    private fun getJid(): String {
        val jid = requireArguments().getString(AppConstants.PARAMS_ACCOUNT_DIALOG)
        if (jid.isNullOrEmpty()) {
            Log.e("AccountDialog", "Invalid or missing JID in arguments")
            Toast.makeText(context, "Error: Invalid account ID", Toast.LENGTH_LONG).show()
            dismiss()
            return ""
        }
        return jid
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val jid = getJid()
        if (jid.isEmpty()) return // Dialog dismissed in getJid() if invalid

        setupSwitch()
        setColorDialogResultListener()
        changeUiWithAccountData()
        initToolbarActions()
        createAvatarPopupMenu()
        initAccountSettingsActions()
        binding.accountAppbar.shapeView.setDrawable(MaskManager.mask)
        viewModel.avatarBitmap.observe(viewLifecycleOwner) {
            setAvatar(it)
        }
        viewModel.avatarUri.observe(viewLifecycleOwner) {
            Glide.with(binding.accountAppbar.avatarGr.imAccountAvatar).load(it)
                .into(binding.accountAppbar.avatarGr.imAccountAvatar)
            viewModel.saveAvatar(getJid(), it.toString())
        }
        binding.accountAppbar.accountToolbar.setNavigationOnClickListener { dismiss() }
        sh = requireActivity().getSharedPreferences(AppConstants.SHARED_PREF_MASK, Context.MODE_PRIVATE)
        sh.registerOnSharedPreferenceChangeListener(this)
        subscribeToViewModelData()
    }

    private fun setAvatar(bitmap: Bitmap) {
        Glide.with(requireContext())
            .load(bitmap)
            .skipMemoryCache(true)
            .into(binding.accountAppbar.avatarGr.imAccountAvatar)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        binding.accountAppbar.shapeView.setDrawable(MaskManager.mask)
    }

    private fun setupSwitch() {
        binding.accountAppbar.switchAccountEnable.isVisible = true
        val account = viewModel.getAccount(getJid())
        binding.accountAppbar.switchAccountEnable.isChecked = account?.enabled ?: false
        binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(getJid(), isChecked)
        }
    }

    private fun setColorDialogResultListener() {
        setFragmentResultListener(AppConstants.COLOR_REQUEST_KEY) { _, bundle ->
            val result = bundle.getString(AppConstants.COLOR_BUNDLE_KEY)
            if (result != null) viewModel.setColor(getJid(), result)
        }
    }

    private fun changeUiWithAccountData() {
        val account = viewModel.getAccount(getJid())
        if (account == null) {
            Log.e("AccountDialog", "No account found for JID: ${getJid()}")
            Toast.makeText(context, "Error: Account not found", Toast.LENGTH_LONG).show()
            dismiss()
            return
        }
        hasAvatar = account.hasAvatar
        val colorKey = account.colorKey ?: resources.getString(R.string.blue)
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        loadBackground(colorRes)
        defineColor(colorRes)
        loadAvatar(account)
        with(binding.accountAppbar) {
            tvTitle.text = account.getAccountName() ?: ""
            tvSubtitle.text = account.jid
            switchAccountEnable.isChecked = account.enabled
        }
    }

    private fun loadBackground(colorRes: Int) {
        binding.accountAppbar.appbar.setBackgroundResource(colorRes)
    }

    private fun defineColor(colorRes: Int) {
        binding.accountAppbar.collapsingToolbar.setContentScrimColor(
            ResourcesCompat.getColor(resources, colorRes, requireContext().theme)
        )
    }

    private fun loadAvatar(account: AccountDto) {
        Log.d("AccountDialog", "Loading avatar for account: jid=${account.jid}, hasAvatar=${account.hasAvatar}, nickname=${account.nickname}, colorKey=${account.colorKey}")
        if (account.hasAvatar) {
            loadAccountAvatar()
        } else {
            loadAvatarWithInitials(account.nickname ?: "", account.colorKey ?: resources.getString(R.string.blue))
        }
    }

    private fun loadAccountAvatar() {
        binding.accountAppbar.avatarGr.tvAccountInitials.isVisible = false
        lifecycleScope.launch {
            val account = getPrimaryAccount()
            val avatar = account?.let { getAvatar(it.id) }
            val uri = avatar?.fileUri
            Log.d("AccountDialog", "Loading account avatar: uri=$uri")
            Glide.with(binding.root.context).load(uri)
                .into(binding.accountAppbar.avatarGr.imAccountAvatar)
        }
    }

    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { it.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
            Log.d("AccountDialog", "Primary account: $accountDto")
        } else {
            Log.w("AccountDialog", "No primary account found")
        }
        return accountDto
    }

    private fun getAvatar(id: String): AvatarDto? {
        var avatarDto: AvatarDto? = null
        realm.writeBlocking {
            val realmAvatar =
                this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
            if (realmAvatar != null) {
                avatarDto = realmAvatar.toAvatarDto()
                Log.d("AccountDialog", "Avatar found for id=$id: $avatarDto")
            } else {
                Log.w("AccountDialog", "No avatar found for id=$id")
            }
        }
        return avatarDto
    }

    private fun loadAvatarWithInitials(name: String, colorKey: String) {
        val color = ColorManager.convertColorLightNameToId(colorKey)
        binding.accountAppbar.avatarGr.imAccountAvatar.setImageResource(color)
        var initials = name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.reduceOrNull { acc, s -> acc + s } ?: ""
        if (initials.length > 2) initials = initials.substring(0, 2)
        binding.accountAppbar.avatarGr.tvAccountInitials.isVisible = true
        binding.accountAppbar.avatarGr.tvAccountInitials.text = initials
        Log.d("AccountDialog", "Loaded avatar with initials: name=$name, colorKey=$colorKey, initials=$initials")
    }

    private fun createAvatarPopupMenu() {
        popupMenu = PopupMenu(requireContext(), binding.accountAppbar.avatarGr.imAvatarGroup, Gravity.TOP)
        popupMenu?.inflate(R.menu.popup_menu_account_avatar)
        popupMenu?.menu?.findItem(R.id.delete_avatar)?.isVisible = hasAvatar
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
        binding.accountAppbar.accountToolbar.findViewById<ImageView>(R.id.colors).setOnClickListener {
            val colorKey = viewModel.getAccount(getJid())?.colorKey ?: resources.getString(R.string.blue)
            val dialog = AccountColorDialog.newInstance(colorKey)
            navigator().showDialogFragment(dialog, "")
        }

        binding.accountAppbar.accountToolbar.findViewById<ImageView>(R.id.generate_qr_code).setOnClickListener {
            val account = viewModel.getAccount(getJid())
            val color = account?.colorKey ?: resources.getString(R.string.blue)
            val name = account?.getAccountName() ?: ""
            navigator().showQRCode(QRCodeParams(name, getJid(), color))
        }

        var isShow = true
        var scrollRange = -1
        with(binding.accountAppbar) {
            appbar.addOnOffsetChangedListener { bar, verticalOffset ->
                if (scrollRange == -1) {
                    scrollRange = bar.totalScrollRange
                }
                if (scrollRange + verticalOffset < 20) {
                    val anim = android.view.animation.AnimationUtils.loadAnimation(context, com.xabber.R.anim.disappearance_300)
                    if (tvTitle.isVisible) {
                        tvTitle.startAnimation(anim)
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
                    val nickname = viewModel.getAccount(getJid())?.nickname ?: ""
                    collapsingToolbar.title = nickname
                    isShow = true
                } else if (isShow) {
                    collapsingToolbar.title = " "
                    isShow = false
                }
            }
        }
    }

    private fun subscribeToViewModelData() {
        val jid = getJid()
        viewModel.initDataListener(jid)
        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                Log.e("AccountDialog", "No accounts found for JID: $jid")
                Toast.makeText(context, "Error: No account found for this ID", Toast.LENGTH_LONG).show()
                dismiss()
                return@observe
            }
            val account = accounts[0]
            Log.d("AccountDialog", "Received account for JID: $jid, account: $account")
            if (account.jid.isEmpty() || account.colorKey == null) {
                Log.e("AccountDialog", "Invalid account data: jid=${account.jid}, colorKey=${account.colorKey}")
                Toast.makeText(context, "Error: Invalid account data", Toast.LENGTH_LONG).show()
                dismiss()
                return@observe
            }
            loadAvatar(account)
            if (hasAvatar != account.hasAvatar) {
                loadAvatar(account)
                popupMenu?.menu?.findItem(R.id.delete_avatar)?.isVisible = account.hasAvatar
                hasAvatar = account.hasAvatar
            }
            binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener(null)
            binding.accountAppbar.switchAccountEnable.isChecked = account.enabled
            binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setEnabled(jid, isChecked)
            }
        }

        viewModel.colorKey.observe(viewLifecycleOwner) {
            val colorKey = it ?: resources.getString(R.string.blue)
            val color = ColorManager.convertColorNameToId(colorKey)
            defineColor(color)
            loadBackground(color)
            if (!hasAvatar) {
                val colorLight = ColorManager.convertColorLightNameToId(colorKey)
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
            } else {
                profile.setOnClickListener {
                    childFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(com.xabber.R.id.application_container, ProfileSettingsFragment())
                        .addToBackStack(null)
                        .commit()
                }
                cloudStorage.setOnClickListener {
                    childFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(com.xabber.R.id.application_container, CloudStorageSettingsFragment())
                        .addToBackStack(null)
                        .commit()
                }
                encryptionAndKeys.setOnClickListener {
                    childFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(com.xabber.R.id.application_container, EncryptionSettingsFragment())
                        .addToBackStack(null)
                        .commit()
                }
                devices.setOnClickListener {
                    childFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(com.xabber.R.id.application_container, DevicesSettingsFragment())
                        .addToBackStack(null)
                        .commit()
                }
                settings.interfaceSettings.setOnClickListener {
                    childFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(com.xabber.R.id.application_container, InterfaceFragment())
                        .addToBackStack(null)
                        .commit()
                }
                settings.dataAndStorage.setOnClickListener {
                    childFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(com.xabber.R.id.application_container, CloudStorageSettingsDialog())
                        .addToBackStack(null)
                        .commit()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sh.isInitialized) {
            sh.unregisterOnSharedPreferenceChangeListener(this)
        }
        realm.close()
    }
}