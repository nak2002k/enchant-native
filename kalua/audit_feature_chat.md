# feature:chat Audit

## Security Issues

### 1. Message Content Logging
- **MEDIUM**: `ConversationScreen.kt` line 246 directly passes `message.content` to `copyToClipboard()`. While system clipboard is the intended API, message content should be treated as sensitive and not exposed through logs.
- **LOW**: Exception handlers in `MessageSendPipeline.kt` (lines 281, 300, 320) and `IncomingMessageProcessor.kt` (line 281) log error messages that could potentially contain message metadata. The log tag "Enchant" is generic which is good, but `e.message` should be sanitized.

### 2. Keyboard Input Protection
- **HIGH**: `ComposerBar` in `ConversationScreen.kt` (line 471-483) uses standard `OutlinedTextField` without `keyboardOptions` security flags. The `ImeAction.Send` is set but no `KeyboardType` or `imeOptions` flags like `FLAG_SECURE` are applied.
- **HIGH**: The `text` field on line 436 is a standard `String` state variable. No secure flag is applied to prevent system clipboard copy of message text in the composer.

### 3. Screenshot/Screen Recording Handling
- **HIGH**: No `FLAG_SECURE` applied to the activity or window containing `ConversationScreen`. Media content (images, video) in `MediaViewerScreen` is also not protected. The reference implementation applies `WindowManager.LayoutParams.FLAG_SECURE` on sensitive screens.
- **MEDIUM**: View-once media in `ConversationViewModel.markViewOnceViewed()` (line 405-409) relies purely on application logic to delete local media. There's no secure surface protection preventing screenshots during view-once display.

### 4. Sensitive Data in Memory
- **MEDIUM**: `MessageSendPipeline` stores plaintext as `ByteArray` throughout send flow (line 69, 122). After encryption, the plaintext byte array should be explicitly overwritten (zeroed) before deprecation, especially for media messages which can be large.
- **LOW**: `ConversationRepository.insertMessage()` and `sendTextMessage()` store plaintext in `MessageEntity.content` field after encoding, which is expected but worth noting that database encryption at rest is handled elsewhere in the system.

---

## Bugs

### 1. Message Ordering
- **MEDIUM**: `loadMoreMessages()` in `ConversationViewModel.kt` (line 94-103) prepends older messages to the list: `_messages.value = list + _messages.value`. However, when the cursor-based pagination returns messages in descending order, this creates chronological ordering problems. The `ChatPagingSource` correctly uses descending order from the repository, but older messages are prepended which may cause visual ordering issues if timestamps are not perfectly sequential.
- **LOW**: `jumpToMessage()` and `jumpToDate()` rely on in-memory `_messages.value` which may not contain all messages. If the target message is not loaded, the function silently fails by not emitting any scroll event (closestIndex stays -1).

### 2. Delivery Receipt Handling
- **MEDIUM**: `MessageSendPipeline.sendDeliveryReceipt()` (line 266-283) and `sendReadReceipt()` (line 285-302) parse `envelopeId` as `Long` via `toLongOrNull()`: `val ts = envelopeId.toLongOrNull() ?: System.currentTimeMillis()`. This is a lossy conversion since `envelopeId` is a UUID string. The fallback to current timestamp could cause receipt timestamp mismatches.
- **LOW**: `IncomingMessageProcessor.processSignalMessage()` (line 160-228) handles receipts but `processUnidentifiedSender()` (line 243-284) silently ignores receipt messages (line 272-273: just returns `ProcessResult.Handled`), meaning sealed sender delivery receipts are not processed.

### 3. Encryption/Decryption Error Handling
- **MEDIUM**: `SessionManager.encryptMessage()` and `decryptMessage()` can return null (observed in `MessageSendPipeline` line 90-91). The error is propagated as `SendResult.Failed(SendError.ENCRYPTION_FAILED)` but the actual decryption failure in `IncomingMessageProcessor` (line 172-174) returns a generic "Decryption failed" error without logging the underlying cause.
- **LOW**: `fetchKeyBundle()` in `IncomingMessageProcessor.kt` (line 304-327) silently swallows exceptions with `catch (_: Exception)`. If key bundle fetch fails repeatedly, there's no circuit breaker or user notification.

---

## Completeness Gaps

### 1. Message Types Not Fully Supported
- **MEDIUM**: Location messages (line 181-204) store content as `"LOCATION_JSON:${lat}:${lng}:${label}"` string format. This is not a structured type and cannot be properly rendered as an interactive map preview. The reference implementation uses a dedicated location message type.
- **MEDIUM**: Sticker messages (line 206-222) use `"STICKER_JSON:$packId:$stickerId"` format - same issue, not a structured type.
- **MEDIUM**: Contact cards (line 422-438) store vcard as plaintext with `"VCARD_JSON:"` prefix - not a structured message type.
- **LOW**: Voice messages in `sendVoiceMessage()` (line 159-178) hardcode mimeType as `"audio/mp4"` regardless of actual format.

### 2. Reactions
- **MEDIUM**: `setReaction()` in `ConversationViewModel.kt` (line 286-290) calls `pipeline.sendReaction()` but the UI shows reactions read from `message.reactions` which is populated by `attachReactions()` in `ConversationRepository`. There's no UI to *remove* a reaction once added. The reference implementation supports toggling reactions.
- **LOW**: Reactions are stored locally in `addReaction()`/`removeReaction()` but there's no synchronization when receiving reactions from other users through `IncomingMessageProcessor`.

