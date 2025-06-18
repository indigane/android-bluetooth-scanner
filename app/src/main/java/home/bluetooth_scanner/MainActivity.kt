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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val discoveredDevices = mutableListOf<BleDevice>()
    private lateinit var bleDeviceAdapter: BleDeviceAdapter

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                val deviceAddress = device.address
                val deviceName = try {
                    device.name // Can throw SecurityException if BLUETOOTH_CONNECT is not granted
                } catch (e: SecurityException) {
                    Log.w("MainActivity", "Missing BLUETOOTH_CONNECT permission for device name: ${e.message}")
                    null // Or handle appropriately
                }
                val rssi = result.rssi

                val existingDevice = discoveredDevices.find { it.address == deviceAddress }

                if (existingDevice != null) {
                    existingDevice.addRssiReading(rssi)
                    Log.d("MainActivity", "Updated device: ${existingDevice.name ?: "Unknown"}, Address: ${existingDevice.address}, RSSI: ${existingDevice.currentRssi}, Smoothed RSSI: ${existingDevice.smoothedRssi}")
                } else {
                    val newDevice = BleDevice(
                        address = deviceAddress,
                        name = deviceName,
                        currentRssi = rssi
                    )
                    // The BleDevice init block calls addRssiReading, so history and smoothedRssi are initialized.
                    discoveredDevices.add(newDevice)
                    Log.d("MainActivity", "New device: ${newDevice.name ?: "Unknown"}, Address: ${newDevice.address}, RSSI: ${newDevice.currentRssi}, Smoothed RSSI: ${newDevice.smoothedRssi}")
                }

                // Ensure UI updates and list modifications for the adapter are on the main thread
                runOnUiThread {
                    // Sort the list: primary by smoothedRssi descending, secondary by address ascending for stability
                    discoveredDevices.sortWith(compareByDescending<BleDevice> { it.smoothedRssi }.thenBy { it.address })

                    // Submit the sorted list to the adapter
                    bleDeviceAdapter.submitList(discoveredDevices.toList())
                    // Log.d("MainActivity", "Device list updated and sorted. Count: ${discoveredDevices.size}")
                }
            } ?: run {
                Log.w("MainActivity", "ScanResult or device was null.")
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.d("MainActivity", "Batch scan results: ${results?.size}")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("MainActivity", "Scan failed with error code: $errorCode")
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

        bleDeviceAdapter = BleDeviceAdapter()
        val recyclerView: RecyclerView = findViewById(R.id.devicesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = bleDeviceAdapter

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            // Optionally, prompt the user to enable Bluetooth
            Log.w("MainActivity", "Bluetooth is not enabled.")
            // For simplicity, we're not adding an Intent to enable Bluetooth here.
            // In a real app, you would typically do:
            // val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        if (!hasPermissions()) {
            requestPermissions()
        } else {
            // Permissions are already granted.
            // We will start scanning in a later step.
            Log.d("MainActivity", "Permissions already granted.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            var allPermissionsGranted = true
            for (grantResult in grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                Log.d("MainActivity", "All permissions granted by user.")
                // Permissions granted, proceed with BLE operations (e.g., start scanning)
                // For now, we just log. startBleScan() will be called explicitly later.
            } else {
                Log.w("MainActivity", "Not all permissions were granted by user.")
                // Handle the case where permissions are denied (e.g., show a message to the user)
            }
        }
    }

    private fun startBleScan() {
        if (bluetoothAdapter.isEnabled && hasPermissions()) {
            // Check for BLUETOOTH_SCAN permission specifically for Android 12+ when starting scan
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("MainActivity", "BLUETOOTH_SCAN permission not granted for starting scan.")
                // Optionally, request it again or inform the user.
                // For now, we just log and don't start the scan.
                return
            }
            bluetoothLeScanner.startScan(leScanCallback)
            Log.d("MainActivity", "BLE scan started.")
        } else {
            Log.w("MainActivity", "Cannot start BLE scan: Bluetooth disabled or permissions missing.")
            if (!bluetoothAdapter.isEnabled) {
                Log.w("MainActivity", "Bluetooth is not enabled.")
            }
            if (!hasPermissions()) {
                Log.w("MainActivity", "Required permissions are missing.")
                // Attempt to request permissions again if they are missing.
                requestPermissions()
            }
        }
    }

    private fun stopBleScan() {
        if (bluetoothAdapter.isEnabled) {
             // Check for BLUETOOTH_SCAN permission specifically for Android 12+ when stopping scan
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("MainActivity", "BLUETOOTH_SCAN permission not granted for stopping scan.")
                // For stopping scan, this permission might not be strictly needed by the system,
                // but it's good practice to ensure consistency.
                // However, the primary concern for scanning is starting it.
                // If there's an issue here, it might indicate a problem with the app's permission logic.
            }
            bluetoothLeScanner.stopScan(leScanCallback)
            Log.d("MainActivity", "BLE scan stopped.")
        } else {
            Log.w("MainActivity", "Cannot stop BLE scan: Bluetooth disabled.")
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        // private const val REQUEST_ENABLE_BT = 2 // Example if you add enable Bluetooth intent
    }
}
