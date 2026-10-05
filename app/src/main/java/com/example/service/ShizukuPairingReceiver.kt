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
                val port = intent.getIntExtra("port", 0)

                val pending = goAsync()
                ShizukuPairingManager.handlePairingCodeReceived(context.applicationContext, code.orEmpty(), port)
                    .invokeOnCompletion { pending.finish() }
            }
            ShizukuPairingManager.ACTION_STOP_PAIRING_HELPER -> {
                ShizukuPairingManager.dismissHelper(context)
            }
        }
    }
}
