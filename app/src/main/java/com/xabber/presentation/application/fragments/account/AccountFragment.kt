package com.xabber.presentation.application.fragments.account

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.PopupMenu
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.xabber.R
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.dto.AccountDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.color.AccountColorDialog
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.chat.AvatarChangerBottomSheet
import com.xabber.presentation.application.manage.AccountManager
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.blur.BlurTransformation
import com.xabber.utils.dp
import com.xabber.utils.setFragmentResultListener
import kotlinx.coroutines.launch

class AccountFragment : DetailBaseFragment(R.layout.fragment_account) {
    private val binding by viewBinding(FragmentAccountBinding::bind)
    private val viewModel: AccountViewModel by viewModels()
    private var hasAvatar = false
    private var popupMenu: PopupMenu? = null

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
        setupSwitch()
        setColorDialogResultListener()
        changeUiWithAccountData()
        initToolbarActions()
        createAvatarPopupMenu()
        initAccountSettingsActions()
        subscribeToViewModelData()

        viewModel.avatarBitmap.observe(viewLifecycleOwner) {
            setAvatar(it)
        }
        viewModel.avatarUri.observe(viewLifecycleOwner) {
            Glide.with(binding.accountAppbar.avatarGr.imAccountAvatar).load(it)
                .into(binding.accountAppbar.avatarGr.imAccountAvatar)
            viewModel.saveAvatar(getJid(), it.toString())
        }
    }

    private fun setAvatar(bitmap: Bitmap) {
        Glide.with(requireContext())
            .load(bitmap)
            .skipMemoryCache(true)
            .into(binding.accountAppbar.avatarGr.imAccountAvatar)
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

    private fun setupSwitch() {
        binding.accountAppbar.switchAccountEnable.isVisible = true
        val colorStateList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        binding.accountAppbar.switchAccountEnable.thumbTintList = colorStateList
        binding.accountAppbar.switchAccountEnable.isChecked =
            viewModel.getAccount(getJid())!!.enabled
        binding.accountAppbar.switchAccountEnable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(
                getJid(),
                isChecked
            )
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
        lifecycleScope.launch {
            val avatar = baseViewModel.getAvatar(getJid())
            val uri = avatar?.fileUri
            Glide.with(binding.root.context).load(uri)
                .into(binding.accountAppbar.avatarGr.imAccountAvatar)
        }
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
                            viewModel.getAccount(getJid())?.colorKey ?: resources.getString(R.string.blue)
                        )
                        navigator().showDialogFragment(dialog, "")
                    }
                    R.id.generate_qr_code -> {
                        val color = viewModel.getAccount(getJid())?.colorKey ?: resources.getString(R.string.blue)
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
            profile.setOnClickListener { navigator().showProfileSettings() }
            cloudStorage.setOnClickListener { navigator().showCloudStorageSettings() }
            encryptionAndKeys.setOnClickListener { navigator().showEncryptionAndKeysSettings() }
            devices.setOnClickListener { navigator().showDevicesSettings() }
            settings.interfaceSettings.setOnClickListener { navigator().showInterfaceSettings(true) }
        }
    }

}
