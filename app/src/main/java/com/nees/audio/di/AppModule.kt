package com.nees.audio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nees.audio.data.dao.DeviceSettingsDao
import com.nees.audio.data.dao.DsPresetDao
import com.nees.audio.data.dao.EqPresetDao
import com.nees.audio.data.dao.PresetDao
import com.nees.audio.data.db.ViperDatabase
import com.nees.audio.data.model.DsPreset
import com.nees.audio.data.model.EqPreset
import com.nees.audio.viper.ViperDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "viper_preferences")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ViperDatabase {
        lateinit var db: ViperDatabase
        db =
            Room
                .databaseBuilder(
                    context,
                    ViperDatabase::class.java,
                    "viper4android.db",
                ).addMigrations(
                    ViperDatabase.MIGRATION_1_2,
                    ViperDatabase.MIGRATION_2_3,
                    ViperDatabase.MIGRATION_3_4,
                    ViperDatabase.MIGRATION_4_5,
                    ViperDatabase.MIGRATION_5_6,
                ).addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onCreate(sqDb: SupportSQLiteDatabase) {
                            super.onCreate(sqDb)
                            CoroutineScope(Dispatchers.IO).launch {
                                seedEqPresets(db.eqPresetDao())
                                seedDsPresets(db.dsPresetDao())
                            }
                        }

                        override fun onOpen(sqDb: SupportSQLiteDatabase) {
                            super.onOpen(sqDb)
                            CoroutineScope(Dispatchers.IO).launch {
                                val eqDao = db.eqPresetDao()
                                if (eqDao.countBuiltins() == 0) {
                                    seedEqPresets(eqDao)
                                }
                                val dsDao = db.dsPresetDao()
                                if (dsDao.countBuiltins() == 0) {
                                    seedDsPresets(dsDao)
                                }
                            }
                        }
                    },
                ).build()
        return db
    }

    private suspend fun seedEqPresets(dao: EqPresetDao) {
        val presets = mutableListOf<EqPreset>()
        for (builtin in ViperDispatcher.BUILTIN_EQ_PRESETS) {
            val bandsByCount =
                mapOf(
                    10 to builtin.bands10,
                    15 to builtin.bands15,
                    25 to builtin.bands25,
                    31 to builtin.bands31,
                )
            for ((bandCount, bands) in bandsByCount) {
                presets.add(
                    EqPreset(
                        name = builtin.key,
                        nameKey = builtin.key,
                        bandCount = bandCount,
                        bands = bands,
                    ),
                )
            }
        }
        dao.insertAll(presets)
    }

    private suspend fun seedDsPresets(dao: DsPresetDao) {
        val presets =
            ViperDispatcher.BUILTIN_DS_PRESETS.map { builtin ->
                DsPreset(
                    name = builtin.key,
                    nameKey = builtin.key,
                    xLow = builtin.xLow,
                    xHigh = builtin.xHigh,
                    yLow = builtin.yLow,
                    yHigh = builtin.yHigh,
                    sideGainLow = builtin.sideGainLow,
                    sideGainHigh = builtin.sideGainHigh,
                )
            }
        dao.insertAll(presets)
    }

    @Provides
    @Singleton
    fun providePresetDao(database: ViperDatabase): PresetDao = database.presetDao()

    @Provides
    @Singleton
    fun provideEqPresetDao(database: ViperDatabase): EqPresetDao = database.eqPresetDao()

    @Provides
    @Singleton
    fun provideDsPresetDao(database: ViperDatabase): DsPresetDao = database.dsPresetDao()

    @Provides
    @Singleton
    fun provideDeviceSettingsDao(database: ViperDatabase): DeviceSettingsDao = database.deviceSettingsDao()

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore
}
