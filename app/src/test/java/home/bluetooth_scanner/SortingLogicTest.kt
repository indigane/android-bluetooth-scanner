package home.bluetooth_scanner

import org.junit.Assert.*
import org.junit.Test

class SortingLogicTest {

    // Helper function to create BleDevice instances easily for tests
    // In BleDevice, smoothedRssi is initialized from currentRssi via init block.
    // For testing sorting, we care about the smoothedRssi value directly.
    // We can create devices and then manually set their smoothedRssi for specific test scenarios,
    // or ensure currentRssi (which init uses for smoothedRssi) is what we want for smoothedRssi.
    // Let's make a helper that sets currentRssi which then defines the initial smoothedRssi.
    private fun createDevice(address: String, name: String?, rssiForSmoothing: Int): BleDevice {
        return BleDevice(address = address, name = name, currentRssi = rssiForSmoothing)
    }

    @Test
    fun `list sorts by smoothedRssi descending primarily`() {
        val device1 = createDevice("AA:00", "DeviceA", -50) // smoothedRssi = -50.0
        val device2 = createDevice("BB:00", "DeviceB", -70) // smoothedRssi = -70.0
        val device3 = createDevice("CC:00", "DeviceC", -60) // smoothedRssi = -60.0

        val devices = mutableListOf(device2, device1, device3) // Unsorted: B(-70), A(-50), C(-60)

        devices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

        // Expected order: A(-50), C(-60), B(-70)
        assertEquals("AA:00", devices[0].address)
        assertEquals("CC:00", devices[1].address)
        assertEquals("BB:00", devices[2].address)
    }

    @Test
    fun `list sorts by address ascending for devices with same smoothedRssi`() {
        val device1 = createDevice("AA:00", "DeviceA", -60)
        val device2 = createDevice("CC:00", "DeviceC", -50)
        val device3 = createDevice("BB:00", "DeviceB", -60)

        // device1: smoothedRssi = -60.0, address = "AA:00"
        // device2: smoothedRssi = -50.0, address = "CC:00"
        // device3: smoothedRssi = -60.0, address = "BB:00"

        val devices = mutableListOf(device1, device2, device3) // Unsorted: A(-60, AA), C(-50, CC), B(-60, BB)

        devices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

        // Expected order:
        // 1. C(-50, CC) - Highest RSSI
        // 2. A(-60, AA) - Same RSSI as B, address AA comes before BB
        // 3. B(-60, BB) - Same RSSI as A, address BB comes after AA

        assertEquals("CC:00", devices[0].address) // Device C
        assertEquals("AA:00", devices[1].address) // Device A
        assertEquals("BB:00", devices[2].address) // Device B
    }

    @Test
    fun `sorting handles empty list`() {
        val devices = mutableListOf<BleDevice>()
        devices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })
        assertTrue(devices.isEmpty())
    }

    @Test
    fun `sorting handles single element list`() {
        val device1 = createDevice("AA:00", "DeviceA", -50)
        val devices = mutableListOf(device1)
        devices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })
        assertEquals(1, devices.size)
        assertEquals("AA:00", devices[0].address)
    }

    @Test
    fun `sorting complex case with multiple same RSSIs and different addresses`() {
        val d1 = createDevice("00:00:00:00:00:01", "D1", -70)
        val d2 = createDevice("00:00:00:00:00:02", "D2", -60)
        val d3 = createDevice("00:00:00:00:00:03", "D3", -70) // Same RSSI as D1, higher address
        val d4 = createDevice("00:00:00:00:00:04", "D4", -50) // Highest RSSI
        val d5 = createDevice("00:00:00:00:00:05", "D5", -60) // Same RSSI as D2, higher address

        val devices = mutableListOf(d1, d2, d3, d4, d5) // Unsorted

        devices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

        // Expected order:
        // 1. D4 (-50)
        // 2. D2 (-60, addr 02)
        // 3. D5 (-60, addr 05)
        // 4. D1 (-70, addr 01)
        // 5. D3 (-70, addr 03)

        assertEquals("00:00:00:00:00:04", devices[0].address) // D4
        assertEquals("00:00:00:00:00:02", devices[1].address) // D2
        assertEquals("00:00:00:00:00:05", devices[2].address) // D5
        assertEquals("00:00:00:00:00:01", devices[3].address) // D1
        assertEquals("00:00:00:00:00:03", devices[4].address) // D3
    }
}
