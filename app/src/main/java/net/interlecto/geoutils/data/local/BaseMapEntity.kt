package net.interlecto.geoutils.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.interlecto.geoutils.domain.model.BaseMap

@Entity(tableName = "base_maps")
data class BaseMapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val uri: String,
    val attribution: String?,
    @ColumnInfo(name = "width_px") val widthPx: Int,
    @ColumnInfo(name = "height_px") val heightPx: Int,
)

fun BaseMapEntity.toDomain() = BaseMap(
    id = id,
    name = name,
    uri = uri,
    attribution = attribution,
    widthPx = widthPx,
    heightPx = heightPx,
)

fun BaseMap.toEntity() = BaseMapEntity(
    id = id,
    name = name,
    uri = uri,
    attribution = attribution,
    widthPx = widthPx,
    heightPx = heightPx,
)
