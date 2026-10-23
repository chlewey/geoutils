package net.interlecto.geoutils.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.interlecto.geoutils.data.BundledBaseMaps
import net.interlecto.geoutils.data.local.BaseMapDao
import net.interlecto.geoutils.data.local.toDomain
import net.interlecto.geoutils.data.local.toEntity
import net.interlecto.geoutils.domain.model.BaseMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseMapRepository @Inject constructor(
    private val dao: BaseMapDao,
) {
    fun observeAll(): Flow<List<BaseMap>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun count(): Int = dao.count()

    /** Adds bundled base maps missing from the library; idempotent. */
    suspend fun ensureBundled() {
        for (baseMap in BundledBaseMaps.all) {
            if (dao.findByUri(baseMap.uri) == null) dao.upsert(baseMap.toEntity())
        }
    }

    suspend fun save(baseMap: BaseMap): Long = dao.upsert(baseMap.toEntity())

    suspend fun delete(baseMap: BaseMap) = dao.delete(baseMap.toEntity())
}
