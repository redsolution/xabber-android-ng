package com.xabber.presentation.application.fragments.account.qrcode

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
import com.xabber.presentation.AppConstants

class QRCodeDialogFragment : DialogFragment() {
    private lateinit var toolbarQrCode: MaterialToolbar
    private lateinit var tvQrCodeName: TextView
    private lateinit var tvQrCodeJid: TextView
    private lateinit var imQrCode: ImageView

    companion object {
        fun newInstance(params: QRCodeParams): QRCodeDialogFragment {
            val args = Bundle().apply {
                putParcelable(AppConstants.QR_CODE_PARAMS, params)
            }
            val fragment = QRCodeDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getParams(): QRCodeParams =
        requireArguments().getParcelable(AppConstants.QR_CODE_PARAMS)!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(context)
        val view = layoutInflater.inflate(R.layout.dialog_qr_code, null)
        tvQrCodeName = view.findViewById(R.id.tv_qr_code_name)
        tvQrCodeJid = view.findViewById(R.id.tv_qr_code_jid)
        toolbarQrCode = view.findViewById(R.id.toolbar_qr_code)
        imQrCode = view.findViewById(R.id.im_qr_code)
        changeUiWithData()
        initToolbarActions()
        generateQrCode()
        dialog.setView(view)
        return dialog.create()
    }

    private fun changeUiWithData() {
        toolbarQrCode.setBackgroundResource(getParams().color)
        tvQrCodeName.text = getParams().name
        tvQrCodeJid.text = getParams().jid
    }

    private fun initToolbarActions() {
        toolbarQrCode.setNavigationIcon(R.drawable.ic_close_white)

        toolbarQrCode.setNavigationOnClickListener {
            dismiss()
        }
        toolbarQrCode.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.share_qr_code -> {
                    shareQrCode()
                }
            }; true
        }
    }

    private fun generateQrCode() {
        val writer = MultiFormatWriter()
        val matrix = writer.encode(getParams().jid, BarcodeFormat.QR_CODE, 300, 300)
        val encoder = BarcodeEncoder()
        val bitmap = encoder.createBitmap(matrix)
        imQrCode.setImageBitmap(bitmap)
    }

    private fun shareQrCode() {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, getParams().jid)
        startActivity(Intent.createChooser(shareIntent, getParams().jid))
    }

}
