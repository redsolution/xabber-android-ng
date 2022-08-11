package com.xabber.presentation.application.fragments.contacts

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.tabs.TabLayout
import com.xabber.R
import com.xabber.data.dto.ContactDto
import com.xabber.databinding.FragmentContactAccountBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.*
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.account.QRCodeParams
import com.xabber.presentation.application.fragments.chatlist.NotificationBottomSheet
import com.xabber.presentation.application.util.AppConstants
import com.xabber.presentation.application.util.BlurTransformation
import com.xabber.presentation.application.util.showToast

class ContactAccountFragment : BaseFragment(R.layout.fragment_contact_account) {
    private val binding by viewBinding(FragmentContactAccountBinding::bind)
    private var mediaAdapter: MediaAdapter? = null

    companion object {
        fun newInstance(contactDto: ContactDto): ContactAccountFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT, contactDto) }
            val fragment = ContactAccountFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getContact(): ContactDto =
        requireArguments().getParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT)!!

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
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
        changeUiWidthData()
        initToolbarActions()
        initPanelActions()
        initTabLayout()
    }

    private fun changeUiWidthData() {
        loadBackground()
        loadAvatar()
        defineColor()
        with(binding.accountAppbar) {
            tvTitle.text = getContact().userName
            tvSubtitle.text = getContact().jid
            collapsingToolbar.setBackgroundColor(getContact().color)
        }
        binding.tvJid.text = getContact().jid
        binding.tvFullName.text = getContact().name
        binding.tvSurname.text = getContact().surname
    }

    private fun loadBackground() {
        val blurTransformation = BlurTransformation(
            25, 8, ResourcesCompat.getColor(
                resources,
                getContact().color,
                requireContext().theme
            )
        )
        Glide.with(context!!)
            .load(getContact().avatar)
            .transform(
                MultiTransformation(
                    CenterCrop(),
                    blurTransformation
                )
            ).into(binding.accountAppbar.imBackdrop)
    }

    private fun loadAvatar() {
        val mPictureBitmap = BitmapFactory.decodeResource(resources, getContact().avatar)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, UiChanger.getMask().size176).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        binding.accountAppbar.imPhoto.setImageDrawable(maskedDrawable)
    }

    private fun defineColor() {
        binding.accountAppbar.collapsingToolbar.setContentScrimColor(
            ResourcesCompat.getColor(
                resources,
                getContact().color,
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
        binding.accountAppbar.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.accountAppbar.toolbar.setNavigationOnClickListener { navigator().closeDetail() }

        binding.accountAppbar.toolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_toolbar_contact_account, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.contact_qr_code -> {
                        navigator().showQRCode(
                            QRCodeParams(
                                getContact().userName!!,
                                getContact().jid!!,
                                getContact().color
                            )
                        )
                    }
                    R.id.edit_contact_account -> {
                        navigator().showEditContact(getContact())
                    }
                    R.id.delete_contact_account -> {
                        val dialog = DeletingContactDialog.newInstance(getContact().userName!!)
                        navigator().showDialogFragment(dialog)
                    }
                    R.id.send_contact -> {
                        shareContact(getContact().userName!!)
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
                        AnimationUtils.loadAnimation(context, R.animator.disappearance_long)
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
                    collapsingToolbar.title = getContact().userName
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
        val dialog = BlockContactDialog.newInstance(getContact().userName!!)
        navigator().showDialogFragment(dialog)
    }

    private fun initPanelActions() {
        binding.rlOpenChat.setOnClickListener {
          //  navigator().showChat(getContact().userName!!)
        }

        binding.rlCall.setOnClickListener { showToast("This feature is not implemented") }

        binding.rlNotifications.setOnClickListener {
            val dialog = NotificationBottomSheet()
            navigator().showBottomSheetDialog(dialog)
        }

        binding.rlBlock.setOnClickListener { blockContact() }

        binding.tvViewFullProfile.setOnClickListener {
            navigator().showContactProfile(getContact())
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

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigator().closeDetail()
        }
    }

}
