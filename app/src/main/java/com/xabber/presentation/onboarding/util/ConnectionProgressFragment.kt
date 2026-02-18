package com.xabber.presentation.onboarding.fragments.connectionprogress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xabber.R
import com.xabber.account.AccountManager
import com.xabber.databinding.DialogConnectionProgressBinding
import com.xabber.presentation.onboarding.contract.navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionProgressFragment : Fragment() {

    private var _binding: DialogConnectionProgressBinding? = null
    private val binding get() = _binding!!

    private lateinit var stepContainers: List<View>
    private lateinit var stepProgresses: List<ProgressBar>
    private lateinit var stepChecks: List<ImageView>

    private var animationJob: Job? = null
    private var loginJob: Job? = null
    private lateinit var jid: String
    private var loginSucceeded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogConnectionProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        jid = arguments?.getString("jid") ?: return
        val username = arguments?.getString("username") ?: return
        val password = arguments?.getString("password") ?: return
        val domain = jid.substringAfter("@", "")

        binding.subtitleChecking.text = getString(R.string.checking_features, domain)

        // Back callback – enabled only after login success
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Log out the account that was just created
                lifecycleScope.launch(Dispatchers.IO) {
                    AccountManager.logout(jid)
                }
                isEnabled = false
                navigator().goBack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        initViews()
        startAnimation()
        performLogin(jid, username, password, backCallback)

        binding.btnLetsRock.setOnClickListener {
            navigator().goToApplicationActivity()
        }
    }

    private fun initViews() {
        stepContainers = listOf(
            binding.stepMessageArchiveContainer,
            binding.stepSyncContainer,
            binding.stepPushNotificationsContainer,
            binding.stepMessageEditingContainer,
            binding.stepDeviceManagementContainer,
            binding.stepPubsubContainer,
            binding.stepFileUploadContainer
        )

        stepProgresses = listOf(
            binding.stepMessageArchiveProgress,
            binding.stepSyncProgress,
            binding.stepPushNotificationsProgress,
            binding.stepMessageEditingProgress,
            binding.stepDeviceManagementProgress,
            binding.stepPubsubProgress,
            binding.stepFileUploadProgress
        )

        stepChecks = listOf(
            binding.stepMessageArchiveCheck,
            binding.stepSyncCheck,
            binding.stepPushNotificationsCheck,
            binding.stepMessageEditingCheck,
            binding.stepDeviceManagementCheck,
            binding.stepPubsubCheck,
            binding.stepFileUploadCheck
        )

        stepProgresses.forEach { it.visibility = View.GONE }
        stepChecks.forEach { it.visibility = View.GONE }

        binding.separator.visibility = View.GONE
        binding.tvMotivation.visibility = View.GONE
        binding.btnLetsRock.visibility = View.GONE
    }

    private fun fadeIn(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(400)
            .setListener(null)
            .start()
    }

    private fun showStepCompleted(index: Int) {
        if (index >= stepContainers.size) return
        stepProgresses[index].visibility = View.GONE
        fadeIn(stepChecks[index])
    }

    private fun startAnimation() {
        animationJob = lifecycleScope.launch {
            delay(100)
            for (i in stepContainers.indices) {
                fadeIn(stepContainers[i])
                delay(1000)
                showStepCompleted(i)
                if (i < stepContainers.size - 1) {
                    delay(500)
                }
            }
        }
    }

    private fun performLogin(jid: String, username: String, password: String, backCallback: OnBackPressedCallback) {
        loginJob = lifecycleScope.launch {
            val success = try {
                AccountManager.login(jid, username, password)
            } catch (e: Exception) {
                false
            }

            if (success) {
                loginSucceeded = true
                animationJob?.join()
                delay(600) // 600ms pause after the last checkmark
                fadeIn(binding.separator)
                fadeIn(binding.tvMotivation)
                fadeIn(binding.btnLetsRock)
                backCallback.isEnabled = true
            } else {
                animationJob?.cancel()
                Toast.makeText(requireContext(), "Login failed. Check credentials or try again.", Toast.LENGTH_LONG).show()
                navigator().goBack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        animationJob?.cancel()
        loginJob?.cancel()
        _binding = null
    }

    companion object {
        fun newInstance(jid: String, username: String, password: String): ConnectionProgressFragment {
            return ConnectionProgressFragment().apply {
                arguments = Bundle().apply {
                    putString("jid", jid)
                    putString("username", username)
                    putString("password", password)
                }
            }
        }
    }
}