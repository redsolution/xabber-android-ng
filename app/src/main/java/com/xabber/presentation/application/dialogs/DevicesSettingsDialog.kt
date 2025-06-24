package com.xabber.presentation.application.dialogs

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.databinding.FragmentDevicesSettingsBinding
import com.xabber.dto.AccountDto
import com.xabber.dto.DeviceDto
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.settings.DevicesAdapter
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.toAccountDto
import com.xabber.xmpp.device.DeviceStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import java.text.SimpleDateFormat
import java.util.*

class DevicesSettingsDialog : DialogFragment() {
    private val binding by viewBinding(FragmentDevicesSettingsBinding::bind)
    private val realm = Realm.open(defaultRealmConfig())
    private var isLoggingOut: Boolean = false
    private lateinit var devicesAdapter: DevicesAdapter

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
                val width = (resources.displayMetrics.widthPixels * 0.8).toInt()
                val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER)
            }
            if (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE) {
                val width = (resources.displayMetrics.widthPixels * 0.48).toInt()
                val height = (resources.displayMetrics.heightPixels * 0.97).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_devices_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        // Initialize RecyclerView
        devicesAdapter = DevicesAdapter()
        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = devicesAdapter
            Log.d("DevicesSettingsFragment", "RecyclerView adapter set: $adapter")
        }

        // Load devices
        loadDevices()

        binding.logOutButton.setOnClickListener {
            if (!isLoggingOut) {
                isLoggingOut = true
                Log.d("DevicesSettingsFragment", "Logout button clicked")
                try {
                    navigator().logOut()
                    Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("DevicesSettingsFragment", "Logout failed: ${e.message}", e)
                    Toast.makeText(requireContext(), "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isLoggingOut = false
                }
            } else {
                Log.w("DevicesSettingsFragment", "Logout already in progress, skipping")
            }
        }

        setupToolbarColor()
    }

    private fun loadDevices() {
        val devices = getActiveDevices()
        Log.d("DevicesSettingsFragment", "Updating adapter with ${devices.size} devices: ${devices.map { it.uid }}")
        devicesAdapter.updateDevices(devices)
        binding.devicesRecyclerView.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        binding.devicesRecyclerView.postDelayed({
            Log.d("DevicesSettingsFragment", "RecyclerView item count: ${devicesAdapter.itemCount}")
        }, 1000)
    }

    private fun getActiveDevices(): List<DeviceDto> {
        val account = getPrimaryAccount()
        if (account == null) {
            Log.e("DevicesSettingsFragment", "No primary account found")
            return emptyList()
        }
        Log.d("DevicesSettingsFragment", "Querying devices for JID: ${account.jid}")
        val currentTime = System.currentTimeMillis() / 1000.0
        val devices = realm.query<DeviceStorageItem>("owner = $0", account.jid).find()
        Log.d("DevicesSettingsFragment", "Found ${devices.size} devices for JID: ${account.jid}")
        devices.forEach { device ->
            Log.d("DevicesSettingsFragment", "Device: uid=${device.uid}, name=${device.client}, model=${device.device}, expire=${device.expire}, authDate=${device.authDate}")
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return devices.map { device ->
            val isExpired = device.expire < currentTime
            val lastAuth = try {
                dateFormat.format(Date((device.authDate * 1000).toLong()))
            } catch (e: Exception) {
                Log.e("DevicesSettingsFragment", "Error formatting authDate for device ${device.uid}: ${e.message}")
                "Unknown"
            }
            DeviceDto(
                uid = device.uid,
                name = device.client.ifEmpty { device.device },
                description = device.descr,
                model = device.device,
                lastAuth = lastAuth,
                isExpired = isExpired,
                client = "Android ${Build.VERSION.SDK_INT}"
            ).also {
                Log.d("DevicesSettingsFragment", "Mapped DeviceDto: uid=${it.uid}, name=${it.name}, model=${it.model}, lastAuth=${it.lastAuth}, isExpired=${it.isExpired}")
            }
        }.sortedBy { it.lastAuth }
    }

    private fun setupToolbarColor() {
        val account = getPrimaryAccount()
        val colorKey = account?.colorKey ?: resources.getString(R.string.blue)
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        defineColor(colorRes)
    }

    private fun defineColor(colorRes: Int) {
        binding.toolbar.setBackgroundColor(
            ResourcesCompat.getColor(
                resources,
                colorRes,
                requireContext().theme
            )
        )
    }

    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(AccountStorageItem::class, "enabled = true").find()
        Log.d("DevicesSettingsFragment", "Found ${realmAccounts.size} enabled accounts")
        val primaryAccount = realmAccounts.minByOrNull { it.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
            Log.d("DevicesSettingsFragment", "Primary account JID: ${accountDto.jid}")
        } else {
            Log.e("DevicesSettingsFragment", "No primary account found")
        }
        return accountDto
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }
}