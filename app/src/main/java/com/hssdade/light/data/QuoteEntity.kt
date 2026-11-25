package com.hssdade.light.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
