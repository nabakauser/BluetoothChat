package com.example.bluetoothchat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothController {
    val isConnected: StateFlow<Boolean>
    val scannedDevices: StateFlow<List<BluetoothDevice>>
    val pairedDevices: StateFlow<List<BluetoothDevice>>
    val errors: SharedFlow<String>

    fun startDiscovery()
    fun stopDiscovery()
    fun releaseDevices()

    fun startBluetoothService(): Flow<ConnectionResult>
    fun connectTODevice(device: BluetoothDevice): Flow<ConnectionResult>
    fun closeConnection()
}