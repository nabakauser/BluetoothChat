package com.example.bluetoothchat

sealed interface ConnectionResult {
    object ConnectionEstablished: ConnectionResult
    data class Error(val message: String) : ConnectionResult
}