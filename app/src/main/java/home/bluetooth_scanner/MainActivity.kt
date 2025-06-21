package home.bluetooth_scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.recyclerview.widget.SimpleItemAnimator
// import androidx.annotation.VisibleForTesting // Uncomment if you have the annotation dependency

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val discoveredDevices = mutableListOf<BleDevice>()
    private lateinit var bleDeviceAdapter: BleDeviceAdapter
    private var topVisibleDeviceBeforeSortUpdate: BleDevice? = null

    private lateinit var nearbyDevicesPermissionText: TextView
    private lateinit var requestNearbyDevicesPermissionButton: Button
    private lateinit var locationPermissionText: TextView
    private lateinit var requestLocationPermissionButton: Button
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var permissionRequestContainer: View
    private lateinit var dataContainer: View
    private lateinit var noDevicesTextView: TextView


    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            this@MainActivity.topVisibleDeviceBeforeSortUpdate = null
            super.onScanResult(callbackType, result)
            Log.d("ScanCallback", "onScanResult triggered.")
            if (result == null) {
                Log.w("ScanCallback", "onScanResult: Received null ScanResult.")
                return
            }

            // Log detailed information from the ScanResult object
            Log.i("ScanCallback", "ScanResult: " +
                    "Device Name: ${result.device?.name ?: "N/A"}, " +
                    "Address: ${result.device?.address ?: "N/A"}, " +
                    "RSSI: ${result.rssi}, " +
                    "isConnectable: ${result.isConnectable}") // Removed ScanRecord

            val deviceAddress = result.device?.address
            if (deviceAddress == null) {
                Log.w("ScanCallback", "onScanResult: Device address is null. Skipping.")
                return
            }

            val deviceName = try {
                result.device?.name
            } catch (e: SecurityException) {
                Log.e("ScanCallback", "Failed to get device name due to SecurityException: ${e.message}")
                "Unknown (Permission Issue)"
            }
            val rssi = result.rssi

            val existingDevice = discoveredDevices.find { it.address == deviceAddress }

            if (existingDevice != null) {
                val updatedDevice = existingDevice.withNewRssiReading(rssi)
                val index = discoveredDevices.indexOf(existingDevice)
                if (index != -1) {
                    discoveredDevices[index] = updatedDevice
                }
                Log.d("ScanCallback", "Updated device: ${updatedDevice.name ?: "Unknown"}, Address: ${updatedDevice.address}, RSSI: ${updatedDevice.currentRssi}, Smoothed RSSI: ${updatedDevice.smoothedRssi}")
            } else {
                val newDevice = BleDevice.create(
                    address = deviceAddress,
                    name = deviceName,
                    initialRssi = rssi
                )
                discoveredDevices.add(newDevice)
                Log.d("ScanCallback", "New device: ${newDevice.name ?: "Unknown"}, Address: ${newDevice.address}, RSSI: ${newDevice.currentRssi}, Smoothed RSSI: ${newDevice.smoothedRssi}")
            }

            // Ensure UI updates and list modifications for the adapter are on the main thread
            runOnUiThread {
                Log.d("ScanCallback", "Inside runOnUiThread: Processing devices for UI update.")

                val layoutManager = (devicesRecyclerView.layoutManager as LinearLayoutManager)
                val firstCompletelyVisibleItemPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (firstCompletelyVisibleItemPosition == 0 && bleDeviceAdapter.currentList.isNotEmpty()) {
                    // Check if currentList is not empty to avoid IndexOutOfBoundsException
                    val currentTopDevice = bleDeviceAdapter.currentList.getOrNull(0)
                    if (currentTopDevice != null) {
                        this@MainActivity.topVisibleDeviceBeforeSortUpdate = currentTopDevice
                        Log.d("MainActivity", "Stored topVisibleDeviceBeforeSortUpdate: ${currentTopDevice.address}")
                    } else {
                        Log.d("MainActivity", "bleDeviceAdapter.currentList was empty or item at 0 was null, not storing topVisibleDeviceBeforeSortUpdate.")
                    }
                } else {
                    // Not at the top or list is empty, ensure it's null
                    this@MainActivity.topVisibleDeviceBeforeSortUpdate = null
                    if (firstCompletelyVisibleItemPosition != 0) {
                        Log.d("MainActivity", "Not scrolled to top (pos: $firstCompletelyVisibleItemPosition), not storing topVisibleDeviceBeforeSortUpdate.")
                    } else {
                        Log.d("MainActivity", "Scrolled to top but adapter list is empty, not storing topVisibleDeviceBeforeSortUpdate.")
                    }
                }
                // Sort the list first
                discoveredDevices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

                // Submit the sorted list to the adapter
                bleDeviceAdapter.submitList(discoveredDevices.toList())
                Log.d("ScanCallback", "Adapter updated. Item count: ${bleDeviceAdapter.itemCount}")
                if (permissionRequestContainer.visibility == View.GONE) { // Only update if permissions are granted
                    updateNoDevicesMessageVisibility()
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.d("ScanCallback", "onBatchScanResults triggered.")
            if (results == null || results.isEmpty()) {
                Log.w("ScanCallback", "onBatchScanResults: Received null or empty results list.")
                return
            }
            Log.i("ScanCallback", "Batch Scan Results: ${results.size} devices found.")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("ScanCallback", "onScanFailed triggered. Error Code: $errorCode")
            val errorMessage = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started."
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed."
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error."
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported."
                5 -> "Out of hardware resources (SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES)." // API 26
                6 -> "Scanning too frequently (SCAN_FAILED_SCANNING_TOO_FREQUENTLY)." // API 30
                else -> "Unknown scan failure ($errorCode)."
            }
            Log.e("ScanCallback", "Scan failed: $errorMessage")
        }
    }

    private val NEARBY_DEVICES_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
            // ACCESS_FINE_LOCATION is requested separately now
        )
    }

    private val LOCATION_PERMISSION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasNearbyDevicesPermission(): Boolean {
        return NEARBY_DEVICES_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasLocationPermission(): Boolean {
        return LOCATION_PERMISSION.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updatePermissionUI() {
        val nearbyGranted = hasNearbyDevicesPermission()
        val locationGranted = hasLocationPermission()

        nearbyDevicesPermissionText.visibility = if (nearbyGranted) View.GONE else View.VISIBLE
        requestNearbyDevicesPermissionButton.visibility = if (nearbyGranted) View.GONE else View.VISIBLE

        locationPermissionText.visibility = if (locationGranted) View.GONE else View.VISIBLE
        requestLocationPermissionButton.visibility = if (locationGranted) View.GONE else View.VISIBLE

        if (nearbyGranted && locationGranted) {
            permissionRequestContainer.visibility = View.GONE
            dataContainer.visibility = View.VISIBLE
            updateNoDevicesMessageVisibility() // Update based on current discoveredDevices
            checkBluetoothStateAndStartScan()
        } else {
            permissionRequestContainer.visibility = View.VISIBLE
            dataContainer.visibility = View.GONE
            // Stop scan if it's running and permissions are revoked
            if (bluetoothLeScanner != null && (bluetoothAdapter?.isEnabled == true)) {
                 // Check for BLUETOOTH_SCAN permission before stopping, though it might not be strictly necessary for stop if already started.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        stopBleScan()
                    } else {
                        Log.w("MainActivity", "BLUETOOTH_SCAN not granted, cannot definitively stop scan if it was running with it.")
                    }
                } else {
                     // For older versions, no specific separate permission needed to stop.
                    stopBleScan()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nearbyDevicesPermissionText = findViewById(R.id.nearbyDevicesPermissionText)
        requestNearbyDevicesPermissionButton = findViewById(R.id.requestNearbyDevicesPermissionButton)
        locationPermissionText = findViewById(R.id.locationPermissionText)
        requestLocationPermissionButton = findViewById(R.id.requestLocationPermissionButton)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        permissionRequestContainer = findViewById(R.id.permissionRequestContainer)
        dataContainer = findViewById(R.id.dataContainer)
        noDevicesTextView = findViewById(R.id.noDevicesTextView)

        bleDeviceAdapter = BleDeviceAdapter()
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        devicesRecyclerView.adapter = bleDeviceAdapter
        (devicesRecyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        val scrollObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                handleScrollAdjustment()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                handleScrollAdjustment()
            }
        }
        bleDeviceAdapter.registerAdapterDataObserver(scrollObserver)

        requestNearbyDevicesPermissionButton.setOnClickListener {
            ActivityCompat.requestPermissions(this, NEARBY_DEVICES_PERMISSIONS, REQUEST_NEARBY_DEVICES_PERMISSIONS)
        }

        requestLocationPermissionButton.setOnClickListener {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, REQUEST_LOCATION_PERMISSION)
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e("MainActivity", "BluetoothAdapter not available on this device.")
            // Potentially show a message to the user, disable UI elements that need BT
            nearbyDevicesPermissionText.text = "Bluetooth is not available on this device."
            locationPermissionText.visibility = View.GONE // Hide location if BT itself is not available.
            requestNearbyDevicesPermissionButton.visibility = View.GONE
            requestLocationPermissionButton.visibility = View.GONE
            return
        }

        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        updatePermissionUI() // Initial UI setup based on current permissions
    }

    private fun checkBluetoothStateAndStartScan() {
        if (!hasNearbyDevicesPermission() || !hasLocationPermission()) {
            Log.w("MainActivity", "Attempted to check Bluetooth state without all required permissions.")
            updatePermissionUI() // Ensure UI reflects missing permissions
            return
        }

        if (bluetoothAdapter == null) {
            Log.e("MainActivity", "BluetoothAdapter not initialized")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Log.i("MainActivity", "Bluetooth is not enabled. Requesting user to enable it.")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // BLUETOOTH_CONNECT is part of NEARBY_DEVICES_PERMISSIONS, which should be granted if we reach here.
                // However, a direct check before startActivityForResult is good practice.
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    Log.e("MainActivity", "BLUETOOTH_CONNECT permission not granted, cannot request to enable Bluetooth. This should not happen if hasNearbyDevicesPermission() passed.")
                    // This state indicates a logic flaw or permission being revoked between checks.
                    // Update UI to re-prompt for nearby devices permissions.
                    updatePermissionUI()
                }
            } else {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            Log.d("MainActivity", "Bluetooth is enabled. Starting scan.")
            startBleScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d("MainActivity", "Bluetooth has been enabled by the user.")
                startBleScan() // Permissions should already be granted if we reach here
            } else {
                Log.w("MainActivity", "User did not enable Bluetooth or cancelled the request.")
                // Optionally, show a message that Bluetooth is needed. The UI should still reflect permission status.
                // updatePermissionUI() // Could call this to ensure UI is consistent, though not strictly necessary if only BT enable was cancelled.
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("MainActivity", "onRequestPermissionsResult: requestCode=$requestCode")
        when (requestCode) {
            REQUEST_NEARBY_DEVICES_PERMISSIONS, REQUEST_LOCATION_PERMISSION -> {
                // Even if one specific group was requested, update UI based on the overall state of both.
                updatePermissionUI()
            }
        }
    }

    private fun startBleScan() {
        Log.d("MainActivity", "Attempting to start BLE scan...")
        if (!hasNearbyDevicesPermission() || !hasLocationPermission()) {
            Log.w("MainActivity", "Attempted to start scan without all required permissions.")
            updatePermissionUI() // Update UI to show missing permission prompts.
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w("MainActivity", "Bluetooth adapter not available or not enabled. Cannot start scan.")
            // Call checkBluetoothStateAndStartScan() to re-trigger enable flow if appropriate,
            // but ensure it doesn't create a loop if permissions are the issue.
            // updatePermissionUI() will typically handle prompting if permissions are missing.
            // If BT is just disabled, checkBluetoothStateAndStartScan would handle the enable intent.
            // For now, simply returning as checkBluetoothStateAndStartScan should be the entry point.
            return
        }

        // Specific permission checks before scan (BLUETOOTH_SCAN for S+)
        // These should be covered by hasNearbyDevicesPermission, but being explicit can help debugging.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e("MainActivity", "BLUETOOTH_SCAN permission not granted. Cannot start scan. This indicates an issue with permission state.")
                updatePermissionUI() // Should show the prompt for nearby devices.
                return
            }
        }
        // For older versions, ACCESS_FINE_LOCATION is crucial for scan results.
        // This is covered by hasLocationPermission().

        Log.i("MainActivity", "Starting BLE scan.")
        discoveredDevices.clear()
        bleDeviceAdapter.submitList(emptyList()) // Clear adapter
        if (permissionRequestContainer.visibility == View.GONE) { // Only update if permissions are granted
            updateNoDevicesMessageVisibility() // Show "no devices" message
        }
        bluetoothLeScanner?.startScan(leScanCallback)
    }

    private fun stopBleScan() {
        Log.d("MainActivity", "Attempting to stop BLE scan...")
        if (bluetoothAdapter == null ) { // Removed !bluetoothAdapter!!.isEnabled check as we might want to stop even if BT was disabled after scan started.
             Log.w("MainActivity", "Bluetooth adapter not available. Cannot stop scan.")
             return
        }
        // Check BLUETOOTH_SCAN permission before stopping scan (API 31+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("MainActivity", "BLUETOOTH_SCAN permission not granted. May not be able to stop scan effectively if it requires this permission to interact with the scanner.")
                // Depending on Android version and specific device behavior, stopScan might still work or fail silently.
            }
        }
        // No specific permission usually required to stop for older versions if it was started.
        bluetoothLeScanner?.stopScan(leScanCallback)
        Log.i("MainActivity", "BLE scan stopped.")
        if (permissionRequestContainer.visibility == View.GONE) { // Only update if permissions are granted
             updateNoDevicesMessageVisibility()
        }
    }

    override fun onResume() {
        super.onResume()
        // When the app resumes, permissions might have changed from settings.
        // Re-evaluate and update the UI accordingly.
        if (bluetoothAdapter != null) { // Only if BT adapter exists
             updatePermissionUI() // This will also call updateNoDevicesMessageVisibility if needed
        }
    }

    private fun updateNoDevicesMessageVisibility() {
        // This function assumes dataContainer is already VISIBLE (i.e., permissions granted)
        if (discoveredDevices.isEmpty()) {
            noDevicesTextView.visibility = View.VISIBLE
            devicesRecyclerView.visibility = View.GONE
        } else {
            noDevicesTextView.visibility = View.GONE
            devicesRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun handleScrollAdjustment() {
        val layoutManager = (devicesRecyclerView.layoutManager as LinearLayoutManager)
        val currentTopDevice = this@MainActivity.topVisibleDeviceBeforeSortUpdate

        if (currentTopDevice != null) {
            val newIndexOfTrackedDevice = bleDeviceAdapter.currentList.indexOfFirst { it.address == currentTopDevice.address }

            if (newIndexOfTrackedDevice != -1 && newIndexOfTrackedDevice > 0) {
                Log.d("MainActivity", "Adjusting scroll. Tracked device ${currentTopDevice.address} moved to $newIndexOfTrackedDevice. Scrolling to top.")
                devicesRecyclerView.scrollToPosition(0)
            } else if (newIndexOfTrackedDevice == 0) {
                 Log.d("MainActivity", "No scroll adjustment needed. Tracked device ${currentTopDevice.address} is still at the top.")
            } else {
                 Log.d("MainActivity", "No scroll adjustment. Tracked device ${currentTopDevice.address} not found in the new list or list is empty.")
            }
            this@MainActivity.topVisibleDeviceBeforeSortUpdate = null
        }
    }

    fun getDiscoveredDevicesListForTest(): MutableList<BleDevice> {
        return discoveredDevices
    }

    fun setTopVisibleDeviceBeforeSortUpdateForTest(device: BleDevice?) {
        this.topVisibleDeviceBeforeSortUpdate = device
    }

    fun getTopVisibleDeviceBeforeSortUpdateForTest(): BleDevice? {
        return this.topVisibleDeviceBeforeSortUpdate
    }

    companion object {
        // Removed: private const val REQUEST_PERMISSIONS = 1 (No longer used for combined request)
        private const val REQUEST_ENABLE_BT = 2
        private const val REQUEST_NEARBY_DEVICES_PERMISSIONS = 101
        private const val REQUEST_LOCATION_PERMISSION = 102
    }
}
