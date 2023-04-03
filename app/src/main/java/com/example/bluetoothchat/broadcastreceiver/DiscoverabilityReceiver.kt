package com.example.bluetoothchat.broadcastreceiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DiscoverabilityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action != null) {
            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                when (intent.getIntExtra(
                    BluetoothAdapter.EXTRA_SCAN_MODE,
                    BluetoothAdapter.ERROR
                )) {
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> {
                        Log.d("bluetoothDiscoverability", "Connectable")
                    }
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> {
                        Log.d("bluetoothDiscoverability", "Discoverable")
                    }
                }
            }
        }
    }
}