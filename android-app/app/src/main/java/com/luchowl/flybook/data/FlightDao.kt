package com.luchowl.flybook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {
    @Query("SELECT * FROM flights ORDER BY flightDate DESC")
    fun observeAll(): Flow<List<Flight>>

    @Query("SELECT * FROM flights ORDER BY flightDate DESC")
    suspend fun getAll(): List<Flight>

    @Query("SELECT * FROM flights WHERE id = :id")
    suspend fun getById(id: String): Flight?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flight: Flight)

    @androidx.room.Transaction
    suspend fun insertAll(flights: List<Flight>) {
        for (flight in flights) insert(flight)
    }

    @Update
    suspend fun update(flight: Flight)

    @Query("DELETE FROM flights WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM flights")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM flights")
    fun count(): Flow<Int>
}
