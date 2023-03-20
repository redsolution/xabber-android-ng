package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.xabber.R
import com.xabber.databinding.ActivityViewImageBinding
import com.xabber.presentation.application.activity.DisplayManager


class ViewImageActivity : AppCompatActivity() {
    private val binding: ActivityViewImageBinding by lazy {
        ActivityViewImageBinding.inflate(
            layoutInflater
        )
    }
    private var adapter: PhotoPagerAdapter? = null
    private var startPosition = 0
    private var list: ArrayList<Uri>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
      //  WindowCompat.setDecorFitsSystemWindows(window, false)
        startPosition = intent.getIntExtra("pos", 0)
        list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableArrayListExtra("im", Uri::class.java)
        else
            intent.getParcelableArrayListExtra("im")
        setAppbarPadding()
        setupToolbarActions()
        initPhotoPager()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setAppbarPadding() {
        binding.appbar.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
    }

    private fun setupToolbarActions() {
        setSupportActionBar(binding.toolbarDefault)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "${startPosition+1} " + resources.getString(R.string.of) + " ${list?.size}"
    }

    private fun initPhotoPager() {

        adapter = PhotoPagerAdapter(list!!, this)

        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startPosition, false)
        binding.viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
             supportActionBar?.title = "${position+1} " + resources.getString(R.string.of) + " ${list?.size}"
            }
        })

    }


}