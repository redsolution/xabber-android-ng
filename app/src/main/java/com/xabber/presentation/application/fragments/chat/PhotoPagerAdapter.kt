package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class PhotoPagerAdapter(private val imageUris: List<Uri>, activity: AppCompatActivity) :
    FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = imageUris.size

    override fun createFragment(position: Int): Fragment {
        return ViewImageFragment.newInstance(imageUris[position])
    }
}