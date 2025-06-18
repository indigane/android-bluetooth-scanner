package home.bluetooth_scanner

private const val RSSI_HISTORY_MAX_SIZE = 5 // Decided value for history size

data class BleDevice(
    val address: String,
    var name: String?,
    var currentRssi: Int, // This will be the latest raw RSSI
    // rssiHistory and smoothedRssi are initialized by addRssiReading called from init
) {
    val rssiHistory: MutableList<Int> = mutableListOf()
    var smoothedRssi: Double = currentRssi.toDouble() // Initial value before history builds

    init {
        // Initialize with the first RSSI reading
        addRssiReading(currentRssi, true)
    }

    fun addRssiReading(newRssi: Int, isInitialReading: Boolean = false) {
        if (!isInitialReading) { // Only update currentRssi if it's not the initial call from init
            this.currentRssi = newRssi
        }

        rssiHistory.add(newRssi)
        while (rssiHistory.size > RSSI_HISTORY_MAX_SIZE) {
            rssiHistory.removeAt(0)
        }
        if (rssiHistory.isNotEmpty()) {
            this.smoothedRssi = rssiHistory.average()
        } else {
            // Should ideally not happen if we always add first
            this.smoothedRssi = newRssi.toDouble()
        }
    }
}
