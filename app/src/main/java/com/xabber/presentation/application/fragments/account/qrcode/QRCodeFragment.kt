package com.xabber.presentation.application.fragments.account.qrcode

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
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.fragments.DetailBaseFragment

class QRCodeFragment : DetailBaseFragment(R.layout.fragment_qr_code) {
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
        changeUIWithData()
        initToolbarActions()
        generateQrCode()
        binding.clQrCodeContainer.setBackgroundResource(R.color.grey_200)
    }

    private fun changeUIWithData() {
        binding.tvQrCodeName.text = getParams().name
        binding.tvQrCodeJid.text = getParams().jid
        binding.appbar?.setBackgroundResource(getParams().color)
    }

    private fun initToolbarActions() {
        binding.toolbar?.setOnMenuItemClickListener {
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