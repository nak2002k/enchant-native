package org.enchant.core.jobmanager.observers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.enchant.core.jobmanager.ConstraintObserver

internal class ChargingConstraintObserver(
    private val context: Context,
    private val notifier: ConstraintObserver.Notifier
) : ConstraintObserver {
    private var receiver: BroadcastReceiver? = null

    override fun register(notifier: ConstraintObserver.Notifier) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_POWER_CONNECTED ||
                    intent.action == Intent.ACTION_POWER_DISCONNECTED
                ) {
                    notifier.onConstraintMet("charging_state_changed")
                }
            }
        }
        this.receiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
    }

    override fun unregister() {
        receiver?.let {
            context.unregisterReceiver(it)
            receiver = null
        }
    }
}
