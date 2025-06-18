package home.bluetooth_scanner

import org.junit.Assert.*
import org.junit.Test

// Reflect the actual RSSI_HISTORY_MAX_SIZE used in BleDevice.kt (e.g., 5)
private const val RSSI_HISTORY_MAX_SIZE_FOR_TEST = 5

class BleDeviceTest {

    @Test
    fun `initialization sets values correctly`() {
        val device = BleDevice(address = "00:11:22:33:44:55", name = "TestDevice", currentRssi = -50)
        assertEquals("00:11:22:33:44:55", device.address)
        assertEquals("TestDevice", device.name)
        assertEquals(-50, device.currentRssi)
        assertEquals(1, device.rssiHistory.size)
        assertEquals(-50, device.rssiHistory[0])
        assertEquals(-50.0, device.smoothedRssi, 0.001)
    }

    @Test
    fun `addRssiReading updates currentRssi and history`() {
        val device = BleDevice(address = "AA:BB:CC:DD:EE:FF", name = "TestRSSI", currentRssi = -60)
        // Initial state: history [-60], smoothed -60.0

        device.addRssiReading(-65)
        assertEquals(-65, device.currentRssi)
        assertEquals(2, device.rssiHistory.size)
        assertTrue(device.rssiHistory.containsAll(listOf(-60, -65)))
        assertEquals(-62.5, device.smoothedRssi, 0.001) // (-60 + -65) / 2
    }

    @Test
    fun `rssiHistory respects MAX_SIZE`() {
        val device = BleDevice(address = "11:22:33:44:55:66", name = "HistoryTest", currentRssi = -70) // History: [-70]
        // Fill up history (assuming MAX_SIZE_FOR_TEST is 5)
        device.addRssiReading(-71) // History: [-70, -71]
        device.addRssiReading(-72) // History: [-70, -71, -72]
        device.addRssiReading(-73) // History: [-70, -71, -72, -73]
        device.addRssiReading(-74) // History: [-70, -71, -72, -73, -74]
        assertEquals(RSSI_HISTORY_MAX_SIZE_FOR_TEST, device.rssiHistory.size)
        assertEquals(-74, device.currentRssi) // Latest RSSI

        // Add one more, oldest should be removed
        device.addRssiReading(-75) // History should now be [-71, -72, -73, -74, -75]
        assertEquals(RSSI_HISTORY_MAX_SIZE_FOR_TEST, device.rssiHistory.size)
        assertFalse(device.rssiHistory.contains(-70)) // -70 should be gone
        assertTrue(device.rssiHistory.contains(-75))   // -75 should be present
        assertEquals(-75, device.currentRssi)

        val expectedAverage = listOf(-71, -72, -73, -74, -75).average()
        assertEquals(expectedAverage, device.smoothedRssi, 0.001)
    }

    @Test
    fun `smoothedRssi calculates average correctly`() {
        val device = BleDevice(address = "DE:AD:BE:EF:00:00", name = "AvgTest", currentRssi = -50) // History: [-50]
        device.addRssiReading(-55) // History: [-50, -55], Avg: -52.5
        device.addRssiReading(-60) // History: [-50, -55, -60], Avg: -55.0

        assertEquals(-55.0, device.smoothedRssi, 0.001)
        assertEquals(-60, device.currentRssi)
    }

    @Test
    fun `name can be null`() {
        val device = BleDevice(address = "01:02:03:04:05:06", name = null, currentRssi = -80)
        assertNull(device.name)
        assertEquals(-80, device.currentRssi)
        assertEquals(-80.0, device.smoothedRssi, 0.001)
    }
}
