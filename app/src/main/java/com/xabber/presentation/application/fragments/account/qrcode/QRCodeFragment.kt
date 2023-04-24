package com.xabber.presentation.application.fragments.account.qrcode

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentQrCodeBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.utils.parcelable
import java.io.File
import java.io.FileOutputStream

class QRCodeFragment : DetailBaseFragment(R.layout.fragment_qr_code) {
    private val binding by viewBinding(FragmentQrCodeBinding::bind)
    private var qrGenerator: QRCodeBitmapGenerator? = null

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
        requireArguments().parcelable(AppConstants.QR_CODE_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        qrGenerator = QRCodeBitmapGenerator(requireContext())
        changeUIWithData()
        initToolbarActions()
        generateQrCode()
    }

    private fun changeUIWithData() {
        binding.tvQrCodeName.text = getParams().name
        binding.tvQrCodeJid.text = getParams().jid
        val colorKey = getParams().colorKey
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        binding.appbar.setBackgroundResource(colorRes)
    }

    private fun initToolbarActions() {
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.share_qr_code -> {
                    shareQrCode()
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
        startActivity(Intent.createChooser(shareIntent, "Поделиться QR-кодом"))
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
