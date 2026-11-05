package com.example.ratecalc

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis()
)

// FIX: products now cascade-delete with their profile (no orphan rows, no manual second DELETE).
@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(
        entity = Profile::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId")]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val rate: Double,
    val cc: Double = 0.0,
    val pot: Double = 0.0,
    val soldUnit: String = "Normal",
    val quantity: Int = 0
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY lastAccessed DESC")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY lastAccessed DESC LIMIT 1")
    suspend fun latest(): Profile?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(p: Profile): Long

    @Query("UPDATE profiles SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE profiles SET lastAccessed = :t WHERE id = :id")
    suspend fun touch(id: Long, t: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE profileId = :pid ORDER BY name COLLATE NOCASE ASC")
    fun observe(pid: Long): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE profileId = :pid AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(pid: Long, name: String): Product?

    @Insert
    suspend fun insert(p: Product): Long

    @Update
    suspend fun update(p: Product)

    @Delete
    suspend fun delete(p: Product)

    @Query("UPDATE products SET quantity = :q WHERE id = :id")
    suspend fun setQty(id: Long, q: Int)

    @Query("UPDATE products SET quantity = 0 WHERE profileId = :pid")
    suspend fun clearQty(pid: Long)

    @Query("SELECT * FROM products WHERE profileId = :pid ORDER BY name COLLATE NOCASE ASC")
    suspend fun all(pid: Long): List<Product>
}

@Database(entities = [Profile::class, Product::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ratecalc.db")
                .build().also { instance = it }
        }
    }
}
