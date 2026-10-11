package io.github.vihrea1337.galleria.data.local

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import io.github.vihrea1337.galleria.model.Artwork
import kotlinx.coroutines.flow.Flow

/**
 * Избранное хранится целиком (название, автор, адреса, цвет, превью) — открывается без сети
 * и не зависит от того, что потом вернёт API.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String?,
    val date: String?,
    val thumbUrl: String,
    val imageUrl: String,
    val zoomUrl: String,
    val aspectRatio: Float,
    val placeholder: ByteArray?,
    val dominantColor: Long?,
    val altText: String?,
    /** Когда добавили — свежие сверху. */
    val addedAt: Long,
    // Версия 2 базы: для глубокого зума (автомиграция, у старых записей — null).
    val imageId: String? = null,
    val fullWidth: Int? = null,
    val fullHeight: Int? = null,
)

fun Artwork.toFavorite(now: Long) = FavoriteEntity(
    id, title, artist, date, thumbUrl, imageUrl, zoomUrl, aspectRatio, placeholder, dominantColor, altText, now,
    imageId, fullWidth, fullHeight,
)

fun FavoriteEntity.toArtwork() = Artwork(
    id, title, artist, date, thumbUrl, imageUrl, zoomUrl, aspectRatio, placeholder, dominantColor, altText,
    imageId, fullWidth, fullHeight,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    fun observeIsFavorite(id: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * Версия 2 добавила колонки для глубокого зума. AutoMigration — Room сам сравнивает схемы
 * из app/schemas (поэтому exportSchema = true и схемы лежат в git).
 */
@Database(
    entities = [FavoriteEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class GalleriaDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
}
