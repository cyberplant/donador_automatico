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
import android.os.Handler
import android.os.Looper
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
import uy.roar.donadorautomatico.data.DonationDatabase
import uy.roar.donadorautomatico.data.DonationRecord
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 100
    private val RECEIVE_SMS_PERMISSION_CODE = 101
    private val NOTIFICATION_PERMISSION_CODE = 102
    private val CHANNELID = "donar_saldo_channel"
    private val PREF_NAME = "SmsSenderPrefs"
    private val REMINDER_4_DAYS_KEY = "reminder_4_days"
    private val REMINDER_3_DAYS_KEY = "reminder_3_days"
    private val REMINDER_PENULTIMATE_KEY = "reminder_penultimate"
    private val REMINDER_LAST_DAY_KEY = "reminder_last_day"
    private val BALANCE_NUMBER = "226"
    private val LAST_MONTH_KEY = "last_month"
    private val LAST_BALANCE_KEY = "last_balance"
    private val CONFIRMED_AT_BASELINE_KEY = "confirmed_at_baseline"
    private val CONFIRMED_THIS_SESSION_KEY = "confirmed_this_session"
    private val SENT_THIS_SESSION_KEY = "sent_this_session"
    private val MAX_MESSAGES = 50
    private val DEFAULT_DELAY = 2

    // Map to store recipient name and phone number
    private val recipients = mapOf("Animales Sin Hogar" to "24200")
    private lateinit var recipientSpinner: Spinner
    private lateinit var reminder4DaysCheckbox: CheckBox
    private lateinit var reminder3DaysCheckbox: CheckBox
    private lateinit var reminderPenultimateCheckbox: CheckBox
    private lateinit var reminderLastDayCheckbox: CheckBox
    private lateinit var balanceTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var smsReceiver: SmsReceiver
    private lateinit var donationAmountTextView: TextView
    private lateinit var delayInput: EditText
    private lateinit var messageCountInput: EditText
    private lateinit var clearButton: Button
    private lateinit var progressTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sentMessagesTextView: TextView
    private lateinit var pendingConfirmationsTextView: TextView
    private lateinit var manualConfirmButton: Button
    private lateinit var historyButton: Button
    private lateinit var lastMonthDonationTextView: TextView
    private lateinit var totalDonationTextView: TextView
    private lateinit var todayDonationTextView: TextView
    private val database by lazy { DonationDatabase.getDatabase(this) }
    private var sentThisSession = 0  // Messages sent this session
    private var confirmedThisSession = 0  // Confirmations received this session
    private var confirmedAtBaselineSave = 0  // Snapshot of confirmedThisSession when baseline balance was saved
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var isVerifyingBalance = false
    private val verificationTimeoutHandler = Handler(Looper.getMainLooper())
    private val verificationTimeoutRunnable = Runnable {
        isVerifyingBalance = false
        Toast.makeText(this, "⏱️ No se recibió respuesta de saldo para la verificación", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        donationAmountTextView = findViewById(R.id.donationAmountTextView)
        delayInput = findViewById(R.id.delayInput)
        messageCountInput = findViewById(R.id.messageCount)
        clearButton = findViewById(R.id.clearButton)
        progressTextView = findViewById(R.id.progressTextView)
        progressBar = findViewById(R.id.progressBar)
        sentMessagesTextView = findViewById(R.id.sentMessagesTextView)
        pendingConfirmationsTextView = findViewById(R.id.pendingConfirmationsTextView)
        manualConfirmButton = findViewById(R.id.manualConfirmButton)
        historyButton = findViewById(R.id.historyButton)
        lastMonthDonationTextView = findViewById(R.id.lastMonthDonationTextView)
        totalDonationTextView = findViewById(R.id.totalDonationTextView)
        todayDonationTextView = findViewById(R.id.todayDonationTextView)

        // Set default values
        delayInput.setText(DEFAULT_DELAY.toString())
        messageCountInput.setText("1")
        
        // Setup stepper buttons for message count
        val messageCountMinus = findViewById<Button>(R.id.messageCountMinus)
        val messageCountPlus = findViewById<Button>(R.id.messageCountPlus)
        
        messageCountMinus.setOnClickListener {
            val current = messageCountInput.text.toString().toIntOrNull() ?: 0
            if (current > 0) {
                messageCountInput.setText((current - 1).toString())
            }
        }
        
        messageCountPlus.setOnClickListener {
            val current = messageCountInput.text.toString().toIntOrNull() ?: 0
            if (current < MAX_MESSAGES) {
                messageCountInput.setText((current + 1).toString())
            } else {
                Toast.makeText(this, "⚠️ Antel no permite donar más de $MAX_MESSAGES veces por día", Toast.LENGTH_LONG).show()
            }
        }
        
        // Setup stepper buttons for delay
        val delayMinus = findViewById<Button>(R.id.delayMinus)
        val delayPlus = findViewById<Button>(R.id.delayPlus)
        
        delayMinus.setOnClickListener {
            val current = delayInput.text.toString().toIntOrNull() ?: DEFAULT_DELAY
            if (current > 1) {
                delayInput.setText((current - 1).toString())
            }
        }
        
        delayPlus.setOnClickListener {
            val current = delayInput.text.toString().toIntOrNull() ?: DEFAULT_DELAY
            delayInput.setText((current + 1).toString())
        }

        // Initialize shared preferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Restore persisted baseline snapshot so it stays in sync with LAST_BALANCE_KEY across recreation
        confirmedAtBaselineSave = sharedPreferences.getInt(CONFIRMED_AT_BASELINE_KEY, 0)
        confirmedThisSession = sharedPreferences.getInt(CONFIRMED_THIS_SESSION_KEY, 0)
        sentThisSession = sharedPreferences.getInt(SENT_THIS_SESSION_KEY, 0)

        // Check for month change and ask user if they want to reset counters
        checkMonthChange()

        // Create notification channel for Android 8.0+
        createNotificationChannel()
        
        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Set up the spinner
        recipientSpinner = findViewById(R.id.recipientSpinner)
        val recipientNames = recipients.keys.toList()

        val adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, recipientNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        recipientSpinner.adapter = adapter

        val sendButton = findViewById<Button>(R.id.sendButton)
        val checkBalanceButton = findViewById<Button>(R.id.checkBalanceButton)
        balanceTextView = findViewById(R.id.balanceTextView)
        
        // Initialize reminder checkboxes
        reminder4DaysCheckbox = findViewById(R.id.reminder4DaysCheckbox)
        reminder3DaysCheckbox = findViewById(R.id.reminder3DaysCheckbox)
        reminderPenultimateCheckbox = findViewById(R.id.reminderPenultimateCheckbox)
        reminderLastDayCheckbox = findViewById(R.id.reminderLastDayCheckbox)

        // Set checkbox states from saved preferences
        reminder4DaysCheckbox.isChecked = sharedPreferences.getBoolean(REMINDER_4_DAYS_KEY, false)
        reminder3DaysCheckbox.isChecked = sharedPreferences.getBoolean(REMINDER_3_DAYS_KEY, false)
        reminderPenultimateCheckbox.isChecked = sharedPreferences.getBoolean(REMINDER_PENULTIMATE_KEY, false)
        reminderLastDayCheckbox.isChecked = sharedPreferences.getBoolean(REMINDER_LAST_DAY_KEY, false)

        sendButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
            } else {
                sendSMS(messageCountInput.text.toString())
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

        manualConfirmButton.setOnClickListener {
            showManualConfirmationDialog()
        }

        historyButton.setOnClickListener {
            showDonationHistory()
        }

        // Setup reminder checkbox listeners
        reminder4DaysCheckbox.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(REMINDER_4_DAYS_KEY, isChecked).apply()
            updateReminders(showToast = true)
        }
        
        reminder3DaysCheckbox.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(REMINDER_3_DAYS_KEY, isChecked).apply()
            updateReminders(showToast = true)
        }
        
        reminderPenultimateCheckbox.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(REMINDER_PENULTIMATE_KEY, isChecked).apply()
            updateReminders(showToast = true)
        }
        
        reminderLastDayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(REMINDER_LAST_DAY_KEY, isChecked).apply()
            updateReminders(showToast = true)
        }
        
        // Schedule reminders based on saved preferences
        updateReminders()

        // Register SMS receiver
        smsReceiver = SmsReceiver()
        val filter = IntentFilter()
        filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        registerReceiver(smsReceiver, filter)

        // Initialize donation stats from database
        refreshDonationStats()
        updatePendingConfirmations()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
        verificationTimeoutHandler.removeCallbacks(verificationTimeoutRunnable)
    }

    private fun checkMonthChange() {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonthYear = currentYear * 12 + currentMonth
        
        val lastMonthYear = sharedPreferences.getInt(LAST_MONTH_KEY, currentMonthYear)
        
        if (lastMonthYear < currentMonthYear) {
            // Month has changed, ask user if they want to reset counters
            val dialog = android.app.AlertDialog.Builder(this)
            dialog.setTitle("Nuevo mes detectado")
            dialog.setMessage("El mes ha cambiado desde su última donación, ¿desea reiniciar los contadores?")
            dialog.setPositiveButton("Sí") { _, _ ->
                resetCounters()
                sharedPreferences.edit().putInt(LAST_MONTH_KEY, currentMonthYear).apply()
            }
            dialog.setNegativeButton("No") { _, _ ->
                sharedPreferences.edit().putInt(LAST_MONTH_KEY, currentMonthYear).apply()
            }
            dialog.show()
        } else {
            // Save current month if not set
            sharedPreferences.edit().putInt(LAST_MONTH_KEY, currentMonthYear).apply()
        }
    }
    
    private fun resetCounters() {
        val donationPrefs = getSharedPreferences("donation_prefs", Context.MODE_PRIVATE)
        donationPrefs.edit().apply {
            putInt("response_count", 0)
            apply()
        }
        // Reset session counters
        sentThisSession = 0
        confirmedThisSession = 0
        confirmedAtBaselineSave = 0
        sharedPreferences.edit()
            .remove(LAST_BALANCE_KEY)
            .remove(CONFIRMED_AT_BASELINE_KEY)
            .putInt(CONFIRMED_THIS_SESSION_KEY, 0)
            .putInt(SENT_THIS_SESSION_KEY, 0)
            .apply()
        refreshDonationStats()
        updatePendingConfirmations()
        Toast.makeText(this, "Contadores reiniciados", Toast.LENGTH_SHORT).show()
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
            val delaySeconds = delayInput.text.toString().toIntOrNull() ?: DEFAULT_DELAY

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
                val messageNumber = index + 1
                val messageContent = "Donacion $messageNumber/$count"
                
                // Increment BEFORE send attempt - counts as "attempted"
                sentThisSession++
                sharedPreferences.edit().putInt(SENT_THIS_SESSION_KEY, sentThisSession).apply()
                updatePendingConfirmations()
                refreshDonationStats()
                
                try {
                    smsManager.sendTextMessage(recipientNumber, null, messageContent, null, null)
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
            Toast.makeText(this@MainActivity, "$sentCount de $count mensajes enviados a $recipientName!", Toast.LENGTH_SHORT).show()
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
                val messageNumber = index + 1
                val messageContent = "Donacion $messageNumber/$count"
                
                // Increment BEFORE send attempt - counts as "attempted"
                sentThisSession++
                sharedPreferences.edit().putInt(SENT_THIS_SESSION_KEY, sentThisSession).apply()
                updatePendingConfirmations()
                refreshDonationStats()
                
                try {
                    smsManager.sendTextMessage(recipientNumber, null, messageContent, null, null)
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
            Toast.makeText(this@MainActivity, "$sentCount de $count mensajes enviados a $recipientName con delay de ${delaySeconds}s!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updatePendingConfirmations() {
        val pending = (sentThisSession - confirmedThisSession).coerceAtLeast(0)
        pendingConfirmationsTextView.text = "Pendientes de confirmar: $pending"
        // Dynamic color based on value
        val color = when {
            pending == 0 -> 0xFF388E3C.toInt() // Green
            pending >= 40 -> 0xFFD32F2F.toInt() // Red
            else -> 0xFFFF8F00.toInt() // Orange
        }
        pendingConfirmationsTextView.setTextColor(color)
        manualConfirmButton.visibility = if (pending > 0) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun clearEverything() {
        // Show confirmation dialog
        val dialog = android.app.AlertDialog.Builder(this)
        dialog.setTitle("Reiniciar contadores de sesión")
        dialog.setMessage("Esto reiniciará los contadores de mensajes enviados en esta sesión y limpiará el saldo mostrado.\n\n⚠️ El historial de donaciones NO se borrará.")
        dialog.setPositiveButton("Sí, reiniciar") { _, _ ->
            // Clear response count
            val donationPrefs = getSharedPreferences("donation_prefs", Context.MODE_PRIVATE)
            donationPrefs.edit().apply {
                putInt("response_count", 0)
                apply()
            }
            
            // Clear message count input
            messageCountInput.setText("1")
            
            // Reset delay input to default
            delayInput.setText(DEFAULT_DELAY.toString())
            
            // Clear balance text
            balanceTextView.text = ""
            
            // Reset session counters
            sentThisSession = 0
            confirmedThisSession = 0
            confirmedAtBaselineSave = 0
            sharedPreferences.edit()
                .remove(LAST_BALANCE_KEY)
                .remove(CONFIRMED_AT_BASELINE_KEY)
                .putInt(CONFIRMED_THIS_SESSION_KEY, 0)
                .putInt(SENT_THIS_SESSION_KEY, 0)
                .apply()
            updatePendingConfirmations()
            refreshDonationStats()
            
            // Hide progress UI
            showProgressUI(false)
            
            Toast.makeText(this, "Contadores de sesión reiniciados (historial preservado)", Toast.LENGTH_SHORT).show()
        }
        dialog.setNegativeButton("No", null)
        dialog.show()
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
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun updateReminders(showToast: Boolean = false) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        
        // Cancel all existing reminders (current codes: 0,1,3,4; include 2 for migration from old scheme)
        for (requestCode in listOf(0, 1, 2, 3, 4)) {
            val intent = Intent(this, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pendingIntent)
        }
        
        // Schedule reminders based on checkbox states (requestCode = daysBeforeEnd)
        val reminder4Days = sharedPreferences.getBoolean(REMINDER_4_DAYS_KEY, false)
        val reminder3Days = sharedPreferences.getBoolean(REMINDER_3_DAYS_KEY, false)
        val reminderPenultimate = sharedPreferences.getBoolean(REMINDER_PENULTIMATE_KEY, false)
        val reminderLastDay = sharedPreferences.getBoolean(REMINDER_LAST_DAY_KEY, false)
        
        if (reminder4Days) {
            scheduleReminder(4, 4)
        }
        if (reminder3Days) {
            scheduleReminder(3, 3)
        }
        if (reminderPenultimate) {
            scheduleReminder(1, 1)
        }
        if (reminderLastDay) {
            scheduleReminder(0, 0)
        }
        
        val anyEnabled = reminder4Days || reminder3Days || reminderPenultimate || reminderLastDay
        if (showToast && anyEnabled) {
            Toast.makeText(this, "Recordatorios actualizados", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun scheduleReminder(daysBeforeEnd: Int, requestCode: Int) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        intent.putExtra("days_before_end", daysBeforeEnd)
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Calculate the target day in the current month
        val calendar = Calendar.getInstance()
        val lastDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val targetDay = lastDayOfMonth - daysBeforeEnd
        
        calendar.set(Calendar.DAY_OF_MONTH, targetDay)
        calendar.set(Calendar.HOUR_OF_DAY, 20)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // If the time already passed this month, schedule for next month
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.MONTH, 1)
            val nextMonthLastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            calendar.set(Calendar.DAY_OF_MONTH, nextMonthLastDay - daysBeforeEnd)
        }

        // Use setExactAndAllowWhileIdle to fire even in Doze mode (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
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
            val daysBeforeEnd = intent.getIntExtra("days_before_end", 0)
            
            // Create intent to open the app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            // Create and show notification
            val builder = NotificationCompat.Builder(context, CHANNELID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Recordatorio: Donar Saldo")
                .setContentText("¡Recuerda donar tu saldo a Animales Sin Hogar!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .addAction(android.R.drawable.ic_menu_send, "Abrir App", openAppPendingIntent)

            val notificationManager = NotificationManagerCompat.from(context)

            // Check for notification permission on Android 13+
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Use different notification ID based on days before end to allow multiple notifications
                notificationManager.notify(daysBeforeEnd, builder.build())
            }

            // Only reschedule for next month if it's not a test reminder
            if (daysBeforeEnd >= 0) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, 1)
                val nextMonthLastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, nextMonthLastDay - daysBeforeEnd)
                calendar.set(Calendar.HOUR_OF_DAY, 20)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)

                val newIntent = Intent(context, ReminderReceiver::class.java)
                newIntent.putExtra("days_before_end", daysBeforeEnd)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, daysBeforeEnd, newIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            }
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
                                // Add donation to database (10 pesos per message)
                                this@MainActivity.addDonation(10)
                                Toast.makeText(context, "¡Donación confirmada! +10$ agregado", Toast.LENGTH_SHORT).show()
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
    }

    private fun addDonation(amount: Int) {
        // NOTE: Late auto-confirmation SMSs that arrive after a manual confirmation of the same
        // donation will be recorded again here. Preventing this reliably requires per-message
        // tracking (unique IDs per SMS), which the carrier does not provide. This is a known
        // limitation; the balance-verification fix (confirmedSinceBaseline) already prevents the
        // primary double-count from the verification flow itself.
        val today = dateFormat.format(Calendar.getInstance().time)
        // Increment session confirmation counter and persist so it survives activity recreation
        confirmedThisSession++
        sharedPreferences.edit().putInt(CONFIRMED_THIS_SESSION_KEY, confirmedThisSession).apply()
        updatePendingConfirmations()
        
        CoroutineScope(Dispatchers.IO).launch {
            val existing = database.donationDao().getByDate(today)
            if (existing != null) {
                database.donationDao().addToDate(today, amount)
            } else {
                database.donationDao().insertOrUpdate(DonationRecord(today, amount))
            }
            withContext(Dispatchers.Main) {
                refreshDonationStats()
            }
        }
    }
    
    private fun refreshDonationStats() {
        CoroutineScope(Dispatchers.IO).launch {
            val today = dateFormat.format(Calendar.getInstance().time)
            val calendar = Calendar.getInstance()
            
            // Current month range
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = dateFormat.format(calendar.time)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val monthEnd = dateFormat.format(calendar.time)
            
            // Last month range
            calendar.add(Calendar.MONTH, -1)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val lastMonthStart = dateFormat.format(calendar.time)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val lastMonthEnd = dateFormat.format(calendar.time)
            
            val todayAmount = database.donationDao().getTodayTotal(today)
            val monthAmount = database.donationDao().getMonthTotal(monthStart, monthEnd)
            val lastMonthAmount = database.donationDao().getMonthTotal(lastMonthStart, lastMonthEnd)
            val totalAmount = database.donationDao().getAllTimeTotal()
            
            // Calculate messages confirmed today (each message = 10 pesos)
            val messagesConfirmedToday = todayAmount / 10
            
            withContext(Dispatchers.Main) {
                donationAmountTextView.text = "Donado este mes: $monthAmount$"
                lastMonthDonationTextView.text = "Donado el mes pasado: $lastMonthAmount$"
                totalDonationTextView.text = "Total histórico: $totalAmount$"
                todayDonationTextView.text = "Donado hoy: $todayAmount$"
                // Show sent this session + confirmed from DB (confirmed are already sent)
                val totalSentToday = messagesConfirmedToday + sentThisSession - confirmedThisSession
                sentMessagesTextView.text = "Mensajes enviados hoy: $totalSentToday"
            }
        }
    }
    
    private fun updateBalance(message: String) {
        // Display the full message immediately
        runOnUiThread {
            balanceTextView.text = message
        }
        
        // Extract balance and update UI asynchronously
        if (message.contains("Dispones de:")) {
            val balanceStr = message.substring(message.indexOf("Dispones de:") + "Dispones de:".length).trim()
            // Find the first numeric sequence
            var numericPart = ""
            for (c in balanceStr) {
                if (c.isDigit() || c == '.') {
                    numericPart += c
                } else if (numericPart.isNotEmpty()) {
                    break
                }
            }

            if (numericPart.isNotEmpty()) {
                val balance = numericPart.toDouble()
                val calculatedCount = (balance / 10).toInt()
                val numericPartFinal = numericPart

                // Save the current balance for future verification comparisons (only when establishing a new baseline, not during verification)
                val previousBalance = sharedPreferences.getFloat(LAST_BALANCE_KEY, -1f)
                if (!isVerifyingBalance) {
                    sharedPreferences.edit()
                        .putFloat(LAST_BALANCE_KEY, balance.toFloat())
                        .putInt(CONFIRMED_AT_BASELINE_KEY, confirmedThisSession)
                        .apply()
                    confirmedAtBaselineSave = confirmedThisSession
                }

                // If in verification mode, cancel the timeout and compare with previous balance
                if (isVerifyingBalance) {
                    verificationTimeoutHandler.removeCallbacks(verificationTimeoutRunnable)
                    isVerifyingBalance = false
                    if (previousBalance >= 0f) {
                        val diff = previousBalance - balance.toFloat()
                        val donatedMessages = (diff / 10).toInt()
                        runOnUiThread {
                            if (diff > 0) {
                                val confirmedSinceBaseline = confirmedThisSession - confirmedAtBaselineSave
                                val pendingToConfirm = (donatedMessages - confirmedSinceBaseline).coerceAtLeast(0)
                                val alreadyConfirmedInfo = if (confirmedSinceBaseline > 0)
                                    "\nYa confirmados automáticamente: $confirmedSinceBaseline mensajes\nA agregar ahora: $pendingToConfirm mensajes"
                                else ""
                                android.app.AlertDialog.Builder(this)
                                    .setTitle("✅ Donaciones detectadas por saldo")
                                    .setMessage("Saldo anterior: ${"%.0f".format(previousBalance)}\$\nSaldo actual: ${"%.0f".format(balance)}\$\n\nDiferencia: ${"%.0f".format(diff)}\$ = $donatedMessages mensajes$alreadyConfirmedInfo\n\n¿Confirmar las donaciones pendientes en el historial?")
                                    .setPositiveButton("Confirmar $pendingToConfirm mensajes") { _, _ ->
                                        // Recalculate in case auto-confirmations arrived while dialog was open
                                        val actualPending = (donatedMessages - (confirmedThisSession - confirmedAtBaselineSave)).coerceAtLeast(0)
                                        val newBaseline = confirmedThisSession + actualPending
                                        sharedPreferences.edit()
                                            .putFloat(LAST_BALANCE_KEY, balance.toFloat())
                                            .putInt(CONFIRMED_AT_BASELINE_KEY, newBaseline)
                                            .apply()
                                        confirmedAtBaselineSave = newBaseline
                                        manuallyConfirmPending(actualPending)
                                    }
                                    .setNegativeButton("Cancelar", null)
                                    .show()
                            } else {
                                // No positive diff: advance baseline to current balance so future verifications start fresh
                                sharedPreferences.edit()
                                    .putFloat(LAST_BALANCE_KEY, balance.toFloat())
                                    .putInt(CONFIRMED_AT_BASELINE_KEY, confirmedThisSession)
                                    .apply()
                                confirmedAtBaselineSave = confirmedThisSession
                                Toast.makeText(this, "ℹ️ No se detectaron donaciones por diferencia de saldo", Toast.LENGTH_LONG).show()
                            }
                        }
                        return
                    }
                }
                
                // Get already sent messages from database asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    val today = dateFormat.format(Calendar.getInstance().time)
                    val todayAmount = database.donationDao().getTodayTotal(today)
                    val alreadySent = todayAmount / 10
                    
                    withContext(Dispatchers.Main) {
                        try {
                            val remainingAllowed = MAX_MESSAGES - alreadySent
                            val smsCount = minOf(calculatedCount, remainingAllowed).coerceAtLeast(0)
                            
                            // Show warning if exceeded
                            if (calculatedCount > remainingAllowed && remainingAllowed > 0) {
                                Toast.makeText(this@MainActivity, "⚠️ Ya enviaste $alreadySent hoy. Podés enviar $remainingAllowed más", Toast.LENGTH_LONG).show()
                            } else if (remainingAllowed <= 0) {
                                Toast.makeText(this@MainActivity, "⚠️ Ya alcanzaste el límite de $MAX_MESSAGES mensajes por día", Toast.LENGTH_LONG).show()
                            }

                            // Set the SMS count in the EditText
                            messageCountInput.setText(smsCount.toString())

                            // Show balance and calculation separately
                            val sentInfo = if (alreadySent > 0) "\n📤 Ya enviados hoy: $alreadySent" else ""
                            val calculationText = when {
                                remainingAllowed <= 0 -> {
                                    "\n━━━━━━━━━━━━━━━━━━━━\n" +
                                    "📊 Cálculo: $numericPartFinal ÷ 10 = $calculatedCount mensajes$sentInfo\n" +
                                    "⚠️ Límite diario alcanzado"
                                }
                                calculatedCount > remainingAllowed -> {
                                    "\n━━━━━━━━━━━━━━━━━━━━\n" +
                                    "📊 Cálculo: $numericPartFinal ÷ 10 = $calculatedCount mensajes$sentInfo\n" +
                                    "✅ Podés enviar: $smsCount"
                                }
                                else -> {
                                    "\n━━━━━━━━━━━━━━━━━━━━\n" +
                                    "📊 Cálculo: $numericPartFinal ÷ 10 = $calculatedCount mensajes$sentInfo"
                                }
                            }
                            balanceTextView.text = message + calculationText
                        } catch (e: Exception) {
                            balanceTextView.text = "Error al analizar saldo: ${e.message}"
                        }
                    }
                }
            } else if (isVerifyingBalance) {
                // Balance message arrived but couldn't be parsed — cancel verification mode
                verificationTimeoutHandler.removeCallbacks(verificationTimeoutRunnable)
                isVerifyingBalance = false
                runOnUiThread {
                    Toast.makeText(this, "⚠️ No se pudo leer el saldo para verificar donaciones", Toast.LENGTH_LONG).show()
                }
            }
        } else if (isVerifyingBalance) {
            // Any non-balance message from 226 — don't reset the flag, keep waiting
        }
    }

    private fun showManualConfirmationDialog() {
        val pending = (sentThisSession - confirmedThisSession).coerceAtLeast(0)
        val hasLastBalance = sharedPreferences.getFloat(LAST_BALANCE_KEY, -1f) >= 0f

        val dialog = android.app.AlertDialog.Builder(this)
        dialog.setTitle("Confirmación manual")
        dialog.setMessage("Hay $pending mensaje(s) pendientes de confirmar.\n\nPodés confirmarlos manualmente o verificar por diferencia de saldo.")

        dialog.setPositiveButton("✅ Confirmar $pending donaciones") { _, _ ->
            // Recalculate at click time in case confirmations arrived while dialog was open
            val currentPending = (sentThisSession - confirmedThisSession).coerceAtLeast(0)
            if (currentPending > 0) {
                manuallyConfirmPending(currentPending)
            } else {
                Toast.makeText(this, "No hay donaciones pendientes para confirmar", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setNeutralButton("📊 Verificar por saldo") { _, _ ->
            startBalanceVerification()
        }

        dialog.setNegativeButton("Cancelar", null)

        val alertDialog = dialog.create()
        alertDialog.show()

        // Disable "Verificar por saldo" if no previous balance is stored
        alertDialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.isEnabled = hasLastBalance
        if (!hasLastBalance) {
            alertDialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.text = "📊 Verificar por saldo (sin saldo previo)"
        }
    }

    private fun manuallyConfirmPending(count: Int) {
        if (count <= 0) return
        // Claim the pending count synchronously on the Main thread before the async DB write,
        // so an automatic confirmation SMS arriving in the interim cannot cause double-counting.
        confirmedThisSession += count
        sharedPreferences.edit()
            .putInt(CONFIRMED_THIS_SESSION_KEY, confirmedThisSession)
            .apply()
        updatePendingConfirmations()
        val amountPesos = count * 10
        val today = dateFormat.format(Calendar.getInstance().time)
        CoroutineScope(Dispatchers.IO).launch {
            val existing = database.donationDao().getByDate(today)
            if (existing != null) {
                database.donationDao().addToDate(today, amountPesos)
            } else {
                database.donationDao().insertOrUpdate(
                    uy.roar.donadorautomatico.data.DonationRecord(today, amountPesos)
                )
            }
            withContext(Dispatchers.Main) {
                refreshDonationStats()
                Toast.makeText(this@MainActivity, "✅ $count donación(es) confirmadas manualmente (+\$${amountPesos})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBalanceVerification() {
        // Set flag before requesting permissions so it persists through the permission flow
        isVerifyingBalance = true
        if (checkAndRequestPermissions()) {
            sendBalanceCheck()
            Toast.makeText(this, "📊 Consultando saldo para verificar donaciones...", Toast.LENGTH_SHORT).show()
            // Auto-cancel verification mode after 30 seconds if no response arrives
            verificationTimeoutHandler.postDelayed(verificationTimeoutRunnable, 30_000L)
        }
    }

    private fun showDonationHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val monthlyTotals = database.donationDao().getMonthlyTotals()
            withContext(Dispatchers.Main) {
                if (monthlyTotals.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No hay donaciones registradas aún", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val sb = StringBuilder()
                for (entry in monthlyTotals) {
                    sb.appendLine("${entry.month}  →  ${entry.total}\$")
                }
                val scrollView = android.widget.ScrollView(this@MainActivity)
                val textView = TextView(this@MainActivity).apply {
                    text = sb.toString().trimEnd()
                    textSize = 15f
                    setTextColor(0xFF2E7D32.toInt())
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(48, 24, 48, 24)
                }
                scrollView.addView(textView)
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("📅 Historial de donaciones")
                    .setView(scrollView)
                    .setPositiveButton("Cerrar", null)
                    .show()
            }
        }
    }
}