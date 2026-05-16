package org.enchant.chat.data

import android.content.ContentValues
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.base.AppConfig
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.network.ApiClient
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

data class MediaUploadResult(
    val mediaId: String,
    val mediaKey: ByteArray,
    val mediaIv: ByteArray
)

object MediaService {
    private const val TAG = "MediaService"
    private const val MAX_IMAGE_DIMENSION = 1024
    private const val MAX_FILE_SIZE = 128 * 1024 * 1024
    private const val JPEG_QUALITY = 85

    private var apiClient: ApiClient? = null
    private var initialized = false

    fun init(client: ApiClient) {
        apiClient = client
        initialized = true
    }

    private fun checkInit() {
        if (!initialized) throw IllegalStateException("MediaService not initialized")
    }

    fun pickImage(fromCamera: Boolean): Intent {
        val ctx = AppConfig.applicationContext ?: return Intent()
        return if (fromCamera) {
            val photoFile = createTempFile(ctx, "photo_", ".jpg")
            val uri = FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", photoFile
            )
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }
        }
    }

    fun pickVideo(): Intent {
        return Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
            type = "video/*"
        }
    }

    fun pickDocument(mimeTypes: Array<String> = arrayOf("*/*")): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
    }

    suspend fun startRecording(): File? = withContext(Dispatchers.IO) {
        try {
            val file = createTempFile("voice_", ".mp4")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recording = recorder
            recordingFile = file
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            null
        }
    }

    private var recording: MediaRecorder? = null
    private var recordingFile: File? = null

    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        try {
            recording?.apply {
                stop()
                release()
            }
            recording = null
            val file = recordingFile
            recordingFile = null
            if (file != null && file.length() < 1000) {
                file.delete()
                null
            } else file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            null
        }
    }

    suspend fun compressImage(uri: Uri, maxSize: Int = MAX_IMAGE_DIMENSION): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val ctx = AppConfig.applicationContext ?: return@withContext null
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = inputStream.use { it.readBytes() }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext bytes
            val (width, height) = if (bitmap.width > bitmap.height) {
                maxSize to (bitmap.height * maxSize / bitmap.width)
            } else {
                (bitmap.width * maxSize / bitmap.height) to maxSize
            }
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
            if (scaled != bitmap) bitmap.recycle()
            val output = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            scaled.recycle()
            output.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed", e)
            null
        }
    }

    suspend fun encryptAndUploadMedia(fileBytes: ByteArray, mimeType: String): Result<MediaUploadResult> {
        checkInit()
        return withContext(Dispatchers.Default) {
            try {
                if (fileBytes.size > MAX_FILE_SIZE) {
                    return@withContext Result.failure(IllegalArgumentException("File exceeds 128MB limit"))
                }

                val mediaKey = CryptoHelper.generateRandomKey(32)
                val mediaIv = CryptoHelper.generateRandomKey(12)
                val plaintextWithIv = mediaIv + fileBytes
                val encryptedData = CryptoHelper.encryptAesGcm(plaintextWithIv, mediaKey)

                val client = apiClient!!
                val response = client.postRaw("/v1/media/upload", encryptedData, mimeType)
                val json = response.getOrNull() ?: return@withContext Result.failure(Exception("Upload failed"))
                val mediaId = json["media_id"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("No media_id in response"))

                Result.success(MediaUploadResult(
                    mediaId = mediaId,
                    mediaKey = mediaKey,
                    mediaIv = mediaIv
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun downloadAndDecryptMedia(
        mediaId: String,
        mediaKey: ByteArray,
        mediaIv: ByteArray
    ): Result<File> {
        checkInit()
        return withContext(Dispatchers.IO) {
            try {
                val client = apiClient!!
                val response = client.getBinary("/v1/media/$mediaId")
                val encryptedData = response.getOrNull()
                    ?: return@withContext Result.failure(Exception("Download failed"))

                val ciphertext = if (mediaIv.isNotEmpty()) {
                    val expectedIv = mediaIv
                    if (encryptedData.size > expectedIv.size) {
                        val fileEncrypted = encryptedData.copyOfRange(expectedIv.size, encryptedData.size)
                        val combined = expectedIv + fileEncrypted
                        combined
                    } else encryptedData
                } else encryptedData

                val decrypted = CryptoHelper.decryptAesGcm(ciphertext, mediaKey)

                val ctx = AppConfig.applicationContext ?: return@withContext Result.failure(Exception("No context"))
                val cacheDir = File(ctx.cacheDir, "media_downloads")
                cacheDir.mkdirs()
                val outputFile = File(cacheDir, "${mediaId}_${UUID.randomUUID()}")
                outputFile.writeBytes(decrypted)

                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun saveToGallery(file: File, mimeType: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val ctx = AppConfig.applicationContext ?: return@withContext false
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            ctx.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to gallery", e)
            false
        }
    }

    private fun createTempFile(ctx: android.content.Context, prefix: String, suffix: String): File {
        val dir = File(ctx.cacheDir, "media_temp")
        dir.mkdirs()
        return File.createTempFile(prefix, suffix, dir)
    }
}
