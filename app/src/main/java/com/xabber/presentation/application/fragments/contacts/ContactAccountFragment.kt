package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.animation.AnimationUtils
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.tabs.TabLayout
import com.xabber.R
import com.xabber.databinding.FragmentContactAccountBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.dialogs.NotificationBottomSheet
import com.xabber.presentation.application.dialogs.TimeMute
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.contacts.*
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.dp
import com.xabber.utils.parcelable
import com.xabber.utils.setFragmentResultListener
import com.xabber.utils.showToast

class ContactAccountFragment : DetailBaseFragment(R.layout.fragment_contact_account) {
    private val binding by viewBinding(FragmentContactAccountBinding::bind)
    private var mediaAdapter: MediaAdapter? = null
    private val viewModel: ContactAccountViewModel by viewModels()
    private var chatId = ""

    companion object {
        fun newInstance(params: ContactAccountParams): ContactAccountFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT, params) }
            val fragment = ContactAccountFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getParams(): ContactAccountParams =
        requireArguments().parcelable(AppConstants.PARAMS_CONTACT_ACCOUNT)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.accountAppbar.avatarGr.imAccountAvatar.setImageResource(getParams().avatar!!)
        binding.accountAppbar.tvTitle.isSelected = true
        setFragmentResultListener(AppConstants.DELETING_CONTACT_DIALOG_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.DELETING_CONTACT_BUNDLE_KEY)
            val clearHistory =
                bundle.getBoolean(AppConstants.DELETING_CONTACT_AND_CLEAR_HISTORY)
            if (result) {
                viewModel.deleteContact(getParams().id, clearHistory)
            }
        }
        if (savedInstanceState != null) {

            val tab = viewModel.tab
            val t = binding.tabsAttachment.getTabAt(tab)
            t?.select()
            val list = ArrayList<String>()
            repeat(30) { list.add("") }

            when (tab) {
                0 -> {
                    binding.rvAttachments.layoutManager = GridLayoutManager(context, 3)
                    mediaAdapter = MediaAdapter()
                    binding.rvAttachments.adapter = mediaAdapter
                    mediaAdapter?.updateAdapter(list)
                    viewModel.tab = 0
                }
                1 -> {
                    binding.rvAttachments.layoutManager = LinearLayoutManager(requireContext())
                    val adapter = VideoAdapter()
                    binding.rvAttachments.adapter = adapter
                    adapter.updateAdapter(list)
                    viewModel.tab = 1
                }
                2 -> {
                    binding.rvAttachments.layoutManager = LinearLayoutManager(requireContext())
                    val adapter = FAdapter()
                    binding.rvAttachments.adapter = adapter
                    adapter.updateAdapter(list)

                    viewModel.tab = 2
                }
                3 -> {
                    binding.rvAttachments.layoutManager = LinearLayoutManager(requireContext())
                    val adapter = VoiceAdapter()
                    binding.rvAttachments.adapter = adapter
                    adapter.updateAdapter(list)
                    viewModel.tab = 3
                }
            }
        } else {
            binding.rvAttachments.layoutManager = GridLayoutManager(context, 3)
            mediaAdapter = MediaAdapter()
            binding.rvAttachments.adapter = mediaAdapter
            val list = ArrayList<String>()
            repeat(30) { list.add("") }
            mediaAdapter?.updateAdapter(list)
        }
        val jid = viewModel.getJid(getParams().id)
        val chat = viewModel.getChat(jid)
        if (chat != null) chatId = chat.id
        if (chat != null) {
            setMuteIcon(chat.muteExpired)
        }
        viewModel.initChatDataListener(chat!!.id)
        viewModel.initContactDataListener(getParams().id)
        initToolbarActions()
        setToolbarPadding()
        changeUiWidthData()
        initPanelActions()
        initTabLayout()
        if (!DisplayManager.isDualScreenMode() && DisplayManager.getWidthDp() > 600) {
            val params = CollapsingToolbarLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CollapsingToolbarLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            params.marginStart = 300.dp
            binding.accountAppbar.linText.layoutParams = params
        }
        subscribeToViewModelData()
        binding.tvJid.isSelected = true

    }

    private fun setToolbarPadding() {
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
        binding.accountAppbar.accountToolbar.layoutParams = params
    }

    @SuppressLint("ResourceAsColor")
    private fun changeUiWidthData() {
        loadAvatar()
        loadBackground()
        defineColor()
        val contact = viewModel.getContact(getParams().id)
        with(binding.accountAppbar) {
            tvTitle.text = contact.customNickName ?: contact.nickName
            tvSubtitle.text = contact.jid
        }
        binding.tvJid.text = contact.jid
        val name = contact.nickName?.split(" ")
        if (name!!.isNotEmpty()) {
            binding.tvFullName.text = name[0]
            if (name.size >= 2)
                binding.tvSurname.text = name[1]
        }
        binding.clName.isVisible = binding.tvFullName.text.isNotEmpty()
        binding.clSurname.isVisible = binding.tvSurname.text.isNotEmpty()
        setupMenu(contact.isDeleted)
    }

    private fun loadBackground() {
        val colorKey = viewModel.getContact(getParams().id).color
        val color = ColorManager.convertColorNameToId(colorKey)
        val avatar: Int =
            if (getParams().avatar != null) getParams().avatar!! else R.drawable.ic_photo_white
        Glide.with(requireContext())
            .load(avatar)
            .transform(
                com.xabber.utils.blur.BlurTransformation(
                    25,
                    6,
                    ContextCompat.getColor(
                        requireContext(),
                        color
                    )
                )
            ).placeholder(color).transition(
                DrawableTransitionOptions.withCrossFade()
            )
            .into(binding.accountAppbar.imBackdrop)
    }

    private fun loadAvatar() {
// здесь нужно будет скчать автар с сервера
    }

    private fun defineColor() {
        val colorKey = viewModel.getContact(getParams().id).color
        val color = ColorManager.convertColorNameToId(colorKey)
        binding.accountAppbar.collapsingToolbar.setContentScrimColor(
            ResourcesCompat.getColor(
                resources,
                color,
                requireContext().theme
            )
        )
    }

    private fun shareContact(name: String) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, name)
        startActivity(Intent.createChooser(shareIntent, name))
    }

    private fun initToolbarActions() {

        binding.accountAppbar.accountToolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_toolbar_contact_account, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val colorKey = viewModel.getContact(getParams().id).color
                when (menuItem.itemId) {
                    R.id.contact_qr_code -> {
                        navigator().showQRCode(
                            QRCodeParams(
                                binding.accountAppbar.tvTitle.text.toString(),
                                binding.accountAppbar.tvSubtitle.text.toString(),
                                colorKey
                            )
                        )
                    }
                    R.id.edit_contact -> {
                        navigator().showEditContact(
                            ContactAccountParams(
                                getParams().id,
                                getParams().avatar
                            )
                        )
                    }
                    R.id.delete_contact -> {
                        val dialog =
                            DeletingContactDialog.newInstance(
                                binding.accountAppbar.tvTitle.text.toString(),
                                getParams().id
                            )
                        navigator().showDialogFragment(dialog, "")


                    }
                    R.id.send_contact -> {
                        shareContact("${binding.accountAppbar.tvTitle.text} \n ${binding.accountAppbar.tvSubtitle.text}")
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
                    collapsingToolbar.title = binding.accountAppbar.tvTitle.text
                    isShow = true
                } else if (isShow) {
                    collapsingToolbar.title =
                        " "
                    isShow = false
                }
            }
        }
    }

    private fun blockContact() {
        val name = binding.accountAppbar.tvTitle.text.toString()
        val dialog = BlockContactDialog.newInstance(name, getParams().id)
        navigator().showDialogFragment(dialog, AppConstants.DIALOG_BLOCK_CONTACT_TAG)
        setFragmentResultListener(AppConstants.BLOCK_CONTACT) { _, bundle ->
            val blocked =
                bundle.getBoolean(AppConstants.BLOCK_CONTACT_BUNDLE_KEY)
            if (blocked) viewModel.blockContact(chatId)
        }
    }

    private fun initPanelActions() {
        binding.rlOpenChat.setOnClickListener {
            navigator().showChatInStack(
                ChatParams(
                    chatId,
                    getParams().avatar
                )
            )
        }

        binding.rlCall.setOnClickListener { showToast(R.string.feature_not_implemented) }

        binding.rlNotifications.setOnClickListener {
            if (viewModel.getChat(viewModel.getJid(getParams().id))!!.muteExpired <= 0) {
                NotificationBottomSheet.newInstance(getParams().id)
                    .show(childFragmentManager, AppConstants.NOTIFICATION_BOTTOM_SHEET_TAG)
            } else {
                viewModel.setMute(chatId, 0)
            }
        }

        binding.rlBlock.setOnClickListener { blockContact() }

        binding.tvViewFullProfile.setOnClickListener {
            navigator().showContactProfile(
                ContactAccountParams(
                    getParams().id,
                    getParams().avatar
                )
            )
        }
    }

    private fun initTabLayout() {
        val list = ArrayList<String>()
        repeat(30) { list.add("") }

        binding.tabsAttachment.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.rvAttachments.layoutManager = GridLayoutManager(context, 3)
                        mediaAdapter = MediaAdapter()
                        binding.rvAttachments.adapter = mediaAdapter
                        mediaAdapter?.updateAdapter(list)
                        viewModel.tab = 0
                    }
                    1 -> {
                        binding.rvAttachments.layoutManager = LinearLayoutManager(requireContext())
                        val adapter = VideoAdapter()
                        binding.rvAttachments.adapter = adapter
                        adapter.updateAdapter(list)
                        viewModel.tab = 1
                    }
                    2 -> {
                        binding.rvAttachments.layoutManager = LinearLayoutManager(requireContext())
                        val adapter = FAdapter()
                        binding.rvAttachments.adapter = adapter
                        adapter.updateAdapter(list)
                        viewModel.tab = 2
                    }
                    3 -> {
                        binding.rvAttachments.layoutManager = LinearLayoutManager(requireContext())
                        val adapter = VoiceAdapter()
                        binding.rvAttachments.adapter = adapter
                        adapter.updateAdapter(list)
                        viewModel.tab = 3
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

    }

    private fun subscribeToViewModelData() {
        viewModel.muteExpired.observe(viewLifecycleOwner) {
            setMuteIcon(it)
        }
        viewModel.isDeleted.observe(viewLifecycleOwner) {
            setupMenu(it)
        }
    }

    private fun setupMenu(isDeleted: Boolean) {
        binding.accountAppbar.accountToolbar.menu.findItem(R.id.delete_contact).isVisible =
            !isDeleted
        binding.accountAppbar.btnAddContact.isVisible = isDeleted
        binding.accountAppbar.btnAddContact.setOnClickListener {
            viewModel.addContact(getParams().id)
        }
    }

    private fun setMuteIcon(mute: Long) {
        if (mute - System.currentTimeMillis() <= 0) {
            binding.imNotifications.setImageResource(R.drawable.ic_bell_blue)
        } else if ((mute - System.currentTimeMillis()) > TimeMute.DAY1.time) {
            binding.imNotifications.setImageResource(R.drawable.ic_bell_off_forever_light_grey)
        } else {
            binding.imNotifications.setImageResource(R.drawable.ic_bell_sleep_light_grey)
        }
    }


}
