package com.hssdade.light.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
