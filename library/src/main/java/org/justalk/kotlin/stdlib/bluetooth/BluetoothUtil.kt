package org.justalk.kotlin.stdlib.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresPermission

/**
 * Classic bluetooth util
 * https://developer.android.google.cn/develop/connectivity/bluetooth/setup
 *
 * Tips:
 *     Scan/Pairing bluetooth with no permission by using CompanionDeviceManager
 * https://developer.android.google.cn/develop/connectivity/bluetooth/companion-device-pairing
 *
 * BroadcastReceiver:
 * 1、Bluetooth state changed action: BluetoothAdapter.ACTION_STATE_CHANGED
 * 2、Bond state changed action: BluetoothDevice.ACTION_BOND_STATE_CHANGED
 * 3、Connection state changed action: BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
 */
object BluetoothUtil {

    /**
     * Checks if bluetooth is enabled
     */
    fun isEnabled(bluetoothAdapter: BluetoothAdapter): Boolean {
        return bluetoothAdapter.isEnabled
    }

    /**
     * Start a request to enable bluetooth on device
     *
     * If enable success, return RESULT_OK, otherwise RESULT_CANCELED
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun requestEnableBt(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    /**
     * Checks if device is bonded
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun isBonded(bluetoothAdapter: BluetoothAdapter, device: BluetoothDevice): Boolean {
        return bluetoothAdapter.bondedDevices.any { bondedDevice ->
            bondedDevice.address == device.address
        }
    }

    /**
     * Bond new device
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun bond(device: BluetoothDevice): Boolean {
        return device.createBond()
    }

    /**
     * Connect device
     *
     * @param bluetoothHeadset initialize from below code
     *
     * bluetoothAdapter.getProfileProxy(requireContext(), object : ServiceListener {
     *     override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
     *         if (profile == BluetoothProfile.HEADSET) {
     *             bluetoothHeadset = proxy as BluetoothHeadset
     *             }
     *         }
     *
     *     override fun onServiceDisconnected(profile: Int) {
     *         if (profile == BluetoothProfile.HEADSET) {
     *             bluetoothHeadset = null
     *         }
     *     }
     * }, BluetoothProfile.HEADSET)
     *
     * remember to close use bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
     *
     * @return true if call success; otherwise false
     */
    fun connect(bluetoothHeadset: BluetoothHeadset?, device: BluetoothDevice): Boolean {
        try {
            val connectMethod =
                BluetoothHeadset::class.java.getMethod("connect", BluetoothDevice::class.java)
            connectMethod.invoke(bluetoothHeadset, device)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Checks if device is connected
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun isConnected(bluetoothHeadset: BluetoothHeadset?, device: BluetoothDevice): Boolean {
        return bluetoothHeadset?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
    }

}