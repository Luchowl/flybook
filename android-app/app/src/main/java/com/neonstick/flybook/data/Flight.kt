package com.neonstick.flybook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flights")
data class Flight(
    @PrimaryKey val id: String,
    val flightDate: Long,          // epoch millis (UTC midnight)
    val flightNumber: String = "",
    val airlineIata: String = "",
    val airlineIcao: String = "",
    val airlineName: String = "",
    val departureIata: String = "",
    val departureIcao: String = "",
    val departureName: String = "",
    val departureCity: String = "",
    val departureCountry: String = "",
    val arrivalIata: String = "",
    val arrivalIcao: String = "",
    val arrivalName: String = "",
    val arrivalCity: String = "",
    val arrivalCountry: String = "",
    val aircraftType: String = "",
    val aircraftName: String = "",
    val registration: String = "",
    val seatNumber: String = "",
    val cabinClass: String = "",
    val notes: String = "",
    val depHour: Int = -1,
    val depMinute: Int = -1,
    val arrHour: Int = -1,
    val arrMinute: Int = -1,
    val durationMinutes: Int = 0,
    val distance: Double = 0.0,
)
