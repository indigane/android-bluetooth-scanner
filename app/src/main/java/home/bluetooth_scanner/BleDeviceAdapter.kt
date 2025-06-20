package home.bluetooth_scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
        private val rssiStrengthBar: ProgressBar = itemView.findViewById(R.id.rssiStrengthBar)

        fun bind(device: BleDevice) {
            Log.d("BleDeviceViewHolder", "Binding device: ${device.name}, Address: ${device.address}, Smoothed RSSI: ${device.smoothedRssi}")
            deviceNameTextView.text = device.name ?: "Unknown Device"
            deviceAddressTextView.text = device.address
            deviceRssiTextView.text = String.format("%.0f dBm", device.smoothedRssi)

            itemView.setOnClickListener {
                val context = itemView.context
                val clipboardManager =
                    context.getSystemService(ClipboardManager::class.java) as ClipboardManager
                val clipData = ClipData.newPlainText("MAC Address", device.address)
                clipboardManager.setPrimaryClip(clipData)
                Toast.makeText(context, context.getString(R.string.mac_address_copied), Toast.LENGTH_SHORT)
                    .show()
            }

            // Logic for RSSI strength bar
            // Note: RSSI is typically negative. Closer to 0 is stronger.
            // So, maxRssi is numerically larger (less negative) than minRssi in terms of signal quality.
            // Let's define min_signal_rssi = -100 (weakest) and max_signal_rssi = -50 (strongest for our range)

            val actualMinRssi = -100.0 // Weakest signal for progress bar minimum
            val actualMaxRssi = -50.0  // Strongest signal for progress bar maximum

            val clampedRssi = device.smoothedRssi.coerceIn(actualMinRssi, actualMaxRssi)

            // Normalize the clamped RSSI to a 0-1 range
            // (value - min) / (max - min)
            val normalizedRssi = (clampedRssi - actualMinRssi) / (actualMaxRssi - actualMinRssi)

            // Scale to ProgressBar's range (0-100)
            val progressValue = (normalizedRssi * 100).toInt()

            rssiStrengthBar.progress = progressValue
            Log.d("BleDeviceViewHolder", "Device: ${device.name}, Smoothed RSSI: ${device.smoothedRssi}, Clamped: $clampedRssi, Normalized: $normalizedRssi, Progress: $progressValue")
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
               oldItem.smoothedRssi == newItem.smoothedRssi
    }
}
