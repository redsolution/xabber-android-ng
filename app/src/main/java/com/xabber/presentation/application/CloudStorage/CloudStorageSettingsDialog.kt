package com.xabber.presentation.application.CloudStorage

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCloudStorageSettingsBinding
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.AccountDto
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.toAccountDto
import io.realm.kotlin.Realm
import java.text.DecimalFormat

data class StorageBlock(
    val type: String,
    val sizeInBytes: Long,
    val iconResId: Int
)

class CloudStorageSettingsDialog : DialogFragment() {
    private val binding by viewBinding(FragmentCloudStorageSettingsBinding::bind)
    private val realm = Realm.open(defaultRealmConfig())

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
                val width = (resources.displayMetrics.widthPixels * 0.8).toInt()
                val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER)
            }
            if (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE) {
                val width = (resources.displayMetrics.widthPixels * 0.48).toInt()
                val height = (resources.displayMetrics.heightPixels * 0.97).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cloud_storage_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        setupToolbarColor()
        setupStorageUsageList()
       // setupStorageInfo()
    }

    private fun setupToolbarColor() {
        val account = getPrimaryAccount()
        val colorKey = account?.colorKey ?: resources.getString(R.string.blue)
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        defineColor(colorRes)
    }

    private fun defineColor(colorRes: Int) {
        binding.toolbar.setBackgroundColor(
            ResourcesCompat.getColor(resources, colorRes, requireContext().theme)
        )
    }

    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { it.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
        }
        return accountDto
    }

    private fun setupStorageUsageList() {
        val storageBlocks = getStorageBlocksFromDataSource()
        val adapter = StorageBlockAdapter(storageBlocks)
        binding.blocksList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

//    private fun setupStorageInfo() {
//        val totalStorageBytes = 10_737_418_240L // 10 GB - replace with your actual total
//        val storageBlocks = getStorageBlocksFromDataSource()
//        val usedStorageBytes = storageBlocks.sumOf { it.sizeInBytes }
//
//        // ProgressBar
//        val progress = ((usedStorageBytes.toDouble() / totalStorageBytes) * 100).toInt()
//        binding.storageProgressBar.apply {
//            max = 100
//            this.progress = progress
//        }
//
//        // Legend: Total Storage
//        binding.tvStorageTotal.text = "${formatSize(usedStorageBytes)} / ${formatSize(totalStorageBytes)}"
//
//        // Legend: Breakdown (dynamically show/hide based on data)
//        val imagesBlock = storageBlocks.find { it.type == "Images" }
//        binding.tvLegendImages.apply {
//            visibility = if (imagesBlock != null && imagesBlock.sizeInBytes > 0) View.VISIBLE else View.GONE
//            text = "Images: ${formatSize(imagesBlock?.sizeInBytes ?: 0)}"
//        }
//
//        val videosBlock = storageBlocks.find { it.type == "Videos" }
//        binding.tvLegendVideos.apply {
//            visibility = if (videosBlock != null && videosBlock.sizeInBytes > 0) View.VISIBLE else View.GONE
//            text = "Videos: ${formatSize(videosBlock?.sizeInBytes ?: 0)}"
//        }
//
//        val documentsBlock = storageBlocks.find { it.type == "Documents" }
//        binding.tvLegendDocuments.apply {
//            visibility = if (documentsBlock != null && documentsBlock.sizeInBytes > 0) View.VISIBLE else View.GONE
//            text = "Documents: ${formatSize(documentsBlock?.sizeInBytes ?: 0)}"
//        }
//    }

    private fun getStorageBlocksFromDataSource(): List<StorageBlock> {
        // Placeholder - replace with your real data source
        return listOf(
            StorageBlock("Images", 2_147_483_648L, R.drawable.ic_image_dark), // 2 GB
            StorageBlock("Videos", 3_221_225_472L, R.drawable.ic_video), // 3 GB
            StorageBlock("Documents", 1_073_741_824L, R.drawable.ic_document) // 1 GB
        )
    }

    private fun formatSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "${DecimalFormat("#.##").format(size)} ${units[unitIndex]}"
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }
}