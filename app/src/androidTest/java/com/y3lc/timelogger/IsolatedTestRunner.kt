package com.y3lc.timelogger

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.runner.AndroidJUnitRunner
import java.io.File
import java.util.UUID

class IsolatedTestRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(classLoader, IsolatedTestApplication::class.java.name, context)
}

class IsolatedTestApplication : Application() {
    private var storagePrefix = "ui-test-${UUID.randomUUID()}-"

    fun beginIsolatedTest() {
        storagePrefix = "ui-test-${UUID.randomUUID()}-"
    }

    fun deleteTestStorage() {
        super.deleteDatabase(storagePrefix + "time_logger.db")
        super.deleteSharedPreferences(storagePrefix + "time_logger_settings")
    }

    override fun getDatabasePath(name: String): File = super.getDatabasePath(getIsolatedDatabaseName(name))

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        super.openOrCreateDatabase(getIsolatedDatabaseName(name), mode, factory)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase = super.openOrCreateDatabase(getIsolatedDatabaseName(name), mode, factory, errorHandler)

    override fun deleteDatabase(name: String): Boolean = super.deleteDatabase(getIsolatedDatabaseName(name))

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(if (name == "time_logger_settings") storagePrefix + name else name, mode)

    private fun getIsolatedDatabaseName(name: String): String =
        if (name == "time_logger.db") storagePrefix + name else name
}
