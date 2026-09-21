package com.kreativekoala.echonote.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kreativekoala.echonote.BuildConfig
import com.kreativekoala.echonote.data.local.RecordingDao
import com.kreativekoala.echonote.data.model.Recording
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

        // Preconditions not met: nothing to do, and nothing to retry. A contribution
        // will never happen for this recording, so if PlayerViewModel deferred the
        // user's auto-delete preference waiting on this worker (pendingContribution),
        // apply that deletion now instead of leaving the audio file stranded forever.
        if (!contributeEnabled || transcript.isNullOrBlank() || language == null || recording.contributedAt != null) {
            if (recording.pendingContribution) {
                applyDeferredAutoDelete(recording)
            }
            return@withContext Result.success()
        }

        val audioFile = File(recording.fileUri)
        if (!audioFile.exists()) {
            if (recording.pendingContribution) {
                recordingDao.update(recording.copy(pendingContribution = false))
            }
            return@withContext Result.success()
        }

        if (BuildConfig.INGEST_URL.isBlank()) {
            // No ingest endpoint configured for this build — nothing we can do.
            if (recording.pendingContribution) {
                applyDeferredAutoDelete(recording)
            }
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
                    // Upload succeeded — now safe to honor the user's auto-delete
                    // preference for this recording's audio, re-checked live since
                    // it may have changed between transcription and now.
                    if (settingsRepository.autoDeleteAudioAfterTranscription.first()) {
                        try { audioFile.delete() } catch (_: Exception) {}
                    }
                    Result.success()
                } else {
                    // Let WorkManager's normal retry policy handle transient failures.
                    // Leave pendingContribution set so the audio file is preserved
                    // for the retry attempt.
                    Result.retry()
                }
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    // Precondition for the upload stopped holding after PlayerViewModel deferred
    // auto-delete for this recording (contribution disabled/opted-out since,
    // already contributed elsewhere, or no ingest endpoint configured for this
    // build). The upload is never going to happen, so apply the user's auto-delete
    // preference now rather than leaving the audio file stranded indefinitely, and
    // clear the pending flag either way.
    private suspend fun applyDeferredAutoDelete(recording: Recording) {
        if (settingsRepository.autoDeleteAudioAfterTranscription.first()) {
            try { File(recording.fileUri).delete() } catch (_: Exception) {}
        }
        recordingDao.update(recording.copy(pendingContribution = false))
    }
}
