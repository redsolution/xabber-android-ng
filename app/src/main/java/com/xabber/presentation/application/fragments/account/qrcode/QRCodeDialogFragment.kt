package com.xabber.presentation.application.fragments.account.qrcode

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.xabber.R
import com.xabber.databinding.DialogQrCodeBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.utils.parcelable
import java.io.File
import java.io.FileOutputStream

class QRCodeDialogFragment : DialogFragment() {
    private lateinit var binding: DialogQrCodeBinding
    private var qrGenerator: QRCodeBitmapGenerator? = null

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
        requireArguments().parcelable(AppConstants.QR_CODE_PARAMS)!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogQrCodeBinding.inflate(layoutInflater)
        qrGenerator = QRCodeBitmapGenerator(requireContext())
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root)
        changeUiWithData()
        initToolbarActions()
        generateQrCode()
        return dialog.create()
    }

    private fun changeUiWithData() {
        val colorKey = getParams().colorKey
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        binding.toolbarQrCode.setBackgroundResource(colorRes)
        binding.tvQrCodeName.text = getParams().name
        binding.tvQrCodeJid.text = getParams().jid
    }

    private fun initToolbarActions() {
        binding.toolbarQrCode.setNavigationIcon(R.drawable.ic_close_white)

        binding.toolbarQrCode.setNavigationOnClickListener {
            dismiss()
        }
        binding.toolbarQrCode.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.share_qr_code -> {
                    shareQrCode()
                    dismiss()
                }
            }; true
        }
    }

    private fun generateQrCode() {
        val qrBitmap = qrGenerator?.getQrBitmap(getParams().jid, 300)
        binding.imQrCode.setImageBitmap(qrBitmap)
    }

    private fun shareQrCode() {
        val file = makeFileWithQrCode()
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "image/png"
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${XabberApplication.applicationContext().packageName}.provider",
            file
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(shareIntent, requireContext().resources.getString(R.string.share_qr)))
    }

    private fun makeFileWithQrCode(): File {
        val bitmap = qrGenerator?.generateQRCodeToSend(getParams().name, getParams().jid, 500, getParams().colorKey)
        val fileName = "qr_code.png"
        val filePath = activity?.externalCacheDir?.absolutePath + File.separator + fileName
        val file = File(filePath)
        val outputStream = FileOutputStream(file)
        bitmap?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return file
    }

}
