package uy.roar.donadorautomatico.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DonationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: DonationRecord)
    
    @Query("SELECT * FROM donation_records WHERE date = :date")
    suspend fun getByDate(date: String): DonationRecord?
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM donation_records WHERE date = :date")
    suspend fun getTodayTotal(date: String): Int
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM donation_records WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getMonthTotal(startDate: String, endDate: String): Int
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM donation_records")
    suspend fun getAllTimeTotal(): Int
    
    @Query("UPDATE donation_records SET amount = amount + :amount WHERE date = :date")
    suspend fun addToDate(date: String, amount: Int): Int
}
