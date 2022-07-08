package com.xabber.presentation.application.fragments.settings

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSettingsBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.Mask
import com.xabber.presentation.application.activity.MaskChanger
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader

class SettingsFragment : BaseFragment(R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private var isDarkMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
   updateMaskAvatar()

        binding.imMode.setOnClickListener {
            isDarkMode = !isDarkMode
            //  if (isDarkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            //   else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        binding.settingsToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.circle -> {
                    MaskChanger.setMask(Mask.Circle)
                    updateMaskAvatar()
                }
                R.id.hexagon -> {
                    MaskChanger.setMask(Mask.Hexagon)
                    updateMaskAvatar()
                }
                R.id.octagon -> {
                    MaskChanger.setMask(Mask.Octagon)
                    updateMaskAvatar()
                }
                R.id.pentagon -> {
                    MaskChanger.setMask(Mask.Pentagon)
                    updateMaskAvatar()
                }
                R.id.rounded -> {
                    MaskChanger.setMask(Mask.Rounded)
                    updateMaskAvatar()
                }
                R.id.squirсle -> {
                    MaskChanger.setMask(Mask.Squircle)
                    updateMaskAvatar()
                }
                R.id.star -> {
                    MaskChanger.setMask(Mask.Star)
                    updateMaskAvatar()
                }
            }
            true
        }

    }

    private fun updateMaskAvatar() {
        val mPictureBitmap = BitmapFactory.decodeResource(resources, R.drawable.img)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, MaskChanger.getMask().size32).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        binding.acc.imAvatar.setImageDrawable(maskedDrawable)
    }


}