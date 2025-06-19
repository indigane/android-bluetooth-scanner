package home.bluetooth_scanner

private const val RSSI_HISTORY_MAX_SIZE = 5

data class BleDevice(
    val address: String,
    val name: String?,
    val currentRssi: Int,
    val rssiHistory: List<Int>,
    val smoothedRssi: Double
) {
    companion object {
        fun create(address: String, name: String?, initialRssi: Int): BleDevice {
            return BleDevice(
                address = address,
                name = name,
                currentRssi = initialRssi,
                rssiHistory = listOf(initialRssi),
                smoothedRssi = initialRssi.toDouble()
            )
        }
    }

    fun withNewRssiReading(newRssiValue: Int): BleDevice {
        val newHistory = (this.rssiHistory + newRssiValue).takeLast(RSSI_HISTORY_MAX_SIZE)
        val newSmoothedRssi = if (newHistory.isNotEmpty()) {
            newHistory.average()
        } else {
            // This case should ideally not be reached if rssiHistory always starts with at least one value
            newRssiValue.toDouble()
        }
        return this.copy(
            currentRssi = newRssiValue,
            rssiHistory = newHistory,
            smoothedRssi = newSmoothedRssi
            // name and address are copied from the existing object via this.copy
        )
    }
}
