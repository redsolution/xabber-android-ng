package com.xabber.presentation.onboarding.fragments.signup.emoji

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.FragmentEmojiAvatarBinding
import com.xabber.utils.mask.Mask
import com.xabber.utils.mask.MaskedDrawableBitmapShader
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.util.dp
import com.xabber.presentation.application.util.setFragmentResultListener
import com.xabber.presentation.onboarding.activity.OnboardingViewModel

class EmojiAvatarBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(FragmentEmojiAvatarBinding::bind)
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()
    private val viewModel = EmojiAvatarViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_emoji_avatar, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { setupBottomSheet(it) }
        return dialog
    }

    private fun setupBottomSheet(dialogInterface: DialogInterface) {
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(
            bottomSheet
        )
        bottomSheet.updateLayoutParams {
            this.height = 360.dp
            this.width = resources.getDimension(R.dimen.container_width_onboarding).toInt()
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val palette = mapOf(
            binding.greenTint to R.color.green_100,
            binding.orangeTint to R.color.orange_100,
            binding.redTint to R.color.red_100,
            binding.blueTint to R.color.blue_100,
            binding.indigoTint to R.color.indigo_100,
            binding.purpleTint to R.color.purple_100,
            binding.limeTint to R.color.lime_100,
            binding.pinkTint to R.color.pink_100,
            binding.amberTint to R.color.amber_100,
        )

        val toggles = mapOf(
            binding.greenTint to binding.greenTintToggle,
            binding.orangeTint to binding.orangeTintToggle,
            binding.redTint to binding.redTintToggle,
            binding.blueTint to binding.blueTintToggle,
            binding.indigoTint to binding.indigoTintToggle,
            binding.purpleTint to binding.purpleTintToggle,
            binding.limeTint to binding.limeTintToggle,
            binding.pinkTint to binding.pinkTintToggle,
            binding.amberTint to binding.amberTintToggle,
        )

        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, Mask.Circle.size128).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        Log.d(
            "results",
            "${binding.avatarBackground.layoutParams.width}, ${binding.avatarBackground.width}"
        )

        palette.forEach { mapElem ->
            mapElem.key.setOnClickListener {
                binding.avatarBackground.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), mapElem.value)
                )
                Log.d(
                    "results",
                    "${binding.avatarBackground.width}, ${binding.avatarBackground.height}"
                )
                val newBitmap =
                    viewModel.getBitmapFromView(requireContext(), binding.avatarBackground)
                maskedDrawable.setPictureBitmap(newBitmap)
                binding.avatarBackground.setBackgroundDrawable(maskedDrawable)

                for (t in toggles) {
                    t.value.isVisible = false
                }
                toggles[mapElem.key]!!.isVisible = true
            }
        }

        with(binding) {
            toggles[blueTint]!!.isVisible = true
            avatarBackground.setOnClickListener {
                dismiss()
                EmojiKeyboardBottomSheet().show(parentFragmentManager, null)
            }
            imEditSmile.setOnClickListener {
                dismiss()
                EmojiKeyboardBottomSheet().show(parentFragmentManager, null)
            }
            saveButton.setOnClickListener {
                val bitmap = viewModel.getBitmapFromView(requireContext(), avatarBackground)
                onboardingViewModel.setAvatarBitmap(bitmap)
                viewModel.saveBitmapToFile(bitmap, requireContext().cacheDir)
                dismiss()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(AppConstants.REQUEST_EMOJI_KEY) { _, result ->
            result.getString(AppConstants.RESPONSE_EMOJI_KEY)?.let { emoji ->
                binding.avatarBackground.text = emoji
            }
        }
    }

}
