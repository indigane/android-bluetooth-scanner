package home.bluetooth_scanner

import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityScrollTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.devicesRecyclerView)
            // Ensure animations are off for stable tests
            (recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun createBleDevice(address: String, name: String?, rssi: Int): BleDevice {
        // Name can be null for some devices
        return BleDevice.create(address, name, rssi)
    }

    @Test
    fun testListScrollsToTopWhenNewStrongerDeviceAppearsAndListWasAtTop() {
        val latch = CountDownLatch(2) // One for first list, one for second
        val newStrongerDevice = createBleDevice("NEW_STRONG_DEVICE", "Stronger", -50)
        val originalTopDevice = createBleDevice("ORIGINAL_TOP", "OriginalTop", -60)
        val otherDevice = createBleDevice("OTHER_DEVICE", "Other", -70)

        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.devicesRecyclerView)
            val adapter = recyclerView.adapter as BleDeviceAdapter
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager

            // 1. Initial state: list is at the top, originalTopDevice is at the top.
            activity.runOnUiThread {
                val initialList = listOf(originalTopDevice, otherDevice)
                adapter.submitList(initialList) {
                    recyclerView.post { // Ensure layout pass and scroll positioning
                        layoutManager.scrollToPositionWithOffset(0, 0)
                        Log.d("TestScroll", "Initial list submitted and scrolled to top. Top: ${adapter.currentList.getOrNull(0)?.address}")

                        // Set the tracked device *after* initial list is stable and scrolled
                        activity.setTopVisibleDeviceBeforeSortUpdateForTest(originalTopDevice)
                        Log.d("TestScroll", "Set topVisibleDeviceBeforeSortUpdate to: ${originalTopDevice.address}")
                        latch.countDown() // Initial setup complete

                        // 2. Simulate scan result: newStrongerDevice comes in
                        val updatedDeviceList = mutableListOf(newStrongerDevice, originalTopDevice, otherDevice)
                        updatedDeviceList.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

                        // Update the activity's discoveredDevices list (as ScanCallback would)
                        activity.getDiscoveredDevicesListForTest().clear()
                        activity.getDiscoveredDevicesListForTest().addAll(updatedDeviceList)

                        Log.d("TestScroll", "Submitting updated list. Expected new top: ${updatedDeviceList[0].address}")
                        adapter.submitList(updatedDeviceList.toList()) { // Pass a new list copy
                            recyclerView.post { // Ensure UI update from submitList is processed
                                Log.d("TestScroll", "Second list submitted. Current adapter top: ${adapter.currentList.getOrNull(0)?.address}")
                                latch.countDown() // Second update complete
                            }
                        }
                    }
                }
            }

        assert(latch.await(15, TimeUnit.SECONDS)) { "Timeout waiting for UI operations" }

        // 3. Verification
        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.devicesRecyclerView)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            val currentAdapterList = (recyclerView.adapter as BleDeviceAdapter).currentList

            Log.d("TestScroll", "Verification: First completely visible item position: $firstVisiblePosition")
            Log.d("TestScroll", "Verification: Adapter list top: ${currentAdapterList.getOrNull(0)?.address}")

            assert(firstVisiblePosition == 0) {
                "RecyclerView should be scrolled to the top (position 0). Actual: $firstVisiblePosition. Adapter top: ${currentAdapterList.getOrNull(0)?.address}"
            }
            assert(currentAdapterList.getOrNull(0)?.address == newStrongerDevice.address) {
                "The new stronger device should be at the top of the adapter list. Actual: ${currentAdapterList.getOrNull(0)?.address}"
            }
        }
    }

    @Test
    fun testListDoesNotScrollIfItWasNotAtTop() {
        val latch = CountDownLatch(2)
        val newStrongerDevice = createBleDevice("NEW_STRONG_DEVICE_2", "Stronger2", -40)
        val initialTopDeviceInList = createBleDevice("INITIAL_TOP_2", "InitialTop2", -60)
        val originalVisibleDevice = createBleDevice("ORIGINAL_VISIBLE_2", "OriginalVisible2", -70) // This will be scrolled to
        val anotherDevice = createBleDevice("ANOTHER_2", "Another2", -80)

        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.devicesRecyclerView)
            val adapter = recyclerView.adapter as BleDeviceAdapter
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager

            // 1. Initial state: list is NOT scrolled to top. Scroll to make originalVisibleDevice the first visible.
            val initialList = listOf(initialTopDeviceInList, originalVisibleDevice, anotherDevice)
            activity.runOnUiThread {
                adapter.submitList(initialList) {
                    recyclerView.post {
                        layoutManager.scrollToPositionWithOffset(1, 0) // Scroll to index 1
                        val firstVisibleAfterScroll = layoutManager.findFirstVisibleItemPosition()
                        Log.d("TestScrollNoAdjust", "Initial list. Scrolled to index 1. First visible: $firstVisibleAfterScroll")

                        // topVisibleDeviceBeforeSortUpdate should NOT be set by MainActivity's logic
                        // because findFirstCompletelyVisibleItemPosition will not be 0.
                        activity.setTopVisibleDeviceBeforeSortUpdateForTest(null)
                        Log.d("TestScrollNoAdjust", "Set topVisibleDeviceBeforeSortUpdate to null")
                        latch.countDown()

                        // 2. Simulate scan result
                        val updatedDeviceList = mutableListOf(newStrongerDevice, initialTopDeviceInList, originalVisibleDevice, anotherDevice)
                        updatedDeviceList.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

                        activity.getDiscoveredDevicesListForTest().clear()
                        activity.getDiscoveredDevicesListForTest().addAll(updatedDeviceList)

                        Log.d("TestScrollNoAdjust", "Submitting updated list. Expected new top: ${updatedDeviceList[0].address}")
                        adapter.submitList(updatedDeviceList.toList()) {
                            recyclerView.post {
                                Log.d("TestScrollNoAdjust", "Second list submitted. First visible: ${layoutManager.findFirstVisibleItemPosition()}")
                                latch.countDown()
                            }
                        }
                    }
                }
            }

            assert(latch.await(15, TimeUnit.SECONDS)) { "Timeout waiting for UI operations" }

            // 3. Verification
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.devicesRecyclerView)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()

                Log.d("TestScrollNoAdjust", "Verification: First visible item position: $firstVisiblePosition")
                Log.d("TestScrollNoAdjust", "Verification: activity.getTopVisibleDeviceBeforeSortUpdateForTest() is ${activity.getTopVisibleDeviceBeforeSortUpdateForTest()?.address}")

                assert(activity.getTopVisibleDeviceBeforeSortUpdateForTest() == null) {
                    "topVisibleDeviceBeforeSortUpdate should be null as scroll adjustment should not have run to force scroll to top."
                }
            }
        }
    }
}
