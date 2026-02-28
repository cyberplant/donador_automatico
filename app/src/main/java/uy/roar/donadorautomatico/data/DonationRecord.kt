package uy.roar.donadorautomatico.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "donation_records")
data class DonationRecord(
    @PrimaryKey
    val date: String, // Format: "yyyy-MM-dd"
    val amount: Int // Amount in pesos donated that day
)
