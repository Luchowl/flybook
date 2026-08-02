package com.neonstick.flybook.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class FlightRepository(private val dao: FlightDao) {

    val allFlights: Flow<List<Flight>> = dao.observeAll()

    suspend fun getAll(): List<Flight> = dao.getAll()

    suspend fun add(flight: Flight) = dao.insert(flight)

    suspend fun addAll(flights: List<Flight>) = dao.insertAll(flights)

    suspend fun update(flight: Flight) = dao.update(flight)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun clear() = dao.clear()

    fun count(): Flow<Int> = dao.count()

    companion object {
        @Volatile
        private var instance: FlightRepository? = null

        fun get(context: Context): FlightRepository =
            instance ?: synchronized(this) {
                instance ?: FlightRepository(AppDatabase.get(context).flightDao()).also { instance = it }
            }
    }
}
