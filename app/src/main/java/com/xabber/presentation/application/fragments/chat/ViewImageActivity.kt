package com.xabber.presentation.application.fragments.chat


import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.messages.MessageStorageItem

import com.xabber.databinding.ActivityViewImageBinding
import com.xabber.dto.MediaDto
import com.xabber.dto.MessageDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.showToast
import io.realm.kotlin.Realm
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


class ViewImageActivity : AppCompatActivity() {
    private var first = -1
    val realm = Realm.open(defaultRealmConfig())
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
    private val viewModel: MediaViewModel by viewModels()

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

        mediaList = viewModel.getMediaList()
        val posId = intent.getLongExtra(AppConstants.IMAGE_POSITION_KEY, -1L)
        first = intent.getIntExtra("uu", -1)
        var a = 0
        if (posId != -1L) {
            for (i in 0 until mediaList.size) {
                if (mediaList[i].id == posId)
                    a = i
            }
        }
        startPosition = savedInstanceState?.getInt(AppConstants.VIEW_PAGER_CURRENT_POSITION) ?: a
        val longAr =
            savedInstanceState?.getLongArray(AppConstants.SELECTED_SET) ?: intent.getLongArrayExtra(
                AppConstants.SELECTED_IDES
            ) ?: LongArray(0)
        selectedItems = HashSet(longAr.toList())
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
        if (messageUid.isNotEmpty()) {
            val inflater = menuInflater
            inflater.inflate(R.menu.menu_view_image_activity, menu)
        }
        return true
    }

    private fun setupToolbarActions() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setAppbarTitle(startPosition)
        if (messageUid.isNotEmpty()) {
            binding.checkBox.isVisible = false
            binding.toolbar.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.share -> shareMedia()
                }; true
            }
        } else {

            val startMediaDto = mediaList[startPosition]
            binding.checkBox.isChecked = selectedItems.contains(startMediaDto.id)
            binding.checkBox.setOnCheckedChangeListener(onCheckedChangeListener)
        }
    }

    private fun shareMedia() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "image/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, binding.viewPager.currentItem)
        startActivity(Intent.createChooser(shareIntent, "Поделиться через"))
    }

    private fun setAppbarTitle(position: Int) {
        supportActionBar?.title =
            "${position + 1} " + resources.getString(R.string.of) + " ${mediaList.size}"
    }

    private fun initMediaPager() {

if (messageUid.isNotEmpty()) {
    mediaList.clear()
  val message = realm.query(MessageStorageItem::class, "primary = '$messageUid'").first().find()
    for (i in 0 until message?.references!!.size) {
        mediaList.add(MediaDto(i.toLong(), "", Date(), message.references[i].uri!!.toUri()))
    }
  if (first >= 0)  startPosition = first
}
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
        setResult(Activity.RESULT_OK, resultIntent)
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
