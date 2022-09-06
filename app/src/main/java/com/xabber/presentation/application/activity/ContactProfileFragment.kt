package com.xabber.presentation.application.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES.O
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.databinding.library.BuildConfig
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.xabber.R
import com.xabber.databinding.QuestionaryContactFragmentBinding
import com.xabber.model.dto.ContactDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeDialogFragment
import com.xabber.presentation.application.fragments.account.qrcode.QRCodeParams
import com.xabber.presentation.application.fragments.chatlist.NotificationBottomSheet
import com.xabber.presentation.application.util.showToast
import com.xabber.utils.blur.BlurTransformation
import com.xabber.utils.mask.MaskPrepare

class ContactProfileFragment : DetailBaseFragment(R.layout.questionary_contact_fragment) {
    private val binding by viewBinding(QuestionaryContactFragmentBinding::bind)
    private var mediaAdapter: MediaAdapter? = null

    companion object {
        fun newInstance(contactDto: ContactDto): ContactProfileFragment {
            val args =
                Bundle().apply { putParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT, contactDto) }
            val fragment = ContactProfileFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getContact(): ContactDto =
        requireArguments().getParcelable(AppConstants.PARAMS_CONTACT_ACCOUNT)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setToolbarPadding()
        changeUiWidthData()
        initToolbarActions()
        initPanelActions()

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
      loadBackground()
        loadAvatar()
       defineColor()
        with(binding.accountAppbar) {
            tvTitle.text = getContact().userName
            tvSubtitle.text = getContact().jid
        }
        binding.tvJid.text = getContact().jid
        binding.tvFullName.text = getContact().name
        binding.tvSurname.text = getContact().surname
    }


    private fun loadBackground() {
        binding.accountAppbar.imBackdrop.alpha = 1f
        Glide.with(requireContext())
            .load(getContact().avatar)
            .transform(
                BlurTransformation(
                    25,
                    4,

                    ContextCompat.getColor(requireContext(),getContact().color))
            ).diskCacheStrategy(DiskCacheStrategy.ALL).error(getContact().color)
            .into(binding.accountAppbar.imBackdrop as ImageView)
    }

//    @SuppressLint("ResourceAsColor")
//    private fun loadBackground() {
//        if (Build.VERSION.SDK_INT >= O) {
//            Glide.with(requireContext())
//                .load(getContact().avatar)
//                .transform(
//                    BlurTransformation(
//                        25,
//                        8,
//                        ContextCompat.getColor(
//                            requireContext(),
//                            getContact().color
//                        )
//                    )
//                )
//                .into(binding.accountAppbar.imBackdrop)
//        } else {
//            binding.accountAppbar.imBackdrop.alpha = 0f
//            binding.accountAppbar.appbar.setBackgroundColor( ContextCompat.getColor(
//                requireContext(),
//                getContact().color
//            ))
//               binding.accountAppbar.imBackdrop.animate().alphaBy(0f).alpha(1f).setDuration(500L).start()
//        }
//    }

    private fun loadAvatar() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, getContact().avatar, UiChanger.getMask().size176)
        Glide.with(requireContext())
            .load(maskedDrawable).diskCacheStrategy(DiskCacheStrategy.ALL).error(getContact().color)
            .into(binding.accountAppbar.imPhoto)
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
                        val dialog = QRCodeDialogFragment.newInstance(
                            QRCodeParams(
                                getContact().userName!!,
                                getContact().jid!!,
                                getContact().color
                            )
                        )
                        navigator().showDialogFragment(dialog)
                        // navigator().showQRCode(getContact().jid!!)
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
                        tvTitle.startAnimation(
                            anim
                        )
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
                    collapsingToolbar.title =
                        " "
                    isShow = false
                }
                when {
                    verticalOffset > -700 -> {
                        val anim = AnimationUtils.loadAnimation(context, R.anim.appearance)
                        if (!tvTitle.isVisible) {
                            tvTitle.startAnimation(
                                anim
                            )
                            tvSubtitle.startAnimation(anim)
                            imPhoto.startAnimation(anim)
                            imPhoto.isVisible = true
                            tvSubtitle.isVisible = true
                            tvTitle.isVisible = true
                        }
                    }
                    verticalOffset < -700 -> {
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
            //   navigator().showChat(getContact())
        }

        binding.rlCall.setOnClickListener { showToast("This feature is not implemented") }

        binding.rlNotifications.setOnClickListener {
            val dialog = NotificationBottomSheet()
            navigator().showBottomSheetDialog(dialog)
        }

        binding.rlBlock.setOnClickListener { blockContact() }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaAdapter = null
    }

}
