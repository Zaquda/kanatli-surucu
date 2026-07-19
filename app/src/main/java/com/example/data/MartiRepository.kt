package com.example.data

import kotlinx.coroutines.flow.Flow

class MartiRepository(private val dao: MartiDao) {

    // --- Profile ---
    fun getProfile(): Flow<ProfileEntity?> = dao.getProfile()
    suspend fun getProfileSync(): ProfileEntity? = dao.getProfileSync()
    suspend fun saveProfile(profile: ProfileEntity) = dao.insertProfile(profile)

    // --- Shift ---
    fun getActiveShift(): Flow<ShiftEntity?> = dao.getActiveShift()
    suspend fun getActiveShiftSync(): ShiftEntity? = dao.getActiveShiftSync()

    suspend fun startShift(): Long {
        val active = dao.getActiveShiftSync()
        if (active != null) return active.id.toLong() // Already has an active shift
        
        val newShift = ShiftEntity(
            startTime = System.currentTimeMillis()
        )
        return dao.insertShift(newShift)
    }

    suspend fun pauseShift() {
        val active = dao.getActiveShiftSync() ?: return
        if (active.isPaused) return
        
        val updated = active.copy(
            isPaused = true,
            lastPauseTimestamp = System.currentTimeMillis()
        )
        dao.updateShift(updated)
    }

    suspend fun resumeShift() {
        val active = dao.getActiveShiftSync() ?: return
        if (!active.isPaused) return
        
        val now = System.currentTimeMillis()
        val elapsedPause = if (active.lastPauseTimestamp > 0L) now - active.lastPauseTimestamp else 0L
        val updated = active.copy(
            isPaused = false,
            lastPauseTimestamp = 0L,
            accumulatedPauseMillis = active.accumulatedPauseMillis + elapsedPause
        )
        dao.updateShift(updated)
    }

    suspend fun updateShiftDistance(distance: Double) {
        val active = dao.getActiveShiftSync() ?: return
        val updated = active.copy(totalGpsKm = distance)
        dao.updateShift(updated)
    }

    suspend fun updateShift(shift: ShiftEntity) {
        dao.updateShift(shift)
    }

    suspend fun endShift() {
        val active = dao.getActiveShiftSync() ?: return
        val now = System.currentTimeMillis()
        
        var accumPause = active.accumulatedPauseMillis
        if (active.isPaused && active.lastPauseTimestamp > 0L) {
            accumPause += (now - active.lastPauseTimestamp)
        }
        
        val updated = active.copy(
            endTime = now,
            isPaused = false,
            lastPauseTimestamp = 0L,
            accumulatedPauseMillis = accumPause
        )
        dao.updateShift(updated)
    }

    suspend fun deleteActiveShift() {
        val active = dao.getActiveShiftSync() ?: return
        dao.deleteTripsForShift(active.id)
        dao.deleteActiveShift()
    }

    fun getAllShifts(): Flow<List<ShiftEntity>> = dao.getAllShifts()

    suspend fun deleteShift(shiftId: Int) {
        dao.deleteTripsForShift(shiftId)
        dao.deleteShiftById(shiftId)
    }

    // --- Trips ---
    fun getTripsForShift(shiftId: Int): Flow<List<TripEntity>> = dao.getTripsForShift(shiftId)
    suspend fun getTripsForShiftSync(shiftId: Int): List<TripEntity> = dao.getTripsForShiftSync(shiftId)
    fun getAllTrips(): Flow<List<TripEntity>> = dao.getAllTrips()

    suspend fun addTrip(trip: TripEntity): Long = dao.insertTrip(trip)
    suspend fun updateTrip(trip: TripEntity) = dao.updateTrip(trip)
    suspend fun deleteTrip(tripId: Int) = dao.deleteTripById(tripId)

    suspend fun clearAllData() {
        dao.clearAllShifts()
        dao.clearAllTrips()
    }
}
