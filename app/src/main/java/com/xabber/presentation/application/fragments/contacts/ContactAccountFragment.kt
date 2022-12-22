package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.tabs.TabLayout
import com.xabber.R
import com.xabber.databinding.FragmentContactAccountBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.mask.MaskPrepare
import com.xabber.utils.parcelable
import com.xabber.utils.showToast
import jp.wasabeef.glide.transformations.BlurTransformation

class ContactAccountFragment : DetailBaseFragment(R.layout.fragment_contact_account) {
    private val binding by viewBinding(FragmentContactAccountBinding::bind)
    private var mediaAdapter: MediaAdapter? = null
    private val viewModel: ContactAccountViewModel by viewModels()

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
        setToolbarPadding()
        changeUiWidthData()
        initToolbarActions()
        initPanelActions()
        initTabLayout()
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
        binding.accountAppbar.toolbar.layoutParams = params
    }

    @SuppressLint("ResourceAsColor")
    private fun changeUiWidthData() {
        loadAvatar()
        loadBackground()
        defineColor()
        val contact = viewModel.getContact(getParams().id)
        with(binding.accountAppbar) {
            tvTitle.text = contact.nickName
            tvSubtitle.text = contact.jid
        }
//        binding.tvJid.text = getContact().jid
//        binding.tvFullName.text = getContact().name
//        binding.tvSurname.text = getContact().surname
    }

    private fun loadBackground() {

        Glide.with(this)
            .load(getParams().avatar).transform(
                BlurTransformation(
                    25,
                    ContextCompat.getColor(requireContext(), getParams().color)
                )
            ).placeholder(getParams().color).transition(
                DrawableTransitionOptions.withCrossFade()
            )
            .into(binding.accountAppbar.imBackdrop)
    }

    private fun loadAvatar() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, getParams().avatar, UiChanger.getMask().size176)
        Glide.with(requireContext())
            .load(maskedDrawable).error(getParams().color)
            .into(binding.accountAppbar.imPhoto)
    }

    private fun defineColor() {
        binding.accountAppbar.collapsingToolbar.setContentScrimColor(
            ResourcesCompat.getColor(
                resources,
                getParams().color,
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

        binding.accountAppbar.toolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_toolbar_contact_account, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.contact_qr_code -> {
                        navigator().showQRCode(
                            QRCodeParams(
                                binding.accountAppbar.tvTitle.text.toString(),
                                binding.accountAppbar.tvSubtitle.text.toString(),
                                getParams().color
                            )
                        )
                    }
                    R.id.edit_contact -> {
                        navigator().showEditContact(viewModel.getContact(getParams().id))
                    }
                    R.id.delete_contact -> {
                        val dialog =
                            DeletingContactDialog.newInstance(binding.accountAppbar.tvTitle.text.toString())
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
            appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { bar, verticalOffset ->
                if (scrollRange + verticalOffset < 170) {
                    val anim =
                        AnimationUtils.loadAnimation(context, R.anim.disappearance_300)
                    if (tvTitle.isVisible) {
                        tvTitle.startAnimation(anim)
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
                if (scrollRange == -1) {
                    scrollRange = bar.totalScrollRange
                }
                if (scrollRange + verticalOffset == 0) {
                    //    collapsingToolbar.title = getContact().userName
                    isShow = true
                } else if (isShow) {
                    collapsingToolbar.title = " "
                    isShow = false
                }
            })
        }
    }

    private fun shareContact() {
        // открыть чаты
    }

    private fun deleteContact() {

    }

    private fun blockContact() {
        val name = binding.accountAppbar.tvSubtitle.text.toString()
           val dialog = BlockContactDialog.newInstance(name)
             navigator().showDialogFragment(dialog, "")
    }

    private fun initPanelActions() {
        binding.rlOpenChat.setOnClickListener {
            navigator().showChat(
                ChatParams(
                    viewModel.getChatId,
                    binding.accountAppbar.tvSubtitle.text.toString(),
                    getParams().avatar
                )
        }

        binding.rlCall.setOnClickListener { showToast("This feature is not implemented") }

        binding.rlNotifications.setOnClickListener {
            val dialog = NotificationBottomSheet()
            navigator().showBottomSheetDialog(dialog)
        }

        binding.rlBlock.setOnClickListener { blockContact() }

        binding.tvViewFullProfile.setOnClickListener {
           navigator().showContactProfile(viewModel.getContact(getParams().id))
        }
    }

    private fun initTabLayout() {
        binding.rvAttachments.layoutManager = GridLayoutManager(context, 3)
        mediaAdapter = MediaAdapter()
        binding.rvAttachments.adapter = mediaAdapter
        val list = ArrayList<String>()
        list.add("k")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        list.add("")
        mediaAdapter?.updateAdapter(list)

        binding.tabsAttachment.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

}
