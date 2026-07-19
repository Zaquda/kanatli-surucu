package com.example.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.LocationTracker
import com.example.utils.SoundHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MartiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MartiDatabase.getDatabase(application)
    val repository = MartiRepository(database.martiDao())
    val locationTracker = LocationTracker.getInstance(application)

    // --- State Expositions ---
    val profile: StateFlow<ProfileEntity?> = repository.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeShift: StateFlow<ShiftEntity?> = repository.getActiveShift()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allShifts: StateFlow<List<ShiftEntity>> = repository.getAllShifts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTrips: StateFlow<List<TripEntity>> = repository.getAllTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Shift State ---
    private val _currentShiftTrips = MutableStateFlow<List<TripEntity>>(emptyList())
    val currentShiftTrips: StateFlow<List<TripEntity>> = _currentShiftTrips.asStateFlow()

    private val _elapsedShiftSeconds = MutableStateFlow(0L)
    val elapsedShiftSeconds: StateFlow<Long> = _elapsedShiftSeconds.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun setUnlocked(unlocked: Boolean) {
        _isUnlocked.value = unlocked
    }

    private var timerJob: Job? = null
    private var locationObserverJob: Job? = null

    init {
        // Ensure background service is started so overlay is always visible
        try {
            val app = getApplication<Application>()
            val serviceIntent = Intent(app, com.example.services.MartiBackgroundService::class.java).apply {
                action = com.example.services.MartiBackgroundService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(serviceIntent)
            } else {
                app.startService(serviceIntent)
            }
        } catch (e: Exception) {}

        // Observe active shift and start tracking GPS / Timer
        viewModelScope.launch {
            activeShift.collect { shift ->
                if (shift != null) {
                    // Update active trips list
                    launch {
                        repository.getTripsForShift(shift.id).collect { trips ->
                            _currentShiftTrips.value = trips
                        }
                    }
                    
                    // Start background service FIRST to ensure AppOps allows background location
                    try {
                        val app = getApplication<Application>()
                        val serviceIntent = Intent(app, com.example.services.MartiBackgroundService::class.java).apply {
                            action = com.example.services.MartiBackgroundService.ACTION_START_SERVICE
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            app.startForegroundService(serviceIntent)
                        } else {
                            app.startService(serviceIntent)
                        }
                    } catch (e: Exception) {}

                    // Setup Location Tracker starting distance
                    if (shift.totalGpsKm > locationTracker.totalDistanceKm.value) {
                        locationTracker.resetDistance(shift.totalGpsKm)
                    }
                    if (!shift.isPaused) {
                        locationTracker.startTracking()
                        startTimer(shift)
                    } else {
                        locationTracker.stopTracking()
                        stopTimer()
                        calculateStaticElapsed(shift)
                    }

                    // Periodically sync tracked distance to database
                    observeLocationDistance()
                } else {
                    locationTracker.stopTracking()
                    stopTimer()
                    stopLocationObservation()
                    _currentShiftTrips.value = emptyList()
                    _elapsedShiftSeconds.value = 0L
                    
                    // We keep the background service running for the floating widget
                }
            }
        }

        // Initialize default profile if empty
        viewModelScope.launch {
            val existing = repository.getProfileSync()
            if (existing == null) {
                repository.saveProfile(
                    ProfileEntity(
                        driverName = "Kanatlı Sürücü",
                        carModel = "Fiat Egea",
                        fuelType = "Benzin",
                        fuelPrice = 63.20,
                        avgConsumption = 7.2,
                        appPassword = "",
                        themeMode = "AUTO"
                    )
                )
            }
        }
    }

    private fun startTimer(shift: ShiftEntity) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val activeTime = now - shift.startTime - shift.accumulatedPauseMillis
                _elapsedShiftSeconds.value = maxOf(0L, activeTime / 1000L)
                delay(1000)
            }
        }
    }

    private fun calculateStaticElapsed(shift: ShiftEntity) {
        val now = if (shift.isPaused) shift.lastPauseTimestamp else System.currentTimeMillis()
        val activeTime = now - shift.startTime - shift.accumulatedPauseMillis
        _elapsedShiftSeconds.value = maxOf(0L, activeTime / 1000L)
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun observeLocationDistance() {
        locationObserverJob?.cancel()
        locationObserverJob = viewModelScope.launch {
            locationTracker.totalDistanceKm.collect { dist ->
                val active = repository.getActiveShiftSync()
                if (active != null && dist > active.totalGpsKm) {
                    repository.updateShiftDistance(dist)
                }
            }
        }
    }

    private fun stopLocationObservation() {
        locationObserverJob?.cancel()
        locationObserverJob = null
    }

    // --- Profile Operations ---
    fun saveProfile(name: String, car: String, fuel: String, price: Double, consumption: Double, password: String, themeMode: String = "AUTO", shiftStartSoundUri: String? = null, shiftEndSoundUri: String? = null) {
        viewModelScope.launch {
            val current = repository.getProfileSync()
            repository.saveProfile(
                ProfileEntity(
                    id = current?.id ?: 1,
                    driverName = name,
                    carModel = car,
                    fuelType = fuel,
                    fuelPrice = price,
                    avgConsumption = consumption,
                    appPassword = password,
                    themeMode = themeMode,
                    shiftStartSoundUri = shiftStartSoundUri ?: current?.shiftStartSoundUri,
                    shiftEndSoundUri = shiftEndSoundUri ?: current?.shiftEndSoundUri
                )
            )
        }
    }

    fun updateFuelPriceOnly(price: Double) {
        viewModelScope.launch {
            val current = repository.getProfileSync()
            if (current != null) {
                repository.saveProfile(current.copy(fuelPrice = price))
            } else {
                repository.saveProfile(
                    ProfileEntity(
                        driverName = "Kanatlı Sürücü",
                        carModel = "Fiat Egea",
                        fuelType = "Benzin",
                        fuelPrice = price,
                        avgConsumption = 7.2,
                        appPassword = "",
                        themeMode = "AUTO"
                    )
                )
            }
        }
    }

    // --- Shift Operations ---
    fun startNewShift() {
        viewModelScope.launch {
            locationTracker.resetDistance(0.0)
            repository.startShift()
            val app = getApplication<Application>()
            SoundHelper.playStartShiftSound(app, profile.value?.shiftStartSoundUri)

            // Start background service
            try {
                val serviceIntent = Intent(app, com.example.services.MartiBackgroundService::class.java).apply {
                    action = com.example.services.MartiBackgroundService.ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } catch (e: Exception) {}
        }
    }

    fun pauseActiveShift() {
        viewModelScope.launch {
            repository.pauseShift()
        }
    }

    fun resumeActiveShift() {
        viewModelScope.launch {
            repository.resumeShift()
        }
    }

    fun endActiveShift() {
        viewModelScope.launch {
            repository.endShift()
            val app = getApplication<Application>()
            SoundHelper.playEndShiftSound(app, profile.value?.shiftEndSoundUri)
        }
    }

    fun deleteCurrentShiftData() {
        viewModelScope.launch {
            repository.deleteActiveShift()
        }
    }

    // --- Expense Operations ---
    fun addExpenseToActiveShift(amount: Double, description: String) {
        viewModelScope.launch {
            val active = repository.getActiveShiftSync() ?: return@launch
            val desc = description.trim()
            val finalDescription = if (desc.isNotEmpty()) {
                val formattedExpense = "$desc (₺${String.format(java.util.Locale.US, "%.1f", amount)})"
                if (active.generalExpensesDescription.isEmpty()) {
                    formattedExpense
                } else {
                    "${active.generalExpensesDescription}, $formattedExpense"
                }
            } else {
                active.generalExpensesDescription
            }
            val updated = active.copy(
                generalExpenses = active.generalExpenses + amount,
                generalExpensesDescription = finalDescription
            )
            repository.updateShift(updated)
        }
    }

    // --- Trip Operations ---
    fun addTripToActiveShift(distanceKm: Double, earnings: Double, paymentMethod: String) {
        viewModelScope.launch {
            val active = repository.getActiveShiftSync() ?: return@launch
            val trip = TripEntity(
                shiftId = active.id,
                distanceKm = distanceKm,
                earnings = earnings,
                paymentMethod = paymentMethod,
                timestamp = System.currentTimeMillis()
            )
            repository.addTrip(trip)
        }
    }

    fun updateTrip(trip: TripEntity) {
        viewModelScope.launch {
            repository.updateTrip(trip)
        }
    }

    fun deleteTrip(tripId: Int) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
        }
    }

    // --- Historical Changes ---
    fun deleteHistoricalShift(shiftId: Int) {
        viewModelScope.launch {
            repository.deleteShift(shiftId)
        }
    }

    fun updateHistoricalShift(shift: ShiftEntity) {
        viewModelScope.launch {
            repository.updateShift(shift)
        }
    }

    fun resetPasswordAndClearAllData(newPassword: String) {
        viewModelScope.launch {
            locationTracker.stopTracking()
            stopTimer()
            stopLocationObservation()
            
            repository.clearAllData()
            
            val currentProfile = repository.getProfileSync()
            if (currentProfile != null) {
                repository.saveProfile(currentProfile.copy(appPassword = newPassword))
            } else {
                repository.saveProfile(
                    ProfileEntity(
                        driverName = "Kanatlı Sürücü",
                        carModel = "Fiat Egea",
                        fuelType = "Benzin",
                        fuelPrice = 63.20,
                        avgConsumption = 7.2,
                        appPassword = newPassword,
                        themeMode = "AUTO"
                    )
                )
            }
            setUnlocked(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do not stop location tracking here! It should continue in the background if a shift is active.
        stopTimer()
        stopLocationObservation()
    }
}



