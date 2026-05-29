# Audit Report: feature:backup Module

Module path: feature/backup/src/main/java/org/enchant/backup/
Files examined: 7 Kotlin source files + 1 test file

## 1. SECURITY

### Critical: Nonce Buffer Overflow in decryptSection
File: BackupArchive.kt line 125  Severity: CRITICAL

val nonce = ByteArray(12) allocates only 12 bytes but XCHACHA_NONCE_SIZE = 24.
This causes a buffer overflow when ciphertext.copyInto writes past the allocated region.
Fix: val nonce = ByteArray(XCHACHA_NONCE_SIZE)
Same bug in BackupExporter.importFullBackup line 125.

### Critical: Wrong Random Source for Nonce Generation
File: BackupExporter.kt line 98  Severity: CRITICAL

java.util.Random is used for nonce generation instead of SecureRandom.
SecureRandom is available (import line 21) but not used.
Fix: Use CryptoHelper.generateRandomKey(BackupArchive.XCHACHA_NONCE_SIZE).

### High: No Memory Zeroing of Sensitive Buffers
Files: BackupExporter.kt, BackupArchive.kt

plaintext, nonce, encrypted buffers never zeroed after use.
CryptoHelper.zeroBytes exists but is never called.

### High: AEADBadTagException Log Suppression
File: BackupArchive.kt line 45-46

Returns false silently instead of surfacing tampering to user.
Attackers cannot be distinguished from wrong passphrase.

### Medium: No Atomic Write on Export
File: BackupExporter.kt lines 101-106

Crash mid-write leaves corrupted file. Should use temp file + renameTo.

## 2. BUGS

### Critical: Chunk Upload Race Condition
File: BackupViewModel.kt lines 71-86

uploadChunk launches fire-and-forget coroutines. No ordering guarantee.
Concurrent uploads can arrive out of order, corrupting backup.

### High: downloadBackup Ignores Downloaded Data
File: BackupViewModel.kt lines 129-144

Bytes returned by getBinary are completely discarded.
No restore triggered. successMessage set but no actual data.

### High: Retry Loop Without Exponential Backoff or Timeout
File: BackupViewModel.kt lines 215-242 pollExportStatus

Fixed 2-second interval, recursive forever, no max retry/timeout.

### High: getLatestBackup Does Not Populate backups List
File: BackupViewModel.kt lines 108-127

latestBackup set but backups always emptyList. No listing method.

### Medium: downloadProgress Never Updated
Field exists but downloadBackup sets successMessage immediately.

### Medium: uploadProgress Stale After First Chunk Failure
If chunk i fails, isProcessing=false but uploadProgress reflects chunkIndex+1.

### Medium: restoreBackup Has No Selective Section Option
Server restore is all-or-nothing. Local importFullBackup supports Selective.

## 3. COMPLETENESS

### Missing: Selective Restore from Server Backup
restoreBackup restores everything or nothing.

### Missing: Backup Listing
No method to list all backups. backups field always empty.

### Missing: Media Data Backup
Archive exporters handle metadata only. Media attachments not exported.

### Present: Full Archive Export/Import Cycle
exportFullBackup and importFullBackup cover all four sections with selective restore.

## 4. CODE QUALITY

### Medium: Duplicate existingIds Query Pattern
Each exporter queries all IDs into in-memory Set before insert.
Large DBs cause high memory usage. INSERT OR IGNORE would be better.

### Medium: No Transaction Timeout on Import
beginTransaction has no timeout. Large imports hold write lock indefinitely.

### Low: prettyPrint = true in Production Json
BackupExporter.kt line 29. Inflates backup file size by ~30 percent.

## 5. TEST COVERAGE

### Critical Missing: No Tests for BackupExporter
Zero tests for exportFullBackup and importFullBackup.

### Critical Missing: No Tests for BackupArchive
Zero tests for encryptSection, decryptSection, verifyIntegrity.
Nonce buffer overflow would be caught by any test.

### Critical Missing: No Tests for Chunk Upload/Download
Only basic invocation tests. No ordering, progress, or failure tests.

### Critical Missing: No Integration Tests
No full export -> import round-trip test.

### Present: BackupViewModelTest Insufficient
Only 5 tests. None assert state transitions, progress values, or error states.

## SUMMARY

CRITICAL: Nonce buffer overflow (12 vs 24 bytes) - BackupArchive.kt BackupExporter.kt
CRITICAL: java.util.Random for nonce - BackupExporter.kt
CRITICAL: Chunk upload race condition - BackupViewModel.kt
HIGH: No memory zeroing - BackupExporter.kt BackupArchive.kt
HIGH: AEADBadTagException suppressed - BackupArchive.kt
HIGH: downloadBackup discards bytes - BackupViewModel.kt
HIGH: Polling loop infinite - BackupViewModel.kt
HIGH: backups list never populated - BackupViewModel.kt
HIGH: No server-side backup listing
HIGH: No media data backup
MEDIUM: Non-atomic file writes - BackupExporter.kt
MEDIUM: downloadProgress never updated - BackupViewModel.kt
MEDIUM: uploadProgress stale after failure - BackupViewModel.kt
MEDIUM: restoreBackup lacks selective restore - BackupViewModel.kt
MEDIUM: existingIds in-memory Set pattern - All archive exporters
MEDIUM: No DB transaction timeout
MEDIUM: No export completion callback
LOW: prettyPrint = true inflates size - BackupExporter.kt
CRITICAL: No tests for BackupExporter
CRITICAL: No tests for BackupArchive
CRITICAL: No chunk upload/download tests - BackupViewModelTest
CRITICAL: No integration test
