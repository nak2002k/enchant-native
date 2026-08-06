package org.enchant.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.core.base.SecurePreferences
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.entity.ConversationEntity

private val ShareBrandPurple = Color(0xFF3A0D6E)
private val ShareBrandPurpleDark = Color(0xFF8E24AA)
private val ShareGroupGreen = Color(0xFF6A9C2F)

private val ShareLightScheme = lightColorScheme(
    primary = ShareBrandPurple,
    onPrimary = Color.White,
    primaryContainer = ShareBrandPurple.copy(alpha = 0.12f),
    onPrimaryContainer = ShareBrandPurple,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    surfaceTint = ShareBrandPurple,
)

private val ShareDarkScheme = darkColorScheme(
    primary = ShareBrandPurpleDark,
    onPrimary = Color.Black,
    primaryContainer = ShareBrandPurpleDark.copy(alpha = 0.2f),
    onPrimaryContainer = ShareBrandPurpleDark,
    background = Color(0xFF000000),
    onBackground = Color(0xFFE5E5E7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFE5E5E7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color.Black,
    surfaceTint = ShareBrandPurpleDark,
)

private data class ShareTarget(
    val conversationId: String,
    val name: String,
    val isGroup: Boolean
)

class ShareTargetActivity : ComponentActivity() {
    companion object {
        const val ACTION_SHARE_TEXT = "org.enchant.action.SHARE_TEXT"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var sharedText: String? = null
    private var imageUris: List<Uri> = emptyList()
    private var mimeType: String? = null
    private var sendAction: String? = null

    private var targets by mutableStateOf<List<ShareTarget>>(emptyList())
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        captureSharedContent(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) ShareDarkScheme else ShareLightScheme
            ) {
                ShareSheet(
                    targets = targets,
                    isLoading = isLoading,
                    onClose = { finish() },
                    onTargetSelected = { conversationId ->
                        scope.launch {
                            forwardToConversation(conversationId)
                            runOnUiThread { finish() }
                        }
                    }
                )
            }
        }
        scope.launch { loadTargets() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun captureSharedContent(intent: Intent?) {
        sendAction = intent?.action
        mimeType = intent?.type
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                } else if (intent.type?.startsWith("image/") == true) {
                    imageUris = listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
            }
        }
    }

    private suspend fun loadTargets() {
        try {
            val pool = DatabasePool.instance
            if (pool == null) {
                // Legacy path: no database available yet — forward to the
                // stored target conversation exactly as before.
                targets = emptyList()
                isLoading = false
                val storedId = SecurePreferences.getString("share.target_conversation_id")
                if (storedId != null) {
                    forwardToConversation(storedId)
                    runOnUiThread { finish() }
                }
                return
            }
            val conversationDao = ConversationDao(pool)
            val recipientDao = RecipientDao(pool)
            val conversations = conversationDao.getAll().first()
            targets = conversations
                .filterNot { it.isArchived }
                .sortedByDescending { it.lastMessageTimestamp ?: 0L }
                .map { conv -> ShareTarget(
                    conversationId = conv.conversationId,
                    name = resolveDisplayName(pool, recipientDao, conv),
                    isGroup = conv.type.equals("group", ignoreCase = true)
                ) }
        } catch (e: Exception) {
            android.util.Log.w("Enchant", "Share targets failed: ${e.message}")
            targets = emptyList()
        } finally {
            isLoading = false
        }
    }

    private suspend fun resolveDisplayName(
        pool: DatabasePool,
        recipientDao: RecipientDao,
        conv: ConversationEntity
    ): String {
        if (conv.type.equals("group", ignoreCase = true)) {
            val groupName = pool.readWith { db ->
                db.rawQuery(
                    "SELECT name FROM groups_table WHERE group_id = ?",
                    arrayOf(conv.conversationId)
                ).use { if (it.moveToFirst()) it.getString(0) else null }
            }
            if (!groupName.isNullOrBlank()) return groupName
            return "Group"
        }
        val cached = runCatching { recipientDao.getByUserId(conv.conversationId) }.getOrNull()
        val name = cached?.displayName ?: cached?.username
        if (!name.isNullOrBlank()) return name
        return conv.conversationId.take(10)
    }

    /**
     * Forwards the captured shared content to [conversationId] — identical
     * send logic to the legacy handleIntent path.
     */
    private suspend fun forwardToConversation(conversationId: String) {
        runCatching {
            when (sendAction) {
                Intent.ACTION_SEND -> {
                    if (mimeType?.startsWith("text/") == true) {
                        sharedText?.let { text ->
                            MessageSendPipeline.sendMessage(
                                conversationId = conversationId,
                                recipientUserId = conversationId,
                                plaintext = text.encodeToByteArray()
                            )
                        }
                    } else if (mimeType?.startsWith("image/") == true) {
                        imageUris.firstOrNull()?.let { uri ->
                            MessageSendPipeline.sendMediaMessage(
                                conversationId = conversationId,
                                recipientUserId = conversationId,
                                fileUri = uri,
                                mimeType = mimeType ?: "image/*"
                            )
                        }
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    imageUris.forEach { uri ->
                        MessageSendPipeline.sendMediaMessage(
                            conversationId = conversationId,
                            recipientUserId = conversationId,
                            fileUri = uri,
                            mimeType = mimeType ?: "image/*"
                        )
                    }
                }
            }
        }.onFailure {
            android.util.Log.w("Enchant", "Share forward failed: ${it.message}")
        }
    }
}

@Composable
private fun ShareSheet(
    targets: List<ShareTarget>,
    isLoading: Boolean,
    onClose: () -> Unit,
    onTargetSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share to Enchant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    targets.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No conversations yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(targets, key = { it.conversationId }) { target ->
                                ShareTargetRow(
                                    target = target,
                                    onClick = { onTargetSelected(target.conversationId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTargetRow(
    target: ShareTarget,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (target.isGroup) ShareGroupGreen else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = target.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = target.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
