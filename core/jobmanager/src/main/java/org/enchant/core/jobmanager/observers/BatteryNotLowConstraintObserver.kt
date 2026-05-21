package org.enchant.core.jobmanager.observers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.enchant.core.jobmanager.ConstraintObserver

internal class BatteryNotLowConstraintObserver(
    private val context: Context,
    private val notifier: ConstraintObserver.Notifier
) : ConstraintObserver {
    private var receiver: BroadcastReceiver? = null

    override fun register(notifier: ConstraintObserver.Notifier) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_LOW ||
                    intent.action == Intent.ACTION_BATTERY_OKAY
                ) {
                    notifier.onConstraintMet("battery_state_changed")
                }
            }
        }
        this.receiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
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
