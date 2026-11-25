package com.hssdade.light.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = ""
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientName: String,
    val clientPhone: String = "",
    val clientEmail: String = "",
    val clientAddress: String = "",
    val serviceName: String,
    val descriptionEs: String,
    val descriptionEn: String,
    val observations: String,
    val quantity: Double,
    val total: Double,
    val status: String = "Pendiente",
    val signatureData: String = "",
    val signedAt: Long = 0L,
    val photoUris: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY name ASC")
    suspend fun getAllOnce(): List<ClientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: ClientEntity): Long

    @Delete
    suspend fun delete(client: ClientEntity)
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<QuoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quote: QuoteEntity): Long

    @Update
    suspend fun update(quote: QuoteEntity)
}

@Database(entities = [ClientEntity::class, QuoteEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN clientPhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE quotes ADD COLUMN clientEmail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE quotes ADD COLUMN clientAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE quotes ADD COLUMN status TEXT NOT NULL DEFAULT 'Pendiente'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN signatureData TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE quotes ADD COLUMN signedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN photoUris TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "hssdade_light.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
