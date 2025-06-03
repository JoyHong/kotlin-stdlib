package org.justalk.kotlin.stdlib.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
     * @param bluetoothA2dp initialize from below code
     *
     * bluetoothAdapter.getProfileProxy(context, object : ServiceListener {
     *     override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
     *         bluetoothA2dp = proxy as BluetoothA2dp
     *     }
     *
     *     override fun onServiceDisconnected(profile: Int) {
     *         bluetoothHeadset = null
     *     }
     * }, BluetoothProfile.A2DP)
     *
     * remember to close use bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp)
     *
     * @return true if call success; otherwise false
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(bluetoothA2dp: BluetoothA2dp, device: BluetoothDevice): Boolean {
        try {
            val connectMethod =
                BluetoothA2dp::class.java.getMethod("connect", BluetoothDevice::class.java)
            connectMethod.invoke(bluetoothA2dp, device)
            return true
        } catch (tr: Throwable) {
            tr.printStackTrace()
            return false
        }
    }

    /**
     * Get device connection state
     *
     * @see BluetoothProfile.STATE_CONNECTED
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun getConnectionState(bluetoothA2dp: BluetoothA2dp, device: BluetoothDevice): Int {
        return bluetoothA2dp.getConnectionState(device)
    }

}