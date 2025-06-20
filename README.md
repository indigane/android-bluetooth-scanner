# Bluetooth Scanner

A minimal, free and open-source Android application that scans for nearby Bluetooth Low Energy (BLE) devices and displays them sorted by signal strength in descending order.

## Features

- Scans for nearby Bluetooth Low Energy (BLE) devices.
- Lists discovered devices with their name, address, and signal strength (RSSI).
- Sorts devices by signal strength in descending order, updated in real-time.
- Handles Bluetooth permissions and enabling.

## Requirements

- Android device with Bluetooth Low Energy (BLE) support.
- Android API level 26 (Android 8.0 Oreo) or higher.

## Permissions

The application requires the following permissions to function correctly:

- `android.permission.BLUETOOTH` (maxSdkVersion 30): For basic Bluetooth functionality on older Android versions.
- `android.permission.BLUETOOTH_ADMIN` (maxSdkVersion 30): For managing Bluetooth settings on older Android versions.
- `android.permission.BLUETOOTH_SCAN`: To discover BLE devices (Android 12+).
- `android.permission.BLUETOOTH_CONNECT`: To interact with already-paired Bluetooth devices and manage Bluetooth state (Android 12+).
- `android.permission.ACCESS_FINE_LOCATION`: Required for BLE scanning, especially on Android 6.0 and newer, as BLE beacons can be used to determine location.

## Contributing

Contributions are welcome! This project has been primarily developed using an LLM agent and utilizes GitHub Actions for continuous integration. While the project follows standard Android structure, contributors intending to use Android Studio extensively might need to verify or adjust project configurations.

If you'd like to improve the app or add new features, please follow these general steps:
1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Make your changes and commit them.
4. Push your changes to your fork.
5. Submit a pull request with a clear description of your changes.

## License

This project is licensed under the MIT-0 license.
