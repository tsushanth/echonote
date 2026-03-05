package com.kreativekoala.echonote.di

import android.content.Context
import com.kreativekoala.echonote.data.local.EchoNoteDatabase
import com.kreativekoala.echonote.data.local.BookmarkDao
import com.kreativekoala.echonote.data.local.RecordingDao
import com.kreativekoala.echonote.data.local.RecordingFolderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EchoNoteDatabase {
        return EchoNoteDatabase.create(context)
    }

    @Provides
    fun provideRecordingDao(database: EchoNoteDatabase): RecordingDao {
        return database.recordingDao()
    }

    @Provides
    fun provideRecordingFolderDao(database: EchoNoteDatabase): RecordingFolderDao {
        return database.recordingFolderDao()
    }

    @Provides
    fun provideBookmarkDao(database: EchoNoteDatabase): BookmarkDao {
        return database.bookmarkDao()
    }
}
