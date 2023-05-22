package com.xabber.presentation.application.fragments.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentMaskBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.utils.MaskManager

class MaskFragment : DetailBaseFragment(R.layout.fragment_mask) {
    private val binding by viewBinding(FragmentMaskBinding::bind)
    private val maskMap = mapOf(
        R.drawable.ic_mask_circle to R.id.circle,
        R.drawable.ic_mask_hexagon to R.id.hexagon,
        R.drawable.ic_mask_octagon to R.id.octagon,
        R.drawable.ic_mask_pentagon to R.id.pentagon,
        R.drawable.ic_mask_rounded to R.id.rounded,
        R.drawable.ic_mask_squircle to R.id.squirсle,
        R.drawable.ic_mask_star to R.id.star
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRadioGroup()
    }

    private fun setupRadioGroup() {
        binding.radioGroup.setOnCheckedChangeListener { radioGroup, _ ->
            radioGroup.jumpDrawablesToCurrentState()
        }

        binding.radioGroup.check(getCheckedItemId())
        binding.imDemonstration.setImageResource(MaskManager.mask)

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            maskMap.values.find { it == checkedId }?.let {
                setAvatarsShape(
                    maskMap.entries.find { entry -> entry.value == it }?.key
                        ?: R.drawable.ic_mask_circle
                )
            }
        }
    }

    private fun getCheckedItemId(): Int {
        return maskMap[MaskManager.mask] ?: R.id.circle
    }

    private fun setAvatarsShape(mask: Int) {
        binding.imDemonstration.setImageResource(mask)
        MaskManager.mask = mask
        val pref =
            activity?.getSharedPreferences(AppConstants.SHARED_PREF_MASK, Context.MODE_PRIVATE)
                ?: return
        pref.edit()?.putInt(AppConstants.MASK_KEY, mask)?.apply()
    }

}
