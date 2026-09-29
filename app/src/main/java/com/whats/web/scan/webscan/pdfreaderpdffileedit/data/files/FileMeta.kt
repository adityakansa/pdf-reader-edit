package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** FR-016 / FR-017. The only thing this app stores about a file is the user's own marks on it. */
@Entity(tableName = "file_meta")
data class FileMetaEntity(
    @PrimaryKey val key: String,
    val favourite: Boolean = false,
    val favouritedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
)

@Dao
interface FileMetaDao {
    @Query("SELECT * FROM file_meta")
    fun all(): Flow<List<FileMetaEntity>>

    @Query("SELECT * FROM file_meta WHERE `key` = :key")
    suspend fun find(key: String): FileMetaEntity?

    @Query(
        "INSERT INTO file_meta (`key`, favourite, favouritedAt, lastOpenedAt) VALUES (:key, :favourite, :favouritedAt, 0) " +
            "ON CONFLICT(`key`) DO UPDATE SET favourite = :favourite, favouritedAt = :favouritedAt",
    )
    suspend fun setFavourite(key: String, favourite: Boolean, favouritedAt: Long)

    @Query(
        "INSERT INTO file_meta (`key`, favourite, favouritedAt, lastOpenedAt) VALUES (:key, 0, 0, :openedAt) " +
            "ON CONFLICT(`key`) DO UPDATE SET lastOpenedAt = :openedAt",
    )
    suspend fun markOpened(key: String, openedAt: Long)

    @Query("DELETE FROM file_meta WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Database(entities = [FileMetaEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileMetaDao(): FileMetaDao
}
