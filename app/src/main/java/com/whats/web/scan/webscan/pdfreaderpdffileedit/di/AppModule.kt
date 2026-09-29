package com.whats.web.scan.webscan.pdfreaderpdffileedit.di

import android.content.Context
import androidx.room.Room
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.AppDatabase
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileMetaDao
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
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pdfreader.db").build()

    @Provides
    fun fileMetaDao(db: AppDatabase): FileMetaDao = db.fileMetaDao()
}
