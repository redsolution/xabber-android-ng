package com.xabber.presentation.application.dialogs

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.fonts.FontFamily
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.xabber.R


class NotificationsFragment : DialogFragment() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Create the root layout
        val rootLayout = LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL

        }
        // Add AppBarLayout with Toolbar
        val appBarLayout = createAppBarLayout()
        rootLayout.addView(appBarLayout)



        // Add Private Chats Switch
        val privateChatsLayout = createSwitchLayout(
            "Private Chats",
            "notifications_private_chats"
        )
        rootLayout.addView(privateChatsLayout)

        // Add Groups Switch
        val groupsLayout = createSwitchLayout(
            "Groups",
            "notifications_groups"
        )
        rootLayout.addView(groupsLayout)

        // Add Channels Switch
        val channelsLayout = createSwitchLayout(
            "Channels",
            "notifications_channels"
        )
        rootLayout.addView(channelsLayout)

        return rootLayout
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun createSwitchLayout(label: String, preferenceKey: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16) // Add margin between items
            }
            orientation = LinearLayout.HORIZONTAL

            // Add TextView for the label
            val textView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f // Weight
                )
                text = label
                textSize = 16f

            }
            addView(textView)

            // Add Switch
            val switch = Switch(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isChecked = sharedPreferences.getBoolean(preferenceKey, true) // Load saved preference
                setOnCheckedChangeListener { _, isChecked ->
                    savePreference(preferenceKey, isChecked)
                }
            }
            addView(switch)
        }
    }

    private fun savePreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }


    private fun createAppBarLayout(): AppBarLayout {
        return AppBarLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Create MaterialToolbar
            val toolbar = MaterialToolbar(requireContext()).apply {
                layoutParams = AppBarLayout.LayoutParams(
                    AppBarLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.avatar_bottom_sheet_item_height)
                )
                setTitleTextColor(Color.WHITE) // Set title text color
            }

            // Add Back Button (ImageView)
            val backButton = ImageView(requireContext()).apply {
                layoutParams = Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Toolbar.LayoutParams.WRAP_CONTENT
                ).apply {
                    setPadding(16, 16, 16, 16) // Add padding
                }
                setImageResource(R.drawable.ic_arrow_left_white) // Set icon
                setOnClickListener {
                    dismiss() // Close the dialog when the back button is clicked
                }
            }
            toolbar.addView(backButton)

            // Add Title (TextView)
            val title = TextView(requireContext()).apply {
                layoutParams = Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Toolbar.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                text = "Notification Settings" // Set title
                setTextAppearance(R.style.ApplicationToolbarTitle) // Apply style
            }
            toolbar.addView(title)

            addView(toolbar)
        }
    }


    override fun onStart() {
        super.onStart()
        // Set dialog width and height
        val dialog = dialog
        if (dialog != null) {
            val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
            val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
            dialog.window?.setLayout(width, height)
            dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
        }
    }


}