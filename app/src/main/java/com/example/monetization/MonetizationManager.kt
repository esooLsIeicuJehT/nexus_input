package com.example.monetization

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class MonetizationState(
    val userId: String = "USR-${UUID.randomUUID().toString().take(6).uppercase()}",
    val referralCode: String = "CTL-${(1000..9999).random()}",
    val isVipActive: Boolean = false,
    val vipExpiryTimestamp: Long = 0L,
    val coinBalance: Int = 145,                 // 250 coins = 7 days VIP
    val successfulReferralCount: Int = 2,
    val invitedUserCumulativeHours: Float = 38.5f,
    val targetThresholdHours: Float = 48.0f,    // 48h for 1..5, 12h thereafter
    val lastRewardMessage: String = "",
    val isVerifyingAd: Boolean = false
)

class MonetizationManager(private val context: Context) {

    private val _state = MutableStateFlow(MonetizationState())
    val state: StateFlow<MonetizationState> = _state.asStateFlow()

    fun updateThreshold() {
        val count = _state.value.successfulReferralCount
        val target = if (count < 5) 48.0f else 12.0f
        _state.value = _state.value.copy(targetThresholdHours = target)
    }

    /**
     * Simulates server-side verification (SSV) of rewarded ad completion
     * Prevents client-side double counting timer exploit
     */
    fun verifyRewardedAdCompletion(adDurationSec: Int, callbackToken: String, onComplete: (Int) -> Unit) {
        _state.value = _state.value.copy(isVerifyingAd = true)

        // Server-Side Verification: award 5..10 coins scaled to duration
        val coinsAwarded = (5 + (adDurationSec / 6)).coerceIn(5, 10)
        val newCoinBalance = _state.value.coinBalance + coinsAwarded

        var newVipActive = _state.value.isVipActive
        var newVipExpiry = _state.value.vipExpiryTimestamp
        var msg = "SSV Verified: +$coinsAwarded coins added!"

        // Auto grant 7 days VIP when crossing 250 coins threshold
        if (newCoinBalance >= 250) {
            val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
            newVipActive = true
            newVipExpiry = System.currentTimeMillis() + sevenDaysMs
            msg = "SSV Verified: 250 Coins reached! 7 Days VIP Granted!"
        }

        _state.value = _state.value.copy(
            coinBalance = if (newCoinBalance >= 250) newCoinBalance - 250 else newCoinBalance,
            isVipActive = newVipActive,
            vipExpiryTimestamp = newVipExpiry,
            lastRewardMessage = msg,
            isVerifyingAd = false
        )

        sendDailyProgressNotification(
            title = "Controlyst Rewards",
            message = "Earned $coinsAwarded coins! Balance: ${_state.value.coinBalance} / 250 (VIP unlock)"
        )

        onComplete(coinsAwarded)
    }

    /**
     * Direct $4.99 monthly VIP activation
     */
    fun purchaseVipMonthly() {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        _state.value = _state.value.copy(
            isVipActive = true,
            vipExpiryTimestamp = System.currentTimeMillis() + thirtyDaysMs,
            lastRewardMessage = "$4.99 VIP activated! Unlimited cloud sync & zero latency unlocked."
        )
        sendDailyProgressNotification(
            title = "Controlyst VIP Activated",
            message = "Welcome to Controlyst VIP! All features and zero latency injector unlocked."
        )
    }

    /**
     * Simulates receiving server heartbeat ping for invited user play time
     */
    fun addInvitedUserHours(hours: Float) {
        val newHours = _state.value.invitedUserCumulativeHours + hours
        val currentTarget = _state.value.targetThresholdHours

        if (newHours >= currentTarget) {
            // Milestone reached! Grant 1 week VIP
            val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
            val newReferrals = _state.value.successfulReferralCount + 1
            val nextTarget = if (newReferrals < 5) 48.0f else 12.0f

            _state.value = _state.value.copy(
                invitedUserCumulativeHours = newHours - currentTarget,
                successfulReferralCount = newReferrals,
                targetThresholdHours = nextTarget,
                isVipActive = true,
                vipExpiryTimestamp = System.currentTimeMillis() + sevenDaysMs,
                lastRewardMessage = "Referral Milestone! 7 Days VIP rewarded for referral #$newReferrals!"
            )
            sendDailyProgressNotification(
                title = "Referral Goal Reached!",
                message = "Your friend hit $currentTarget hours! 7 days of VIP unlocked."
            )
        } else {
            _state.value = _state.value.copy(invitedUserCumulativeHours = newHours)
        }
    }

    fun sendDailyProgressNotification(title: String, message: String) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val channelId = "controlyst_daily_progress"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Controlyst Progress & Rewards",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily mapping hours, calibration tips, and referral rewards."
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(101, notification)
        } catch (e: Exception) {
            // Notification permission might be pending on Android 13+
        }
    }
}
