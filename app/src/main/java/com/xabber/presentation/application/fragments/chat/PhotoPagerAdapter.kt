package com.xabber.presentation.application.fragments.chat

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.xabber.models.dto.MediaDto

class PhotoPagerAdapter(private val imageUris: List<MediaDto>, activity: AppCompatActivity) :
    FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = imageUris.size

    override fun createFragment(position: Int): Fragment {
        return ViewImageFragment.newInstance(imageUris[position].uri)
    }
}