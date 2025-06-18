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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.content.Intent
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val discoveredDevices = mutableListOf<BleDevice>()
    private lateinit var bleDeviceAdapter: BleDeviceAdapter
    private lateinit var debugTextView: TextView

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
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
                    "isConnectable: ${result.isConnectable}, " + // isConnectable requires API 26 for ScanResult
                    "ScanRecord: ${result.scanRecord?.bytes?.joinToString(":") { String.format("%02X", it) } ?: "N/A"}")

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
                existingDevice.addRssiReading(rssi)
                // Log.d for MainActivity is fine here, or use "ScanCallback" tag for consistency
                Log.d("ScanCallback", "Updated device: ${existingDevice.name ?: "Unknown"}, Address: ${existingDevice.address}, RSSI: ${existingDevice.currentRssi}, Smoothed RSSI: ${existingDevice.smoothedRssi}")
            } else {
                val newDevice = BleDevice(
                    address = deviceAddress,
                    name = deviceName,
                    currentRssi = rssi
                )
                discoveredDevices.add(newDevice)
                Log.d("ScanCallback", "New device: ${newDevice.name ?: "Unknown"}, Address: ${newDevice.address}, RSSI: ${newDevice.currentRssi}, Smoothed RSSI: ${newDevice.smoothedRssi}")
            }

            // Ensure UI updates and list modifications for the adapter are on the main thread
            runOnUiThread {
                Log.d("ScanCallback", "Inside runOnUiThread: Processing devices for UI update.") // Corrected log message

                // Sort the list first
                discoveredDevices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

                // Update debug text with the top device after sorting
                if (discoveredDevices.isNotEmpty()) {
                    val topDevice = discoveredDevices[0]
                    val debugText = "Top Device: ${topDevice.name ?: "Unknown"} (${topDevice.address}) | Smoothed RSSI: ${String.format("%.2f", topDevice.smoothedRssi)}"
                    debugTextView.text = debugText
                    Log.d("MainActivityDebug", "DebugTextView updated with top device: $debugText")
                } else {
                    debugTextView.text = "Debug: No devices yet..."
                    Log.d("MainActivityDebug", "DebugTextView: No devices yet.")
                }

                // Submit the sorted list to the adapter
                bleDeviceAdapter.submitList(discoveredDevices.toList())
                Log.d("ScanCallback", "Adapter updated. Item count: ${bleDeviceAdapter.itemCount}")
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
            for (result in results) {
                // Log summary for each result in batch, similar to onScanResult
                Log.i("ScanCallback", "  Batch Result - Device: ${result.device?.name ?: "N/A"}, Address: ${result.device?.address ?: "N/A"}, RSSI: ${result.rssi}")
                // TODO: Process each result if batching is actively used and devices need individual handling
                // For now, if processing is added, ensure thread safety for discoveredDevices list.
            }
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

    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun hasPermissions(): Boolean {
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        debugTextView = findViewById(R.id.debugTextView) // Initialize debugTextView
        bleDeviceAdapter = BleDeviceAdapter()
        val recyclerView: RecyclerView = findViewById(R.id.devicesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = bleDeviceAdapter

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter // Initialize class member

        if (bluetoothAdapter == null) {
            Log.e("MainActivity", "BluetoothAdapter not available on this device.")
            // Handle this case: inform user, disable BLE features
            return // Cannot proceed with BLE setup
        }

        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner

        if (hasPermissions()) {
            Log.d("MainActivity", "Permissions already granted on create.")
            checkBluetoothStateAndStartScan()
        } else {
            Log.i("MainActivity", "Requesting permissions on create.")
            requestPermissions()
        }
    }

    private fun checkBluetoothStateAndStartScan() {
        if (bluetoothAdapter == null) {
            Log.e("MainActivity", "BluetoothAdapter not initialized")
            // Potentially show a toast or dialog to the user
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Log.i("MainActivity", "Bluetooth is not enabled. Requesting user to enable it.")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            // Check if BLUETOOTH_CONNECT permission is granted before starting activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    Log.e("MainActivity", "BLUETOOTH_CONNECT permission not granted, cannot request to enable Bluetooth.")
                    // Optionally inform the user they need to grant BLUETOOTH_CONNECT or enable BT manually
                }
            } else {
                // For older APIs, no specific BLUETOOTH_CONNECT permission needed for this action
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
                startBleScan()
            } else {
                Log.w("MainActivity", "User did not enable Bluetooth or cancelled the request.")
                // Optionally, show a message to the user that Bluetooth is required.
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("MainActivity", "All permissions granted by user.")
                checkBluetoothStateAndStartScan()
            } else {
                Log.w("MainActivity", "Not all permissions were granted by user.")
                // Handle permission denial, e.g., show an explanation to the user.
            }
        }
    }

    private fun startBleScan() {
        Log.d("MainActivity", "Attempting to start BLE scan...")
        if (!hasPermissions()) { // Double check permissions
            Log.w("MainActivity", "Attempted to start scan without all permissions.")
            requestPermissions() // Or inform user
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w("MainActivity", "Bluetooth adapter not available or not enabled. Cannot start scan.")
            // Optionally call checkBluetoothStateAndStartScan() again or inform user
            return
        }

        // Check BLUETOOTH_SCAN permission before starting scan (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e("MainActivity", "BLUETOOTH_SCAN permission not granted. Cannot start scan.")
                // This should ideally be caught by hasPermissions(), but good for defense.
                return
            }
        }

        Log.i("MainActivity", "Starting BLE scan.")
        // Consider adding scan settings or filters if needed, for now, default scan
        bluetoothLeScanner?.startScan(leScanCallback)
        // Add a state variable like isScanning = true if you want to manage UI based on scanning state
    }

    private fun stopBleScan() {
        Log.d("MainActivity", "Attempting to stop BLE scan...")
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
             Log.w("MainActivity", "Bluetooth adapter not available or not enabled. Cannot stop scan.")
             return
        }
        // Check BLUETOOTH_SCAN permission before stopping scan (API 31+)
        // Note: stopScan might not strictly require SCAN permission if it was granted for start,
        // but it's good practice to log if it's missing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e("MainActivity", "BLUETOOTH_SCAN permission not granted. Cannot stop scan effectively.")
            }
        }
        bluetoothLeScanner?.stopScan(leScanCallback)
        Log.i("MainActivity", "BLE scan stopped.")
        // isScanning = false
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        private const val REQUEST_ENABLE_BT = 2
    }
}
