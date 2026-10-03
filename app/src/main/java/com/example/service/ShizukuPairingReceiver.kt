package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class ShizukuPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ShizukuPairingManager.ACTION_SUBMIT_PAIRING_CODE -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val code = remoteInput?.getCharSequence(ShizukuPairingManager.KEY_PAIRING_CODE)?.toString()
                val port = intent.getIntExtra("port", 5555)

                if (!code.isNullOrBlank()) {
                    ShizukuPairingManager.handlePairingCodeReceived(context, code, port)
                }
            }
            ShizukuPairingManager.ACTION_STOP_PAIRING_HELPER -> {
                ShizukuPairingManager.dismissHelper(context)
            }
        }
    }
}
