package com.example.bluetoothchat.mapper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.example.bluetoothchat.BluetoothDeviceDomain

@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    return BluetoothDeviceDomain(
        name = name,
        address = address
    )
}