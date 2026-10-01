package com.y3lc.timelogger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ActivityTypeEntity::class, ActivitySessionEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(TimeConverters::class)
abstract class TimeLoggerDatabase : RoomDatabase() {
    abstract fun activityTypeDao(): ActivityTypeDao

    abstract fun activitySessionDao(): ActivitySessionDao

    companion object {
        fun open(context: Context, name: String = "time_logger.db"): TimeLoggerDatabase =
            Room.databaseBuilder(context.applicationContext, TimeLoggerDatabase::class.java, name)
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE UNIQUE INDEX index_activity_sessions_one_active_type " +
                                    "ON activity_sessions(activityTypeId) WHERE endedAtUtc IS NULL",
                            )
                            db.execSQL(
                                "CREATE TRIGGER activity_sessions_valid_end_insert " +
                                    "BEFORE INSERT ON activity_sessions " +
                                    "WHEN NEW.endedAtUtc IS NOT NULL AND NEW.endedAtUtc <= NEW.startedAtUtc " +
                                    "BEGIN SELECT RAISE(ABORT, 'endedAtUtc must be after startedAtUtc'); END",
                            )
                            db.execSQL(
                                "CREATE TRIGGER activity_sessions_valid_end_update " +
                                    "BEFORE UPDATE ON activity_sessions " +
                                    "WHEN NEW.endedAtUtc IS NOT NULL AND NEW.endedAtUtc <= NEW.startedAtUtc " +
                                    "BEGIN SELECT RAISE(ABORT, 'endedAtUtc must be after startedAtUtc'); END",
                            )
                        }
                    },
                )
                .build()
    }
}