### 3. Replies
- **LOW**: `replyToEnvelopeId` is stored and displayed via `ReplyPreview` component, but there's no indicator in `MessageBubble` itself showing which message is being replied to (no "reply chain" or quoted message preview in bubble).

### 4. Edits
- **LOW**: Edit UI in `MessageBubble` (onEdit callback) uses `messageText` state variable from outer scope. This creates a race condition if user edits multiple messages. The edit text should be isolated per message.

### 5. Message Status Icons
- **LOW**: In `MessageBubble` (line 586-598), all non-SENT/DELIVERED/READ statuses show a generic `AccessTime` icon. There's no visual distinction between PENDING, FAILED, SENDING states.

---

## Code Quality Issues

### 1. ViewModel Architecture
- **MEDIUM**: `ConversationViewModel` directly uses `SecurePreferences.getString()` and `SecurePreferences.getBoolean()` (lines 88, 111, 113) instead of dependency injection. This tightly couples the ViewModel to a static singleton, making testing difficult.
- **MEDIUM**: `MessageSendPipeline` and `IncomingMessageProcessor` are **object singletons** with mutable internal state (`initialized`, `bufferedMessages`, `lastTypingTs`, etc.). This pattern is not thread-safe by design and makes initialization order critical. The reference implementation uses proper dependency injection with scoped instances.

### 2. Coroutine Scope Management
- **MEDIUM**: `ConversationViewModel` uses `viewModelScope.launch` for operations that may outlive the ViewModel's active state (e.g., `sendLocationMessage` that makes API calls). If `init()` is called multiple times (line 73 guard exists but `loadMoreMessages()` doesn't), multiple coroutines collect from the same flow.
- **MEDIUM**: `MessageSendPipeline.sendDeliveryReceipt()` and `sendReadReceipt()` use `scope?.launch` (lines 274, 293) which is a detached scope that may not complete if the app goes to background. These should use a proper structured concurrency model.
- **LOW**: `typingJob` in `MessageSendPipeline` (line 45, 324-330) can be lost if `sendTypingIndicator` is called after `shutdown()`, which cancels the scope. No check before launching.

### 3. State Flow Patterns
- **MEDIUM**: `_messages` StateFlow is directly mutated (`_messages.value = list`) instead of using `update {}`. This can cause race conditions if multiple coroutines modify it simultaneously.
- **MEDIUM**: `loadConversations()` (line 278-283) is called unconditionally in `ConversationScreen` LaunchedEffect (line 85). This creates a second subscription to the conversations flow in addition to `getConversations()` in the list, potentially causing duplicate emissions.
- **LOW**: `sendLocationMessage()` (line 182), `sendSticker()` (line 207) silently swallow exceptions in the outer `pipeline.sendMessage()` call by mapping to FAILED state. No error details propagated to UI beyond "Failed to send".

### 4. Error Handling
- **MEDIUM**: Generic `Exception` catches throughout (`catch (e: Exception)`) mask specific error types. For security-sensitive code, specific exception handling would be preferable to detect tampering or protocol violations.
- **MEDIUM**: `MediaService.compressImage()` can return `null` on failure but the caller `sendMediaMessage()` doesn't check for null, leading to potential NPE when using result.

### 5. Security-Sensitive Code
- **LOW**: `copyToClipboard()` (line 314-318) uses system clipboard without `ClipboardManager.newPlainclip()` being flagged as sensitive. Messages copied to clipboard persist beyond app scope. No warning or automatic clipboard clear.

---

## Recommendations (Prioritized)

### Priority 1 - Critical for production
1. **Apply `FLAG_SECURE`** to ConversationScreen window to prevent screenshots/recording of message content.
2. **Use UUID-safe timestamp handling** for delivery/read receipts. The current `envelopeId.toLongOrNull()` conversion loses data for UUID-format envelope IDs.
3. **Add structured message types** for Location, Sticker, and ContactCard instead of JSON-prefixed strings.

### Priority 2 - High priority
4. **Inject `SecurePreferences`** instead of static access in `ConversationViewModel` for testability.
5. **Convert object singletons** (`MessageSendPipeline`, `IncomingMessageProcessor`) to proper injectable classes with scoped lifetimes.
6. **Add reaction toggle UI** - ability to remove own reactions.
7. **Zero plaintext byte arrays** after encryption in `sendMessage()` flow.

### Priority 3 - Medium priority
8. **Add keyboard security flags** to composer text field - `KeyboardOptions` with appropriate security settings.
9. **Handle `mediaBytes` nullability** in `MediaService.compressImage()` caller chain.
10. **Fix reply-to display** - show quoted message content in `MessageBubble`.
11. **Differentiate message status icons** - show distinct icons for SENDING, PENDING, FAILED states.
12. **Add circuit breaker** for repeated key bundle fetch failures in `fetchKeyBundle()`.

### Priority 4 - Minor/Polish
13. **Add viewModelScope check** before launching typing indicator jobs after shutdown.
14. **Use `MutableStateFlow.update {}`** for thread-safe mutations of `_messages`.
15. **Remove duplicate `loadConversations()`** subscription or consolidate into single source of truth.
16. **Add automatic clipboard clear** after 60 seconds for sensitive message copies.
