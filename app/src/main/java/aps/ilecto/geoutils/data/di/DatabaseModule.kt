package net.interlecto.geoutils.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.interlecto.geoutils.data.local.BaseMapDao
import net.interlecto.geoutils.data.local.GeoUtilsDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GeoUtilsDatabase =
        Room.databaseBuilder(context, GeoUtilsDatabase::class.java, GeoUtilsDatabase.NAME)
            .build()

    @Provides
    fun provideBaseMapDao(database: GeoUtilsDatabase): BaseMapDao = database.baseMapDao()
}
