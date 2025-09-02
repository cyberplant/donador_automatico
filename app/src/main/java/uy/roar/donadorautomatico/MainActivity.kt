package uy.roar.donadorautomatico

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlinx.coroutines.*
import android.widget.ProgressBar

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 100
    private val RECEIVE_SMS_PERMISSION_CODE = 101
    private val CHANNELID = "donar_saldo_channel"
    private val PREF_NAME = "SmsSenderPrefs"
    private val REMINDER_ENABLED_KEY = "reminder_enabled"
    private val BALANCE_NUMBER = "226"

    // Map to store recipient name and phone number
    private val recipients = mapOf("Animales Sin Hogar" to "24200")
    private lateinit var recipientSpinner: Spinner
    private lateinit var reminderCheckbox: CheckBox
    private lateinit var balanceTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var smsReceiver: SmsReceiver
    private lateinit var donationAmountTextView: TextView
    private lateinit var delayInput: EditText
    private lateinit var clearButton: Button
    private lateinit var progressTextView: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        donationAmountTextView = findViewById(R.id.donationAmountTextView)
        delayInput = findViewById(R.id.delayInput)
        clearButton = findViewById(R.id.clearButton)
        progressTextView = findViewById(R.id.progressTextView)
        progressBar = findViewById(R.id.progressBar)

        // Set default delay value to 5 seconds
        delayInput.setText("5")

        // Initialize shared preferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Create notification channel for Android 8.0+
        createNotificationChannel()

        // Set up the spinner
        recipientSpinner = findViewById(R.id.recipientSpinner)
        val recipientNames = recipients.keys.toList()

        val adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, recipientNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        recipientSpinner.adapter = adapter

        val messageCountEditText = findViewById<EditText>(R.id.messageCount)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val checkBalanceButton = findViewById<Button>(R.id.checkBalanceButton)
        balanceTextView = findViewById(R.id.balanceTextView)
        reminderCheckbox = findViewById(R.id.reminderCheckbox)

        // Set checkbox state from saved preferences
        val reminderEnabled = sharedPreferences.getBoolean(REMINDER_ENABLED_KEY, false)
        reminderCheckbox.isChecked = reminderEnabled

        sendButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
            } else {
                sendSMS(messageCountEditText.text.toString())
            }
        }

        checkBalanceButton.setOnClickListener {
            // Request permissions if not granted
            if (checkAndRequestPermissions()) {
                // Send "SALDO" to number 226
                sendBalanceCheck()
            }
        }

        clearButton.setOnClickListener {
            clearEverything()
        }

        reminderCheckbox.setOnCheckedChangeListener { _, isChecked ->
            // Save checkbox state
            sharedPreferences.edit().putBoolean(REMINDER_ENABLED_KEY, isChecked).apply()

            if (isChecked) {
                // Schedule reminder
                scheduleMonthlyReminder()
                Toast.makeText(this, "Recordatorio mensual activado", Toast.LENGTH_SHORT).show()
            } else {
                // Cancel reminder
                cancelMonthlyReminder()
                Toast.makeText(this, "Recordatorio mensual desactivado", Toast.LENGTH_SHORT).show()
            }
        }

        // Schedule reminder if it was previously enabled
        if (reminderEnabled) {
            scheduleMonthlyReminder()
        }

        // Register SMS receiver
        smsReceiver = SmsReceiver()
        val filter = IntentFilter()
        filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        registerReceiver(smsReceiver, filter)

        // Initialize the donation amount from SharedPreferences
        val sharedPreferences = getSharedPreferences("donation_prefs", Context.MODE_PRIVATE)
        val currentAmount = sharedPreferences.getInt("donation_amount", 0)
        updateDonationUI(currentAmount)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister receiver when activity is destroyed
        unregisterReceiver(smsReceiver)
    }

    private fun checkAndRequestPermissions(): Boolean {
        var allPermissionsGranted = true

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
            allPermissionsGranted = false
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), RECEIVE_SMS_PERMISSION_CODE)
            allPermissionsGranted = false
        }

        return allPermissionsGranted
    }

    private fun sendBalanceCheck() {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(BALANCE_NUMBER, null, "SALDO", null, null)
            Toast.makeText(this, "Consultando saldo...", Toast.LENGTH_SHORT).show()
            balanceTextView.text = "Esperando respuesta..."
        } catch (e: Exception) {
            Toast.makeText(this, "Error al consultar saldo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSMS(countStr: String) {
        try {
            val count = countStr.toInt()
            val delaySeconds = delayInput.text.toString().toIntOrNull() ?: 5 // Default to 5 seconds

            // Get the selected recipient
            val selectedRecipientName = recipientSpinner.selectedItem.toString()
            val recipientNumber = recipients[selectedRecipientName]

            if (delaySeconds > 0) {
                // Send messages with delay
                sendSMSWithDelay(count, recipientNumber, selectedRecipientName, delaySeconds)
            } else {
                // Send messages immediately
                sendSMSImmediate(count, recipientNumber, selectedRecipientName)
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Por favor ingrese un número válido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSMSImmediate(count: Int, recipientNumber: String?, recipientName: String) {
        // Show progress UI
        showProgressUI(true)
        progressBar.max = count
        progressBar.progress = 0
        
        CoroutineScope(Dispatchers.Main).launch {
            val smsManager = SmsManager.getDefault()
            var sentCount = 0
            
            repeat(count) { index ->
                try {
                    smsManager.sendTextMessage(recipientNumber, null, "Hola desde $recipientName!", null, null)
                    sentCount++
                    
                    // Update progress UI
                    progressBar.progress = sentCount
                    progressTextView.text = "Enviando mensaje ${sentCount}/$count..."
                    
                    // Small delay to show progress even for immediate sending
                    delay(200)
                } catch (e: Exception) {
                    progressTextView.text = "Error enviando mensaje ${index + 1}: ${e.message}"
                    Toast.makeText(this@MainActivity, "Error enviando mensaje ${index + 1}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Hide progress UI and show completion message
            showProgressUI(false)
            Toast.makeText(this@MainActivity, "$count mensajes enviados a $recipientName!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSMSWithDelay(count: Int, recipientNumber: String?, recipientName: String, delaySeconds: Int) {
        // Show progress UI
        showProgressUI(true)
        progressBar.max = count
        progressBar.progress = 0
        
        CoroutineScope(Dispatchers.Main).launch {
            val smsManager = SmsManager.getDefault()
            var sentCount = 0
            
            repeat(count) { index ->
                try {
                    smsManager.sendTextMessage(recipientNumber, null, "Hola desde $recipientName!", null, null)
                    sentCount++
                    
                    // Update progress UI
                    progressBar.progress = sentCount
                    progressTextView.text = "Enviando mensaje ${sentCount}/$count..."
                    
                    // Wait for delay (except for the last message)
                    if (index < count - 1) {
                        delay(delaySeconds * 1000L)
                    }
                } catch (e: Exception) {
                    progressTextView.text = "Error enviando mensaje ${index + 1}: ${e.message}"
                    Toast.makeText(this@MainActivity, "Error enviando mensaje ${index + 1}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Hide progress UI and show completion message
            showProgressUI(false)
            Toast.makeText(this@MainActivity, "$count mensajes enviados a $recipientName con delay de ${delaySeconds}s!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearEverything() {
        // Clear donation amount
        val sharedPreferences = getSharedPreferences("donation_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("donation_amount", 0).apply()
        sharedPreferences.edit().putInt("response_count", 0).apply()
        
        // Clear message count input
        val messageCountEditText = findViewById<EditText>(R.id.messageCount)
        messageCountEditText.setText("")
        
        // Clear delay input
        delayInput.setText("")
        
        // Clear balance text
        balanceTextView.text = ""
        
        // Update donation UI
        updateDonationUI(0)
        
        // Hide progress UI
        showProgressUI(false)
        
        Toast.makeText(this, "Todo ha sido limpiado", Toast.LENGTH_SHORT).show()
    }

    private fun showProgressUI(show: Boolean) {
        if (show) {
            progressTextView.visibility = android.view.View.VISIBLE
            progressBar.visibility = android.view.View.VISIBLE
        } else {
            progressTextView.visibility = android.view.View.GONE
            progressBar.visibility = android.view.View.GONE
            progressTextView.text = ""
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de Donación de Saldo"
            val descriptionText = "Recordatorios para donar el último día de cada mes"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNELID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleMonthlyReminder() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Set alarm to trigger on the last day of each month
        val calendar = Calendar.getInstance()
        // Set to last day of current month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        // Set time to 10:00 AM
        calendar.set(Calendar.HOUR_OF_DAY, 10)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        // If the time is already past today, schedule for next month
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.MONTH, 1)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        // Schedule a repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY * 30, // Approximate monthly interval
            pendingIntent
        )
    }

    private fun cancelMonthlyReminder() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE || requestCode == RECEIVE_SMS_PERMISSION_CODE) {
            // Check if all permissions are granted
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (checkAndRequestPermissions()) {
                    // If this was from the check balance button, send the SMS
                    if (requestCode == RECEIVE_SMS_PERMISSION_CODE) {
                        sendBalanceCheck()
                    }
                }
            } else {
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // BroadcastReceiver for handling the monthly reminder
    class ReminderReceiver : BroadcastReceiver() {
        private val CHANNELID = "donar_saldo_channel"

        override fun onReceive(context: Context, intent: Intent) {
            // Create and show notification
            val builder = NotificationCompat.Builder(context, CHANNELID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Recordatorio: Donar Saldo")
                .setContentText("Hoy es el último día del mes. ¡Recuerda donar!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)

            // Check for notification permission on Android 13+
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1, builder.build())
            }

            // Reschedule for next month
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, 1)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            calendar.set(Calendar.HOUR_OF_DAY, 10)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    // BroadcastReceiver for SMS response
    inner class SmsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
                for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    val sender = smsMessage.originatingAddress
                    val message = smsMessage.messageBody

                    // Check if the message is from the balance number
                    if (sender != null) {
                        if (sender.contains(BALANCE_NUMBER)) {
                            // Update UI with the message
                            updateBalance(message)
                        } else if (sender.contains("24200")) {
                            // Check if it's a confirmation message
                            if (message.startsWith("Gracias por colaborar")) {
                                // Increment donation count by 10
                                incrementDonationCount(context, 10)
                                Toast.makeText(context, "¡Donación confirmada! +10 agregado", Toast.LENGTH_SHORT).show()
                            } else {
                                // Regular response message
                                incrementResponseCount(context)
                            }
                        }
                    }
                }
            }
        }
    }
    private fun incrementResponseCount(context: Context) {
        // Retrieve the current count from SharedPreferences
        val sharedPreferences = context.getSharedPreferences("donation_prefs", Context.MODE_PRIVATE)
        val currentCount = sharedPreferences.getInt("response_count", 0)

        // Increment the count
        val newCount = currentCount + 1

        // Save the new count
        with(sharedPreferences.edit()) {
            putInt("response_count", newCount)
            apply()
        }

        // Update the UI
        updateDonationUI(newCount)
    }

    private fun incrementDonationCount(context: Context, amount: Int) {
        // Retrieve the current donation amount from SharedPreferences
        val sharedPreferences = context.getSharedPreferences("donation_prefs", Context.MODE_PRIVATE)
        val currentAmount = sharedPreferences.getInt("donation_amount", 0)

        // Increment the amount
        val newAmount = currentAmount + amount

        // Save the new amount
        with(sharedPreferences.edit()) {
            putInt("donation_amount", newAmount)
            apply()
        }

        // Update the UI
        updateDonationUI(newAmount)
    }

    private fun updateDonationUI(amount: Int) {
        val donationText = "Cantidad donada: $amount"
        donationAmountTextView.text = donationText
    }

    private fun updateBalance(message: String) {
        runOnUiThread {
            try {
                // Display the full message
                balanceTextView.text = message

                // Extract the balance amount from the message
                if (message.contains("Dispones de:")) {
                    val balanceStr = message.substring(message.indexOf("Dispones de:") + "Dispones de:".length).trim()
                    // Find the first numeric sequence
                    var numericPart = ""
                    for (c in balanceStr) {
                        if (c.isDigit() || c == '.') {
                            numericPart += c
                        } else if (numericPart.isNotEmpty()) {
                            // Stop once we've found the numeric part and reached a non-numeric character
                            break
                        }
                    }

                    if (numericPart.isNotEmpty()) {
                        // Parse the numeric part and divide by 10
                        val balance = numericPart.toDouble()
                        val smsCount = (balance / 10).toInt()

                        // Set the SMS count in the EditText
                        val messageCountEditText = findViewById<EditText>(R.id.messageCount)
                        messageCountEditText.setText(smsCount.toString())

                        // Also show in the balance text view
                        balanceTextView.text = "$message\n\nCantidad de SMS sugerida: $smsCount (Saldo $numericPart ÷ 10)"
                    }
                }
            } catch (e: Exception) {
                balanceTextView.text = "Error al analizar saldo: ${e.message}"
            }
        }
    }
}