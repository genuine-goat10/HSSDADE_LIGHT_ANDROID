package com.hssdade.light.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clients")
data class ClientEntity(@PrimaryKey(autoGenerate = true) val id:Long=0,val name:String,val phone:String,val email:String,val address:String)

@Entity(tableName = "quotes")
data class QuoteEntity(@PrimaryKey(autoGenerate = true) val id:Long=0,val clientName:String,val serviceName:String,val descriptionEs:String,val descriptionEn:String,val observations:String,val quantity:Double,val total:Double,val createdAt:Long=System.currentTimeMillis(),val syncStatus:String="LOCAL")

@Dao interface ClientDao { @Query("SELECT * FROM clients ORDER BY name") fun observeAll():Flow<List<ClientEntity>>; @Insert suspend fun insert(client:ClientEntity); @Delete suspend fun delete(client:ClientEntity) }
@Dao interface QuoteDao { @Query("SELECT * FROM quotes ORDER BY createdAt DESC") fun observeAll():Flow<List<QuoteEntity>>; @Insert suspend fun insert(quote:QuoteEntity) }

@Database(entities=[ClientEntity::class,QuoteEntity::class],version=1,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){ abstract fun clientDao():ClientDao; abstract fun quoteDao():QuoteDao
 companion object { @Volatile private var INSTANCE:AppDatabase?=null; fun get(context:Context):AppDatabase=INSTANCE?: synchronized(this){ INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"hssdade_local.db").build().also{INSTANCE=it} } } }
