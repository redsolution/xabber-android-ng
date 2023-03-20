package com.xabber.presentation.application.fragments.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentInterfaceBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.MaskManager
import com.xabber.presentation.application.fragments.DetailBaseFragment

class InterfaceFragment : DetailBaseFragment(R.layout.fragment_interface) {
    private val binding by viewBinding(FragmentInterfaceBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRadioGroup()
    }

    private fun setupRadioGroup() {

        val id = when (MaskManager.mask) {
            R.drawable.ic_mask_circle -> R.id.circle
            R.drawable.ic_mask_hexagon -> R.id.hexagon
            R.drawable.ic_mask_octagon -> R.id.octagon
            R.drawable.ic_mask_pentagon -> R.id.pentagon
            R.drawable.ic_mask_rounded -> R.id.rounded
            R.drawable.ic_mask_squircle -> R.id.squirсle
            R.drawable.ic_mask_star -> R.id.star
            else -> { R.id.circle}
        }
        binding.radioGroup.check(id)
        binding.imDemonstration.setImageResource(MaskManager.mask)

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.circle -> setAvatarsShape(R.drawable.ic_mask_circle)
                R.id.hexagon -> setAvatarsShape(R.drawable.ic_mask_hexagon)
                R.id.octagon -> setAvatarsShape(R.drawable.ic_mask_octagon)
                R.id.pentagon -> setAvatarsShape(R.drawable.ic_mask_pentagon)
                R.id.rounded -> setAvatarsShape(R.drawable.ic_mask_rounded)
                R.id.squirсle -> setAvatarsShape(R.drawable.ic_mask_squircle)
                R.id.star -> setAvatarsShape(R.drawable.ic_mask_star)
            }
        }
    }

    private fun setAvatarsShape(mask: Int) {
        binding.imDemonstration.setImageResource(mask)
        MaskManager.mask = mask
        val pref = activity?.getSharedPreferences("Pref", Context.MODE_PRIVATE)
        pref?.edit()?.putInt(AppConstants.MASK_KEY, mask)?.apply()
    }
}