package com.xabber.presentation.application.fragments.chat.message

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.FragmentEmojiAvatarBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.fragments.account.AccountViewModel
import com.xabber.presentation.application.fragments.chat.AvatarChangerBottomSheet
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.onboarding.activity.OnboardingViewModel
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiAvatarViewModel
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiKeyboardBottomSheet
import com.xabber.utils.dp
import com.xabber.utils.setFragmentResultListener
import io.realm.kotlin.internal.RealmInitializer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class EmojiBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(FragmentEmojiAvatarBinding::bind)
    private val accountViewModel: AccountViewModel by viewModels()
    private val viewModel = EmojiAvatarViewModel()
    private var color: Int = R.color.blue_100

    companion object {
        fun newInstance(accountId: String) = EmojiBottomSheet().apply {
            arguments = Bundle().apply {
                putString("tt", accountId)
            }
        }
    }

    override fun getTheme(): Int = R.style.Theme_NoWiredStrapInNavigationBar

    private fun getAccountId(): String =
        requireArguments().getString("tt")!!


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

        if (savedInstanceState != null) {
            val emoji = savedInstanceState.getString("emo")
            if (emoji != null) binding.avatarBackground.text = emoji
            color = savedInstanceState.getInt("color")
            binding.avatarBackground.setBackgroundResource(color)
            binding.profileImage.setBackgroundResource(color)
        } else color = MessageChanger.color

     val toggledView =   when(color) {
         R.color.blue_100 -> binding.blueTint
         R.color.green_100 -> binding.greenTint
         R.color.orange_100 -> binding.orangeTint
         R.color.red_100 -> binding.redTint
         R.color.indigo_100 -> binding.indigoTint
         R.color.purple_100 -> binding.purpleTint
         R.color.lime_100 -> binding.limeTint
         R.color.pink_100 -> binding.pinkTint
         R.color.amber_100 -> binding.amberTint

         else -> binding.blueTint
     }

         toggles[toggledView]?.isVisible = true

        palette.forEach { mapElem ->
            mapElem.key.setOnClickListener {
                color = mapElem.value
                MessageChanger.color = mapElem.value
                binding.avatarBackground.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), mapElem.value)
                )
                for (t in toggles) {
                    t.value.isVisible = false
                }
                toggles[mapElem.key]?.isVisible = true
            }
        }

        with(binding) {

            avatarBackground.setOnClickListener {
                dismiss()
                EmojiKeyBottomSheet.newInstance(getAccountId()).show(parentFragmentManager, null)
            }
            imEditSmile.setOnClickListener {
                dismiss()
                EmojiKeyBottomSheet.newInstance(getAccountId()).show(parentFragmentManager, null)
            }
            saveButton.setOnClickListener {
                val bitmapi = viewModel.getBitmapFromView(requireContext(), avatarBackground)
                binding.profileImage.setImageBitmap(bitmapi)
                val bitmap = (binding.profileImage.drawable as BitmapDrawable).bitmap
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)

                val bytesArray = stream.toByteArray()
                val file =
                    File(RealmInitializer.filesDir, "fileName + ${System.currentTimeMillis()}")
                FileOutputStream(file).use {
                    it.write(bytesArray)
                }
                val avatarUri = Uri.fromFile(file)

                accountViewModel.saveAvatar(getAccountId(), avatarUri.toString())
                dismiss()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(AppConstants.REQUEST_EMOJI_KEY) { _, result ->
            result.getString(AppConstants.RESPONSE_EMOJI_KEY)?.let { emoji ->
                binding.avatarBackground.text = emoji
                binding.avatarBackground.setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        color
                    )
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("emo", binding.avatarBackground.text.toString())
        outState.putInt("color", color)
    }

}
