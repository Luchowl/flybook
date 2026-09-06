package com.luchowl.flybook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luchowl.flybook.data.Enricher
import com.luchowl.flybook.data.Flight
import com.luchowl.flybook.data.FlightRepository
import com.luchowl.flybook.data.ReferenceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlybookViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FlightRepository.get(application)

    val flights: StateFlow<List<Flight>> = repo.allFlights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refData = MutableStateFlow<ReferenceData?>(null)
    val refData: StateFlow<ReferenceData?> = _refData

    init {
        viewModelScope.launch {
            _refData.value = ReferenceData.get(application)
        }
    }

    private suspend fun ensureRef(): ReferenceData =
        _refData.value ?: ReferenceData.get(getApplication())

    fun addFlight(flight: Flight) {
        viewModelScope.launch {
            repo.add(Enricher.enrich(flight, ensureRef()))
        }
    }

    fun updateFlight(flight: Flight) {
        viewModelScope.launch {
            repo.update(Enricher.enrich(flight, ensureRef()))
        }
    }

    fun deleteFlight(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repo.clear() }
    }

    fun importFlights(flights: List<Flight>) {
        viewModelScope.launch {
            val ref = ensureRef()
            val enriched = withContext(Dispatchers.Default) {
                flights.map { Enricher.enrich(it, ref) }
            }
            repo.addAll(enriched)
        }
    }

    fun replaceAllFlights(flights: List<Flight>) {
        viewModelScope.launch {
            val ref = ensureRef()
            val enriched = withContext(Dispatchers.Default) {
                flights.map { Enricher.enrich(it, ref) }
            }
            repo.replaceAll(enriched)
        }
    }
}
