package com.kreativekoala.echonote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kreativekoala.echonote.BuildConfig
import com.kreativekoala.echonote.data.local.RecordingDao
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.InstallIdProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Uploads a single recording's audio + transcript + language to the ingest backend,
 * as an opt-in contribution toward training speech-recognition models.
 *
 * Enqueued right after a transcription completes and is persisted (see
 * PlayerViewModel.transcribe()), constrained to unmetered (Wi-Fi) networks.
 *
 * Future work: DELETE https://voxkey-ingest.t-sushanth.workers.dev/v1/audio-contributions/<installId>
 * for user-initiated revocation of already-uploaded contributions.
 */
@HiltWorker
class ContributionUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val recordingDao: RecordingDao,
    private val settingsRepository: SettingsRepository,
    private val installIdProvider: InstallIdProvider
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        private val client = OkHttpClient()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return@withContext Result.failure()
        val recording = recordingDao.getRecordingById(recordingId) ?: return@withContext Result.success()

        val contributeEnabled = settingsRepository.contributeVoiceData.first()
        val transcript = recording.transcript
        val language = recording.contributionLanguage

        // Preconditions not met: nothing to do, and nothing to retry.
        if (!contributeEnabled || transcript.isNullOrBlank() || language == null || recording.contributedAt != null) {
            return@withContext Result.success()
        }

        val audioFile = File(recording.fileUri)
        if (!audioFile.exists()) {
            return@withContext Result.success()
        }

        if (BuildConfig.INGEST_URL.isBlank()) {
            // No ingest endpoint configured for this build — nothing we can do.
            return@withContext Result.success()
        }

        return@withContext try {
            val audioMediaType = "audio/mp4".toMediaTypeOrNull()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("transcript", transcript)
                .addFormDataPart("language", language.bcp47Tag)
                .addFormDataPart("installId", installIdProvider.getOrCreateInstallId())
                .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody(audioMediaType))
                .build()

            val request = Request.Builder()
                .url("${BuildConfig.INGEST_URL}/v1/audio-contributions")
                .header("X-VoxKey-Key", BuildConfig.INGEST_KEY)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    recordingDao.update(
                        recording.copy(
                            contributedAt = System.currentTimeMillis(),
                            pendingContribution = false
                        )
                    )
                    Result.success()
                } else {
                    // Let WorkManager's normal retry policy handle transient failures.
                    Result.retry()
                }
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
