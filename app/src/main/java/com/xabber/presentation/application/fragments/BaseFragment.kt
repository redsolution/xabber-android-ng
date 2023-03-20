package com.xabber.presentation.application.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.xabber.R
import com.xabber.presentation.application.BaseViewModel
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.MaskManager
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.custom.ShapeOfView

abstract class BaseFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId),
    SharedPreferences.OnSharedPreferenceChangeListener {
    val baseViewModel: BaseViewModel by viewModels()
    private var appbar: AppBarLayout? = null
    private var accountToolbar: MaterialToolbar? = null
    private var imAvatar: ImageView? = null
    private var tvInitials: TextView? = null
    private var status: ImageView? = null
    private var currentJid: String? = null
    private var shapeView: ShapeOfView? = null
    private val defaultColorKey = "blue"
    private lateinit var sh: SharedPreferences
    protected var currentColorKey = "blue"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sh = activity?.getSharedPreferences("Pref", Context.MODE_PRIVATE)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        setupAppbarPadding()
        subscribeToViewModelData()
        sh = activity?.getSharedPreferences("Pref", Context.MODE_PRIVATE)!!
        sh.registerOnSharedPreferenceChangeListener(this)
        val primaryAccount = baseViewModel.getPrimaryAccount()
        currentJid = primaryAccount?.jid
        currentColorKey = primaryAccount?.colorKey ?: "offline"
        setupColor(currentColorKey)

        shapeView?.setDrawable(MaskManager.mask)

        if (imAvatar != null) {
            if (primaryAccount != null) {
                if (primaryAccount.hasAvatar) {
                    loadAvatar(primaryAccount.id)
                } else {
                    loadAvatarWithInitials(primaryAccount.nickname, primaryAccount.colorKey)
                }
            } else {
                loadDefaultAvatar()
                status?.isVisible = false
            }
            imAvatar?.setOnClickListener {
                val account = baseViewModel.getPrimaryAccount()
                if (account != null) navigator().showAccount(account.jid)
            }
        }
        if (savedInstanceState == null) {
            baseViewModel.initPrimaryAccountListener(primaryAccount?.id)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d("mask", "pref")
        shapeView?.setDrawable(MaskManager.mask)
        //  shapeView?.requiresShapeUpdate()
    }

    private fun initViews() {
        appbar = view?.findViewById(R.id.appbar)
        accountToolbar = view?.findViewById(R.id.account_toolbar)
        imAvatar = view?.findViewById(R.id.im_avatar)
        tvInitials = view?.findViewById(R.id.tv_initials)
        status = view?.findViewById(R.id.avatar_status)
        shapeView = view?.findViewById(R.id.shape_view)
    }

    private fun setupAppbarPadding() {
        appbar?.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0)
        if (accountToolbar != null) {
            var actionBarHeight = 0
            val typedValue = TypedValue()
            if (requireActivity().theme.resolveAttribute(
                    android.R.attr.actionBarSize,
                    typedValue,
                    true
                )
            ) actionBarHeight =
                TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
            val params = CollapsingToolbarLayout.LayoutParams(
                CollapsingToolbarLayout.LayoutParams.MATCH_PARENT,
                actionBarHeight
            )
            params.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            params.topMargin = DisplayManager.getHeightStatusBar()
            accountToolbar?.layoutParams = params
        }
    }

    private fun subscribeToViewModelData() {
        baseViewModel.colorKey.observe(viewLifecycleOwner) {
            setupColor(it)
            currentColorKey = it
        }

        baseViewModel.account.observe(viewLifecycleOwner) {
            Log.d("ooo", "account.observe")
            if (it != currentJid) {
                currentJid = it
                val account = baseViewModel.getPrimaryAccount()
                if (account != null) setupColor(account.colorKey) else setupColor("offline")
                if (account == null) {
                    loadDefaultAvatar()
                    status?.isVisible = false
                } else {
                    if (account.hasAvatar)
                        loadAvatar(account.jid) else
                        loadAvatarWithInitials(
                            account.nickname, account.colorKey
                        )
                }
                baseViewModel.initAvatarListener(it)
                baseViewModel.initPrimaryAccountListener(it)

            }
        }

        baseViewModel.avatar.observe(viewLifecycleOwner) {
            Log.d("ooo", "avatar observe")
            val account = baseViewModel.getPrimaryAccount()
            if (account != null) {
                if (account.hasAvatar) loadAvatar(account.jid) else loadAvatarWithInitials(
                    account.nickname,
                    account.colorKey
                )
            } else loadDefaultAvatar()

        }
    }

    private fun setupColor(colorKey: String) {
        val colorId = ColorManager.convertColorNameToId(colorKey)
        appbar?.setBackgroundResource(colorId)
    }

    private fun loadAvatar(jid: String) {
        tvInitials?.isVisible = false
        val avatar = baseViewModel.getAvatar(jid)

        if (avatar != null) {
            imAvatar?.setImageURI(avatar.fileUri.toUri())
        }
    }

    private fun loadAvatarWithInitials(name: String, colorKey: String) {
        val color = ColorManager.convertColorLightNameToId(colorKey)
        imAvatar?.setImageResource(color)
        var initials =
            name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.reduce { acc, s -> acc + s }
        if (initials.length > 2) initials = initials.substring(0, 2)
        tvInitials?.isVisible = true
        tvInitials?.text = initials
    }

    private fun loadDefaultAvatar() {
        tvInitials?.isVisible = false
        imAvatar?.setImageResource(R.drawable.xabber_logo_80dp)
    }

    override fun onDestroy() {
        super.onDestroy()
        sh.unregisterOnSharedPreferenceChangeListener(this)
    }

}
