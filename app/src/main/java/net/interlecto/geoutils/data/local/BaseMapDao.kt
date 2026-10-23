package net.interlecto.geoutils.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BaseMapDao {

    @Query("SELECT * FROM base_maps ORDER BY name")
    fun observeAll(): Flow<List<BaseMapEntity>>

    @Query("SELECT COUNT(*) FROM base_maps")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(baseMap: BaseMapEntity): Long

    @Delete
    suspend fun delete(baseMap: BaseMapEntity)
}
