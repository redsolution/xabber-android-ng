package com.xabber.presentation.application.fragments.settings

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemDeviceBinding
import com.xabber.dto.DeviceDto

class DevicesAdapter : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<DeviceDto>()

    fun updateDevices(newDevices: List<DeviceDto>) {
        Log.d("DevicesAdapter", "Updating with ${newDevices.size} devices: ${newDevices.map { it.uid }}")
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        Log.d("DevicesAdapter", "Binding device at position $position: ${devices[position].uid}")
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int {
        Log.d("DevicesAdapter", "Item count: ${devices.size}")
        return devices.size
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val binding = ItemDeviceBinding.bind(itemView)

        fun bind(device: DeviceDto) {
            Log.d("DevicesAdapter", "Binding device: uid=${device.uid}, name=${device.name}, model=${device.model}")
//            binding.deviceName.text = device.name
//            binding.deviceDescription.text = device.description
            binding.deviceClient.text = device.client
            binding.deviceModel.text = device.model
            binding.deviceLastAuth.text = "Last Auth: ${device.lastAuth}"
//            binding.deviceStatus.text = if (device.isExpired) "Status: Expired" else "Status: Active"
//            binding.deviceStatus.setTextColor(
//                ContextCompat.getColor(
//                    itemView.context,
//                    if (device.isExpired) R.color.red_500 else R.color.green_500
//                )
//            )
        }
    }
}