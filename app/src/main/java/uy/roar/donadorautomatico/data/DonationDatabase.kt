package uy.roar.donadorautomatico.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DonationRecord::class], version = 1, exportSchema = false)
abstract class DonationDatabase : RoomDatabase() {
    
    abstract fun donationDao(): DonationDao
    
    companion object {
        @Volatile
        private var INSTANCE: DonationDatabase? = null
        
        fun getDatabase(context: Context): DonationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DonationDatabase::class.java,
                    "donation_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
