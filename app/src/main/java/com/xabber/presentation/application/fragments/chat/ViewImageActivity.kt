package com.xabber.presentation.application.fragments.chat


import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.xabber.R

import com.xabber.databinding.ActivityViewImageBinding
import com.xabber.models.dto.MediaDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.showToast


class ViewImageActivity : AppCompatActivity() {
    private val binding: ActivityViewImageBinding by lazy {
        ActivityViewImageBinding.inflate(
            layoutInflater
        )
    }
    private var adapter: PhotoPagerAdapter? = null
    private var startPosition = 0
    private var messageUid = ""
    private var mediaList = ArrayList<MediaDto>()
    private var selectedItems = HashSet<Long>()

    private val onCheckedChangeListener =
        android.widget.CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            val currentItem = binding.viewPager.currentItem
            if (isChecked) {
                if (selectedItems.size >= 10) {
                    showToast(R.string.attach_files_warning)
                    buttonView.isChecked = false
                } else {
                    mediaList[currentItem].let { selectedItems.add(it.id) }
                }
            } else {
                mediaList[currentItem].let { selectedItems.remove(it.id) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        startPosition = savedInstanceState?.getInt(AppConstants.VIEW_PAGER_CURRENT_POSITION)
            ?: intent.getIntExtra(AppConstants.IMAGE_POSITION_KEY, 0)
        val longAr =
            savedInstanceState?.getLongArray(AppConstants.SELECTED_SET) ?: intent.getLongArrayExtra(
                AppConstants.SELECTED_IDES
            ) ?: LongArray(0)
        selectedItems = HashSet(longAr.toList())
        mediaList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableArrayListExtra(AppConstants.MEDIA_LIST, MediaDto::class.java)
                ?: ArrayList()
        else
            intent.getParcelableArrayListExtra(AppConstants.MEDIA_LIST) ?: ArrayList()
        messageUid = intent.getStringExtra(AppConstants.MESSAGE_UID) ?: ""
        if (messageUid.isNotEmpty()) {
            binding.checkBox.isVisible = false
        }
        setAppbarPadding()
        setupToolbarActions()
        initMediaPager()
    }

    private fun setAppbarPadding() {
        binding.appbar.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (messageUid.isNotEmpty()){
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_view_image_activity, menu)}
        return true
    }

    private fun setupToolbarActions() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setAppbarTitle(startPosition)
        if (messageUid.isNotEmpty()) {
            binding.checkBox.isVisible = false
binding.toolbar.setOnMenuItemClickListener {
    when(it.itemId) {
        R.id.share -> shareMedia()
    }; true
}
        } else {

        val startMediaDto = mediaList[startPosition]
        binding.checkBox.isChecked = selectedItems.contains(startMediaDto.id)
        binding.checkBox.setOnCheckedChangeListener(onCheckedChangeListener) }
    }

    private fun shareMedia() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "image/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, binding.viewPager.currentItem)
        startActivity(Intent.createChooser(shareIntent, "Поделиться через"))    }

    private fun setAppbarTitle(position: Int) {
        supportActionBar?.title =
            "${position + 1} " + resources.getString(R.string.of) + " ${mediaList.size}"
    }

    private fun initMediaPager() {
        adapter = PhotoPagerAdapter(mediaList, this)
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startPosition, false)
        if (messageUid.isEmpty()) {
            binding.viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    setAppbarTitle(position)
                    binding.checkBox.setOnCheckedChangeListener(null)
                    binding.checkBox.isChecked =
                        (mediaList[binding.viewPager.currentItem].let { selectedItems.contains(it.id) })
                    binding.checkBox.setOnCheckedChangeListener(onCheckedChangeListener)
                }
            })
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult()
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult()
        onBackPressedDispatcher.onBackPressed()
    }

    private fun setResult() {
        val resultIntent = Intent()
        resultIntent.putExtra(AppConstants.VIEW_IMAGE_ACTIVITY_RESULT, selectedItems.toLongArray())
        setResult(222, resultIntent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(AppConstants.VIEW_PAGER_CURRENT_POSITION, binding.viewPager.currentItem)
        outState.putLongArray(AppConstants.SELECTED_SET, selectedItems.toLongArray())
    }

    override fun onDestroy() {
        adapter = null
        super.onDestroy()
    }

}
