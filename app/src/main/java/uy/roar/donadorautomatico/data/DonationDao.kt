package uy.roar.donadorautomatico.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class MonthlyTotal(
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "total") val total: Long
)

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

    @Query("SELECT substr(date, 1, 7) as month, SUM(amount) as total FROM donation_records GROUP BY substr(date, 1, 7) ORDER BY month DESC")
    suspend fun getMonthlyTotals(): List<MonthlyTotal>
}
