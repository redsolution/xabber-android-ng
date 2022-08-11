package com.xabber.presentation.application.fragments.account

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.xabber.R
import com.xabber.presentation.application.util.AppConstants

class ContactAccountDialog: DialogFragment() {
//    private lateinit var toolbarQrCode: MaterialToolbar
//    private lateinit var tvQrCode: TextView
//    private lateinit var imQrCode: ImageView
//
//    companion object {
//        fun newInstance(jid: String): QRCodeFragment {
//            val args = Bundle().apply { putString(AppConstants.JID_FOR_QR_CODE_GENERATE, jid) }
//            val fragment = QRCodeFragment()
//            fragment.arguments = args
//            return fragment
//        }
//    }
//
//    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//        val dialog = AlertDialog.Builder(context)
//        val view = layoutInflater.inflate(R.layout.fragment_qr_code, null)
//        changeUiWidthData()
//        initToolbarActions()
//        initPanelActions()
//        dialog.setView(view)
//        return dialog.create()
//    }
//
//    private fun getJid(): String? =
//        requireArguments().getString(AppConstants.JID_FOR_QR_CODE_GENERATE)
//
//
//     private fun changeUiWidthData() {
//        loadBackground()
//        loadAvatar()
//        defineColor()
//        with(binding.accountAppbar) {
//            tvTitle.text = getContact().userName
//            tvSubtitle.text = getContact().jid
//            collapsingToolbar.setBackgroundColor(getContact().color)
//        }
//    }
//
//    private fun loadBackground() {
//        Glide.with(requireContext())
//            .load(getContact().avatar)
//            .transform(
//                MultiTransformation(
//                    CenterCrop(),
//                    BlurTransformation(
//                        context!!,
//                        25,
//                        8,
//                        ResourcesCompat.getColor(
//                            resources,
//                            getContact().color,
//                            requireContext().theme
//                        )
//                    )
//                )
//            )
//            .into(binding.accountAppbar.imBackdrop)
//    }
//
//    private fun loadAvatar() {
//        val mPictureBitmap = BitmapFactory.decodeResource(resources, getContact().avatar)
//        val mMaskBitmap =
//            BitmapFactory.decodeResource(resources, UiChanger.getMask().size176).extractAlpha()
//        val maskedDrawable = MaskedDrawableBitmapShader()
//        maskedDrawable.setPictureBitmap(mPictureBitmap)
//        maskedDrawable.setMaskBitmap(mMaskBitmap)
//        binding.accountAppbar.imPhoto.setImageDrawable(maskedDrawable)
//    }
//
//    private fun defineColor() {
//        binding.accountAppbar.collapsingToolbar.setContentScrimColor(
//            ResourcesCompat.getColor(
//                resources,
//                getContact().color,
//                requireContext().theme
//            )
//        )
//        activity!!.window.statusBarColor =
//            ResourcesCompat.getColor(resources, getContact().color, requireContext().theme)
//    }
//
//    private fun initToolbarActions() {
//        binding.accountAppbar.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
//        binding.accountAppbar.toolbar.setNavigationOnClickListener { navigator().closeDetail() }
//          binding.accountAppbar.toolbar.addMenuProvider(object : MenuProvider {
//            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
//                menuInflater.inflate(R.menu.menu_account_toolbar, menu)
//            }
//
//            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
//               when (menuItem.itemId) {
//                   R.id.menu_item_generate_qr_code -> navigator().showQRCode(getContact().jid!!)
//               }
//                return true
//            }
//        })
//
//        binding.accountAppbar.toolbar.setOnMenuItemClickListener {
//            when (it.itemId) {
//                R.id.contact_qr_code -> {
// val dialog = QRCodeFragment.newInstance(getContact().jid!!)
//                        navigator().showDialogFragment(dialog)
//                }
//                R.id.edit_contact_account -> {
//                    navigator().showEditContact(getContact())
//                }
//                R.id.send_contact -> {
//                    shareContact()
//                }
//                R.id.delete_contact_account -> {
//                    deleteContact()
//                }
//            }; true
//        }
//        var isShow = true
//        var scrollRange = -1
//        with(binding.accountAppbar) {
//            appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { bar, verticalOffset ->
//                if (scrollRange + verticalOffset < 170) {
//                    val anim =
//                        AnimationUtils.loadAnimation(context, R.animator.disappearance_long)
//                    if (tvTitle.isVisible) {
//                        tvTitle.startAnimation(
//                            anim
//                        )
//                        tvSubtitle.startAnimation(anim)
//                        imPhoto.startAnimation(anim)
//                        imPhoto.isVisible = false
//                        tvSubtitle.isVisible = false
//                        tvTitle.isVisible = false
//                    }
//                }
//                if (scrollRange + verticalOffset > 170) {  val anim = AnimationUtils.loadAnimation(context, R.anim.appearance)
//                    if (!tvTitle.isVisible) {
//                        tvTitle.startAnimation(
//                            anim
//                        )
//                        tvSubtitle.startAnimation(anim)
//                        imPhoto.startAnimation(anim)
//                        imPhoto.isVisible = true
//                        tvSubtitle.isVisible = true
//                        tvTitle.isVisible = true
//                    }}
//                if (scrollRange == -1) {
//                    scrollRange = bar.totalScrollRange
//                }
//                if (scrollRange + verticalOffset == 0) {
//                    collapsingToolbar.title = getContact().userName
//                    isShow = true
//                } else if (isShow) {
//                    collapsingToolbar.title =
//                        " "
//                    isShow = false
//                }
//                when {
//                    verticalOffset > -700 -> {
//                        val anim = AnimationUtils.loadAnimation(context, R.anim.appearance)
//                        if (!tvTitle.isVisible) {
//                            tvTitle.startAnimation(
//                                anim
//                            )
//                            tvSubtitle.startAnimation(anim)
//                            imPhoto.startAnimation(anim)
//                            imPhoto.isVisible = true
//                            tvSubtitle.isVisible = true
//                            tvTitle.isVisible = true
//                        }
//                    }
//                    verticalOffset < -700 -> {
//                        val anim =
//                            AnimationUtils.loadAnimation(context, R.animator.disappearance_long)
//                        if (tvTitle.isVisible) {
//                            tvTitle.startAnimation(
//                                anim
//                            )
//                            tvSubtitle.startAnimation(anim)
//                            imPhoto.startAnimation(anim)
//                            imPhoto.isVisible = false
//                            tvSubtitle.isVisible = false
//                            tvTitle.isVisible = false
//                        }
//                    }
//                }
//            })
//        }
//    }
//
//    private fun shareContact() {
//        // открыть чаты
//    }
//
//    private fun deleteContact() {
//
//    }
//
//    private fun initPanelActions() {
//        binding.rlOpenChat.setOnClickListener { navigator().showMessage(getContact().userName!!) }
//
//        binding.rlCall.setOnClickListener { showToast("This feature is not implemented") }
//
//        binding.rlNotifications.setOnClickListener {
//            val dialog = NotificationBottomSheet()
//            navigator().showBottomSheetDialog(dialog)
//        }
//
//        binding.rlBlock.setOnClickListener { }
//    }
//
//
//
//
//
//
//
//
//
//    private fun initToolbarActions() {
//        toolbarQrCode.setNavigationIcon(R.drawable.ic_close)
//        toolbarQrCode.setNavigationOnClickListener {
//            dismiss()
//        }
//        toolbarQrCode.setOnMenuItemClickListener {
//            when (it.itemId) {
//                R.id.share_qr_code -> {
//                    shareQrCode()
//                }
//            }; true
//        }
//    }
//
//    private fun generateQrCode() {
//        val writer = MultiFormatWriter()
//        val matrix = writer.encode(getJid(), BarcodeFormat.QR_CODE, 300, 300)
//        val encoder = BarcodeEncoder()
//        val bitmap = encoder.createBitmap(matrix)
//        imQrCode.setImageBitmap(bitmap)
//    }
//
//    private fun shareQrCode() {
//        val shareIntent = Intent()
//        shareIntent.action = Intent.ACTION_SEND
//        shareIntent.type = "text/plain"
//        shareIntent.putExtra(Intent.EXTRA_TEXT, getJid())
//        startActivity(Intent.createChooser(shareIntent, getJid()))
//    }

}