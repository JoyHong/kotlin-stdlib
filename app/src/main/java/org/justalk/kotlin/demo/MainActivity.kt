package org.justalk.kotlin.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import org.justalk.kotlin.demo.databinding.FragmentMainBinding
import org.justalk.kotlin.stdlib.activity.setImmersiveMode
import org.justalk.kotlin.stdlib.app.viewDataBindingDelegate
import org.justalk.kotlin.stdlib.bluetooth.BluetoothUtil
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setImmersiveMode()
    }
}

@SuppressLint("MissingPermission")
class MainFragment : Fragment(R.layout.fragment_main) {

    private val binding: FragmentMainBinding by viewDataBindingDelegate()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.text1.setOnClickListener {
            testBluetoothKit()
        }
        bluetoothAdapter.getProfileProxy(requireContext(), object : ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    bluetoothHeadset = proxy as BluetoothHeadset
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HEADSET) {
                    bluetoothHeadset = null
                }
            }
        }, BluetoothProfile.HEADSET)
        requireContext().registerReceiver(bluetoothBroadcastReceiver, IntentFilter().also { filter ->
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothHeadset?.let { proxy ->
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
        }
        requireContext().unregisterReceiver(bluetoothBroadcastReceiver)
    }

    private val bluetoothAdapter by lazy {
        requireContext().getSystemService(BluetoothManager::class.java).adapter
    }
    private var bluetoothHeadset: BluetoothHeadset? = null
    private val bluetoothBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_OFF)
                val nowState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                return
            }
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                val nowState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                return
            }
            val previousState = intent.getIntExtra(BluetoothA2dp.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_DISCONNECTED)
            val nowState = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
        }
    }
    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            activityResult.data?.let { intent ->
                IntentCompat.getParcelableExtra(intent, CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
            }?.let { deviceToPair ->
                if (BluetoothUtil.isConnected(bluetoothHeadset, deviceToPair)) {
                    return@let
                }
                if (BluetoothUtil.isBonded(bluetoothAdapter, deviceToPair)) {
                    BluetoothUtil.connect(bluetoothHeadset, deviceToPair)
                } else {
                    BluetoothUtil.bond(deviceToPair)
                }
            }
        }
    }

    private fun testBluetoothKit() {
        val deviceFilter = BluetoothDeviceFilter.Builder()
//            // Match only Bluetooth devices whose name matches the pattern.
            .setNamePattern(Pattern.compile("JusTalk A2"))
//            // Match only Bluetooth devices whose service UUID matches this pattern.
//            .addServiceUuid(ParcelUuid(UUID(0x1C7BL, -1L)), null)
            .build()
        val pairingRequest = AssociationRequest.Builder()
            // Find only devices that match this request filter.
            .addDeviceFilter(deviceFilter)
            // Stop scanning as soon as one device matching the filter is found.
//            .setSingleDevice(true)
            .build()
        val deviceManager = requireContext().getSystemService(CompanionDeviceManager::class.java)
        deviceManager.associate(
            pairingRequest,
            object : CompanionDeviceManager.Callback() {
                // Called when a device is found. Launch the IntentSender so the user
                // can select the device they want to pair with.
                // 定位到设备且准备好启动用户意见征求对话框
                override fun onAssociationPending(intentSender: IntentSender) {
                    bluetoothLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }

                // 用户确认设备后触发回调
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                }

                // 应用找不到任何设备触发回调
                override fun onFailure(error: CharSequence?) {
                    // discovery_timeout
                    // canceled
                    // user_rejected
                }
            },
            null
        )
    }

}