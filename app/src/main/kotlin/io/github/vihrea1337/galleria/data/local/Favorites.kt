package io.github.vihrea1337.galleria.data.local

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
)

fun Artwork.toFavorite(now: Long) = FavoriteEntity(id, title, artist, date, thumbUrl, imageUrl, zoomUrl, aspectRatio, placeholder, dominantColor, altText, now)

fun FavoriteEntity.toArtwork() = Artwork(id, title, artist, date, thumbUrl, imageUrl, zoomUrl, aspectRatio, placeholder, dominantColor, altText)

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

@Database(entities = [FavoriteEntity::class], version = 1, exportSchema = true)
abstract class GalleriaDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
}
