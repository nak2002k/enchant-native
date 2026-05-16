package org.enchant.feature.share

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.chooser.ChooserTarget
import android.service.chooser.ChooserTargetService
import org.enchant.core.base.DI
import org.enchant.core.base.SecurePreferences

class ConversationChooserTargetService : ChooserTargetService() {
    override fun onGetChooserTargets(
        targetActivity: ComponentName,
        matchedFilter: Intent?
    ): MutableList<ChooserTarget> {
        val targets = mutableListOf<ChooserTarget>()
        try {
            val convId = SecurePreferences.getString("share.target_conversation_id")
            if (convId != null) {
                val intent = Intent().apply {
                    component = targetActivity
                    putExtra("conversation_id", convId)
                }
                targets.add(
                    ChooserTarget(
                        "Last conversation",
                        Icon.createWithResource(this, android.R.drawable.ic_dialog_info),
                        1.0f,
                        ComponentName(this, ShareTargetActivity::class.java),
                        Bundle().apply { putString("conversation_id", convId) }
                    )
                )
            }
        } catch (_: Exception) {}
        return targets
    }
}
