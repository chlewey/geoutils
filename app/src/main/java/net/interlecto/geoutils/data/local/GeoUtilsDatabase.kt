package net.interlecto.geoutils.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App database. Schemas are exported to `app/schemas`; every version bump
 * needs a migration (auto-migration where possible), never a destructive one.
 */
@Database(
    entities = [BaseMapEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GeoUtilsDatabase : RoomDatabase() {
    abstract fun baseMapDao(): BaseMapDao

    companion object {
        const val NAME = "geoutils.db"
    }
}
