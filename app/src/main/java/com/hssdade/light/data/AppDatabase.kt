package com.hssdade.light.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val email: String,
    val address: String
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
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "LOCAL"
)

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name")
    fun observeAll(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY name")
    suspend fun getAllOnce(): List<ClientEntity>

    @Insert
    suspend fun insert(client: ClientEntity)

    @Delete
    suspend fun delete(client: ClientEntity)
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<QuoteEntity>

    @Insert
    suspend fun insert(quote: QuoteEntity)

    @Update
    suspend fun update(quote: QuoteEntity)
}

@Database(entities = [ClientEntity::class, QuoteEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN clientPhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE quotes ADD COLUMN clientEmail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE quotes ADD COLUMN status TEXT NOT NULL DEFAULT 'Pendiente'")
                db.execSQL("ALTER TABLE quotes ADD COLUMN signatureData TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE quotes ADD COLUMN signedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN clientAddress TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hssdade_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
