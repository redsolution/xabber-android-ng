package com.xabber.presentation.onboarding.util

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.xabber.R
import com.xabber.account.ConnectionStep

class ConnectionProgressDialog : DialogFragment() {

    private lateinit var progressResolving: ProgressBar
    private lateinit var checkResolving: ImageView
    private lateinit var progressConnecting: ProgressBar
    private lateinit var checkConnecting: ImageView
    private lateinit var progressAuthenticating: ProgressBar
    private lateinit var checkAuthenticating: ImageView
    private lateinit var progressLoadingRoster: ProgressBar
    private lateinit var checkLoadingRoster: ImageView
    private lateinit var progressSyncingMessages: ProgressBar
    private lateinit var checkSyncingMessages: ImageView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_connection_progress, null)

        progressResolving = view.findViewById(R.id.step_resolving_progress)
        checkResolving = view.findViewById(R.id.step_resolving_check)
        progressConnecting = view.findViewById(R.id.step_connecting_progress)
        checkConnecting = view.findViewById(R.id.step_connecting_check)
        progressAuthenticating = view.findViewById(R.id.step_authenticating_progress)
        checkAuthenticating = view.findViewById(R.id.step_authenticating_check)
        progressLoadingRoster = view.findViewById(R.id.step_loading_roster_progress)
        checkLoadingRoster = view.findViewById(R.id.step_loading_roster_check)
        progressSyncingMessages = view.findViewById(R.id.step_syncing_messages_progress)
        checkSyncingMessages = view.findViewById(R.id.step_syncing_messages_check)

        reset()

        view.setBackgroundResource(R.drawable.dialog_rounded_background)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        // Делаем фон окна прозрачным, чтобы скругление было видно
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        return dialog
    }

    private fun reset() {
        listOf(
            progressResolving, progressConnecting, progressAuthenticating,
            progressLoadingRoster, progressSyncingMessages
        ).forEach { it.visibility = View.GONE }

        listOf(
            checkResolving, checkConnecting, checkAuthenticating,
            checkLoadingRoster, checkSyncingMessages
        ).forEach { it.visibility = View.GONE }
    }

    private fun fadeIn(view: View) {
        val anim = AlphaAnimation(0.0f, 1.0f).apply { duration = 400 }
        view.startAnimation(anim)
        view.visibility = View.VISIBLE
    }

    fun showStepCompleted(step: ConnectionStep) {
        when (step) {
            ConnectionStep.RESOLVING -> {
                progressResolving.visibility = View.GONE
                fadeIn(checkResolving)
            }
            ConnectionStep.CONNECTING -> {
                progressConnecting.visibility = View.GONE
                fadeIn(checkConnecting)
            }
            ConnectionStep.AUTHENTICATING -> {
                progressAuthenticating.visibility = View.GONE
                fadeIn(checkAuthenticating)
            }
            ConnectionStep.LOADING_ROSTER -> {
                progressLoadingRoster.visibility = View.GONE
                fadeIn(checkLoadingRoster)
            }
            ConnectionStep.SYNCING_MESSAGES -> {
                progressSyncingMessages.visibility = View.GONE
                fadeIn(checkSyncingMessages)
            }
        }
    }

    fun complete() = dismiss()
}