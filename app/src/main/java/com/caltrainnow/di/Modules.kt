package com.caltrainnow.di

import android.content.Context
import androidx.room.Room
import com.caltrainnow.core.datasource.ScheduleDataSource
import com.caltrainnow.core.engine.DirectionResolver
import com.caltrainnow.core.engine.LookupEngine
import com.caltrainnow.core.engine.ServiceResolver
import com.caltrainnow.core.model.UserConfig
import com.caltrainnow.core.parser.GtfsParser
import com.caltrainnow.core.validation.ScheduleValidator
import com.caltrainnow.data.db.CaltrainDatabase
import com.caltrainnow.data.db.RoomScheduleDataSource
import com.caltrainnow.data.gtfs.GtfsDownloader
import com.caltrainnow.data.location.LocationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CaltrainDatabase {
        return Room.databaseBuilder(
            context,
            CaltrainDatabase::class.java,
            "caltrain_schedule.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRoomDataSource(db: CaltrainDatabase): RoomScheduleDataSource {
        return RoomScheduleDataSource(db)
    }

    @Provides
    @Singleton
    fun provideScheduleDataSource(roomDataSource: RoomScheduleDataSource): ScheduleDataSource {
        return roomDataSource
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGtfsDownloader(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient
    ): GtfsDownloader {
        return GtfsDownloader(context, httpClient)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGtfsParser(): GtfsParser {
        return GtfsParser()
    }

    @Provides
    @Singleton
    fun provideServiceResolver(): ServiceResolver {
        return ServiceResolver()
    }

    /**
     * Provides the user config. In Phase 2, this will come from DataStore.
     * For now, uses the default (Sunnyvale → San Francisco).
     */
    @Provides
    @Singleton
    fun provideUserConfig(): UserConfig {
        return UserConfig.DEFAULT
    }

    @Provides
    @Singleton
    fun provideDirectionResolver(userConfig: UserConfig): DirectionResolver {
        return DirectionResolver(userConfig)
    }

    @Provides
    @Singleton
    fun provideScheduleValidator(
        dataSource: ScheduleDataSource,
        serviceResolver: ServiceResolver
    ): ScheduleValidator {
        return ScheduleValidator(dataSource, serviceResolver)
    }

    @Provides
    @Singleton
    fun provideLookupEngine(
        dataSource: ScheduleDataSource,
        directionResolver: DirectionResolver,
        serviceResolver: ServiceResolver
    ): LookupEngine {
        return LookupEngine(dataSource, directionResolver, serviceResolver)
    }

    @Provides
    @Singleton
    fun provideLocationProvider(@ApplicationContext context: Context): LocationProvider {
        return LocationProvider(context)
    }
}
