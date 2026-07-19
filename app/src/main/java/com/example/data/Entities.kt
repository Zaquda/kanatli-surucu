package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "driver_profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val driverName: String,
    val carModel: String,
    val fuelType: String, // Benzin, Dizel, LPG, Elektrik
    val fuelPrice: Double, // Litre/kWh fiyatı (₺)
    val avgConsumption: Double, // 100 km'de tüketim (L veya kWh)
    val appPassword: String = "", // Empty means no password lock
    val themeMode: String = "AUTO", // "LIGHT", "DARK", "AUTO"
    val shiftStartSoundUri: String? = null,
    val shiftEndSoundUri: String? = null
)

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val isPaused: Boolean = false,
    val lastPauseTimestamp: Long = 0L,
    val accumulatedPauseMillis: Long = 0L,
    val totalGpsKm: Double = 0.0,
    val generalExpenses: Double = 0.0, // Daily general expenses other than fuel
    val generalExpensesDescription: String = "" // Description of general expenses
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val shiftId: Int,
    val distanceKm: Double,
    val earnings: Double,
    val paymentMethod: String, // "Nakit" veya "K.Kartı"
    val timestamp: Long
)

