package com.kreativekoala.echonote.di

import android.content.Context
import com.kreativekoala.echonote.BuildConfig
import com.kreativekoala.echonote.billing.EntitlementRepository
import com.kreativekoala.echonote.billing.PlayBillingGateway
import com.kreativekoala.echonote.billing.PrefsEntitlementOverride
import com.kreativekoala.echonote.billing.PrefsEntitlementStore
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.AudioRecorderService
import com.kreativekoala.echonote.service.AudioPlayerService
import com.kreativekoala.echonote.service.AudioEditorService
import com.kreativekoala.echonote.service.TranscriptionService
import com.kreativekoala.echonote.service.LocationService
import com.kreativekoala.echonote.service.AppOpenTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAudioRecorderService(@ApplicationContext context: Context): AudioRecorderService {
        return AudioRecorderService(context)
    }

    @Provides
    @Singleton
    fun provideAudioPlayerService(@ApplicationContext context: Context): AudioPlayerService {
        return AudioPlayerService(context)
    }

    @Provides
    @Singleton
    fun provideAudioEditorService(@ApplicationContext context: Context): AudioEditorService {
        return AudioEditorService(context)
    }

    @Provides
    @Singleton
    fun provideTranscriptionService(
        @ApplicationContext context: Context,
        audioPlayerService: AudioPlayerService,
        settingsRepository: SettingsRepository
    ): TranscriptionService {
        return TranscriptionService(context, audioPlayerService, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideLocationService(@ApplicationContext context: Context): LocationService {
        return LocationService(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideAppOpenTracker(@ApplicationContext context: Context): AppOpenTracker {
        return AppOpenTracker(context)
    }

    @Provides
    @Singleton
    fun provideEntitlementRepository(@ApplicationContext context: Context): EntitlementRepository {
        return EntitlementRepository(
            gateway = PlayBillingGateway(context),
            store = PrefsEntitlementStore(context),
            checkEnabled = BuildConfig.ENTITLEMENT_CHECK_ENABLED,
            override = if (BuildConfig.DEBUG) PrefsEntitlementOverride(context) else null,
        )
    }
}
