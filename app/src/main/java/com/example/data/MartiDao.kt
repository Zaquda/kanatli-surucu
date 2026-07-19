package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MartiDao {

    // --- Profile Queries ---
    @Query("SELECT * FROM driver_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM driver_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileSync(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    // --- Shift Queries ---
    @Query("SELECT * FROM shifts WHERE endTime IS NULL LIMIT 1")
    fun getActiveShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveShiftSync(): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity): Long

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE id = :id")
    fun getShiftById(id: Int): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY startTime DESC")
    fun getAllShifts(): Flow<List<ShiftEntity>>

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun deleteShiftById(id: Int)

    @Query("DELETE FROM shifts WHERE endTime IS NULL")
    suspend fun deleteActiveShift()

    // --- Trip Queries ---
    @Query("SELECT * FROM trips WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    fun getTripsForShift(shiftId: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    suspend fun getTripsForShiftSync(shiftId: Int): List<TripEntity>

    @Query("SELECT * FROM trips ORDER BY timestamp DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: Int)

    @Query("DELETE FROM trips WHERE shiftId = :shiftId")
    suspend fun deleteTripsForShift(shiftId: Int)

    @Query("DELETE FROM shifts")
    suspend fun clearAllShifts()

    @Query("DELETE FROM trips")
    suspend fun clearAllTrips()
}
