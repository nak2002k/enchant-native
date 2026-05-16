package org.enchant.feature.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.core.base.SecurePreferences

class ShareTargetActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            handleIntent(intent)
            finish()
        }
    }

    private suspend fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null) {
                        val convId = SecurePreferences.getString("share.target_conversation_id")
                            ?: return
                        MessageSendPipeline.sendMessage(
                            conversationId = convId,
                            recipientUserId = convId,
                            plaintext = sharedText.encodeToByteArray()
                        )
                    }
                } else if (intent.type?.startsWith("image/") == true) {
                    val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (imageUri != null) {
                        val convId = SecurePreferences.getString("share.target_conversation_id")
                            ?: return
                        MessageSendPipeline.sendMediaMessage(
                            conversationId = convId,
                            recipientUserId = convId,
                            fileUri = imageUri,
                            mimeType = intent.type ?: "image/*"
                        )
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.forEach { uri ->
                    val convId = SecurePreferences.getString("share.target_conversation_id")
                        ?: return@forEach
                    MessageSendPipeline.sendMediaMessage(
                        conversationId = convId,
                        recipientUserId = convId,
                        fileUri = uri,
                        mimeType = intent.type ?: "image/*"
                    )
                }
            }
        }
    }
}
