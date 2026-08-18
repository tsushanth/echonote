package com.kreativekoala.echonote.util

object Constants {
    const val APP_NAME = "ClearVoice Recorder"
    const val RECORDINGS_DIRECTORY = "Recordings"

    object Billing {
        object Subscriptions {
            const val WEEKLY = "com.kreativekoala.clearvoice.subscription.weekly"
            const val MONTHLY = "com.kreativekoala.clearvoice.subscription.monthly"
            const val YEARLY = "com.kreativekoala.clearvoice.subscription.yearly"
        }

        val ALL_SUBSCRIPTION_IDS = listOf(
            Subscriptions.WEEKLY,
            Subscriptions.MONTHLY,
            Subscriptions.YEARLY
        )
    }

    object Premium {
        const val FREE_RECORDING_LIMIT = 3
        const val FREE_FOLDER_LIMIT = 1
        const val FREE_BOOKMARK_LIMIT = 2
        const val FREE_OPEN_LIMIT = 2
        const val TERMS_OF_SERVICE_URL = "https://kreativekoala.llc/terms"
        const val PRIVACY_POLICY_URL = "https://kreativekoala.llc/privacy"
    }

    object Audio {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_CHANNELS = 1
        const val STEREO_CHANNELS = 2
        const val SKIP_FORWARD_INTERVAL = 15_000L // ms
        const val SKIP_BACKWARD_INTERVAL = 15_000L // ms
        const val MIN_PLAYBACK_RATE = 0.5f
        const val MAX_PLAYBACK_RATE = 2.0f
        const val PLAYBACK_RATE_STEP = 0.25f
        const val WAVEFORM_SAMPLES_PER_SECOND = 50
        const val SILENCE_THRESHOLD = 0.01f
        const val SILENCE_MIN_DURATION = 500L // ms
    }

    object UI {
        const val CORNER_RADIUS = 12 // dp
        const val SMALL_CORNER_RADIUS = 8 // dp
        const val WAVEFORM_BAR_WIDTH = 3f // dp
        const val WAVEFORM_BAR_SPACING = 2f // dp
        const val RECORD_BUTTON_SIZE = 72 // dp
        const val ANIMATION_DURATION = 300 // ms
        const val MAX_WAVEFORM_HEIGHT = 120 // dp
    }
}
