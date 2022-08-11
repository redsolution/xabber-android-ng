package com.xabber.presentation.application.fragments.account

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.xabber.R
import com.xabber.databinding.FragmentQrCodeBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.util.AppConstants

class QRCodeFragment : BaseFragment(R.layout.fragment_qr_code) {
    private val binding by viewBinding(FragmentQrCodeBinding::bind)

    companion object {
        fun newInstance(params: QRCodeParams): QRCodeFragment {
            val args = Bundle().apply {
                putParcelable(AppConstants.QR_CODE_PARAMS, params)
            }
            val fragment = QRCodeFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getParams(): QRCodeParams =
        requireArguments().getParcelable(AppConstants.QR_CODE_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        params.topToBottom = binding.tvQrCodeName.id
        params.startToStart = binding.clQrCodeContainer.id
        params.endToEnd = binding.clQrCodeContainer.id
        params.setMargins(0, 8, 0, 0)
        binding.tvQrCodeJid.layoutParams = params
        setAppbarPadding()
        changeUIWithData()
        initToolbarActions()
        generateQrCode()
        binding.clQrCodeContainer.setBackgroundResource(R.color.grey_200)
    }

    private fun setAppbarPadding() {
        binding.appbarQrCode.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
    }

    private fun changeUIWithData() {
        binding.tvQrCodeName.text = getParams().name
        binding.tvQrCodeJid.text = getParams().jid
        binding.appbarQrCode.setBackgroundResource(getParams().color)
    }

    private fun initToolbarActions() {
        binding.toolbarQrCode.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbarQrCode.setNavigationOnClickListener {
            navigator().closeDetail()
        }
        binding.toolbarQrCode.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.share_qr_code -> {
                    shareQrCode()
                }
            }; true
        }
    }

    private fun generateQrCode() {
        val writer = MultiFormatWriter()
        val matrix = writer.encode(getParams().jid, BarcodeFormat.QR_CODE, 200, 200)
        val encoder = BarcodeEncoder()
        val bitmap = encoder.createBitmap(matrix)
        binding.imQrCode.setImageBitmap(bitmap)
    }

    private fun shareQrCode() {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, getParams().jid)
        startActivity(Intent.createChooser(shareIntent, getParams().jid))
    }
}