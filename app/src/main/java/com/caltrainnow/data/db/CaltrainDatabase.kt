package com.caltrainnow.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.caltrainnow.data.db.dao.*

@Database(
    entities = [
        StationEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        RouteEntity::class,
        ServiceCalendarEntity::class,
        ServiceExceptionEntity::class,
        ScheduleMetadataEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CaltrainDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun tripDao(): TripDao
    abstract fun stopTimeDao(): StopTimeDao
    abstract fun routeDao(): RouteDao
    abstract fun serviceDao(): ServiceDao
    abstract fun metadataDao(): MetadataDao
}
