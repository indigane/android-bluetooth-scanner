package home.bluetooth_scanner

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class BleDeviceAdapter : ListAdapter<BleDevice, BleDeviceAdapter.BleDeviceViewHolder>(BleDeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return BleDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BleDeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
    }

    class BleDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceNameTextView: TextView = itemView.findViewById(R.id.deviceName)
        private val deviceAddressTextView: TextView = itemView.findViewById(R.id.deviceAddress)
        private val deviceRssiTextView: TextView = itemView.findViewById(R.id.deviceRssi)

        fun bind(device: BleDevice) {
            Log.d("BleDeviceViewHolder", "Binding device: ${device.name}, Address: ${device.address}, Smoothed RSSI: ${device.smoothedRssi}")
            deviceNameTextView.text = device.name ?: "Unknown Device"
            deviceAddressTextView.text = device.address
            deviceRssiTextView.text = String.format("RSSI: %.2f dBm", device.smoothedRssi)
        }
    }
}

class BleDeviceDiffCallback : DiffUtil.ItemCallback<BleDevice>() {
    override fun areItemsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
        return oldItem.address == newItem.address // Address is unique identifier
    }

    override fun areContentsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
        // Compare relevant fields that might change and require UI update
        return oldItem.name == newItem.name &&
               oldItem.smoothedRssi == newItem.smoothedRssi &&
               oldItem.currentRssi == newItem.currentRssi
    }
}
