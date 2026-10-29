package com.example.stepbooster

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var healthConnectClient: HealthConnectClient

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class)
    )

    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            Toast.makeText(this, "Health Connect Permission Mil Gayi!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission zaroori hai steps badhane ke liye", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val availabilityStatus = HealthConnectClient.getSdkStatus(this)
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(this)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StepSimulatorScreen()
                }
            }
        }
    }

    @Composable
    fun StepSimulatorScreen() {
        val isSimulating by StepForegroundService.isRunning.collectAsState()
        val totalInjectedSteps by StepForegroundService.sessionSteps.collectAsState()
        val statusMessage by StepForegroundService.statusText.collectAsState()
        var cadenceStepsPerMin by remember { mutableFloatStateOf(95f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Auto Step Simulator (24x7)",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusMessage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Background Session Steps", fontSize = 14.sp)
                    Text(
                        text = "$totalInjectedSteps",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Walking Speed: ${cadenceStepsPerMin.toInt()} steps / minute",
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = cadenceStepsPerMin,
                onValueChange = { cadenceStepsPerMin = it },
                valueRange = 60f..150f,
                enabled = !isSimulating
            )
            Text(
                text = "Tip: 85-110 steps/min sabse natural walk lagti hai",
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Start / Stop Background Foreground Service Button
            Button(
                onClick = {
                    if (!isSimulating) {
                        lifecycleScope.launch {
                            if (checkAndRequestPermissions()) {
                                startBackgroundStepService(cadenceStepsPerMin.toInt())
                            }
                        }
                    } else {
                        stopBackgroundStepService()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = if (isSimulating) "STOP BACKGROUND STEPS" else "START 24x7 BACKGROUND STEPS",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instant +500 Steps Button
            OutlinedButton(
                onClick = {
                    lifecycleScope.launch {
                        if (checkAndRequestPermissions()) {
                            val now = Instant.now()
                            val start = now.minus(5, ChronoUnit.MINUTES)
                            val success = writeInstantSteps(start, now, 500L)
                            if (success) {
                                StepForegroundService.sessionSteps.value += 500L
                                StepForegroundService.statusText.value = "+500 Steps turant add ho gaye!"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Instant +500 Steps Add Karein")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Battery Optimization Bypass Button for Realme UI
            TextButton(
                onClick = { requestIgnoreBatteryOptimizations() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Realme Battery Saver Se App Ko Free Karein (1-Click)")
            }
        }
    }

    private fun startBackgroundStepService(stepsPerMin: Int) {
        val intent = Intent(this, StepForegroundService::class.java).apply {
            action = StepForegroundService.ACTION_START
            putExtra(StepForegroundService.EXTRA_CADENCE, stepsPerMin)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBackgroundStepService() {
        val intent = Intent(this, StepForegroundService::class.java).apply {
            action = StepForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Battery Saver pehle se OFF hai is app ke liye!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "App Info > Battery Usage me jaakar Background Allow karein", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun writeInstantSteps(
        startTime: Instant,
        endTime: Instant,
        stepCount: Long
    ): Boolean {
        return try {
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)
            val record = StepsRecord(
                count = stepCount,
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset
            )
            healthConnectClient.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun checkAndRequestPermissions(): Boolean {
        if (!::healthConnectClient.isInitialized) {
            Toast.makeText(
                this,
                "Play Store se 'Health Connect' app install/update karein",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return if (granted.containsAll(permissions)) {
            true
        } else {
            requestPermissions.launch(permissions)
            false
        }
    }
}

/**
 * FOREGROUND SERVICE + WAKE LOCK:
 * Yeh service phone ki screen lock hone ya app band karne ke baad bhi
 * background me bina ruke har 30 second me steps Google Fit ko bhejti rehti hai.
 */
class StepForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var simulatorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "step_booster_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_CADENCE = "EXTRA_CADENCE"

        val isRunning = MutableStateFlow(false)
        val sessionSteps = MutableStateFlow(0L)
        val statusText = MutableStateFlow("Simulator Ready Hai")
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val cadence = intent.getIntExtra(EXTRA_CADENCE, 95)
                startForegroundSimulation(cadence)
            }
            ACTION_STOP -> {
                stopForegroundSimulation()
            }
        }
        return START_STICKY
    }

    private fun startForegroundSimulation(baseStepsPerMin: Int) {
        if (isRunning.value) return
        isRunning.value = true
        statusText.value = "Background Service Chalu Hai ($baseStepsPerMin steps/min)"

        // WakeLock taaki screen band hone par Realme 6 CPU ko sleep me na bheje
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "StepBooster::StepWakeLock"
        ).apply {
            acquire(12 * 60 * 60 * 1000L) // Max 12 hours continuous lock
        }

        val notification = buildNotification(sessionSteps.value, baseStepsPerMin)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val healthClient = HealthConnectClient.getOrCreate(applicationContext)

        simulatorJob?.cancel()
        simulatorJob = serviceScope.launch {
            var batchStartTime = Instant.now()

            while (isActive && isRunning.value) {
                delay(30_000L) // Har 30 second me 1 batch

                val batchEndTime = Instant.now()
                val halfMinBase = baseStepsPerMin / 2
                val naturalJitter = Random.nextInt(-4, 6)
                val simulatedSteps = (halfMinBase + naturalJitter).coerceAtLeast(15).toLong()

                try {
                    val zoneOffset = ZoneId.systemDefault().rules.getOffset(batchStartTime)
                    val record = StepsRecord(
                        count = simulatedSteps,
                        startTime = batchStartTime,
                        startZoneOffset = zoneOffset,
                        endTime = batchEndTime,
                        endZoneOffset = zoneOffset
                    )
                    healthClient.insertRecords(listOf(record))

                    sessionSteps.value += simulatedSteps
                    updateNotification(sessionSteps.value, baseStepsPerMin)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                batchStartTime = batchEndTime
            }
        }
    }

    private fun stopForegroundSimulation() {
        isRunning.value = false
        statusText.value = "Background Service Ruki Hui Hai"
        simulatorJob?.cancel()
        simulatorJob = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(currentSteps: Long, speed: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StepForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Step Booster Chalu Hai (+$currentSteps steps)")
            .setContentText("Speed: $speed steps/min • Google Fit me sync ho raha hai")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
            .build()
    }

    private fun updateNotification(currentSteps: Long, speed: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(currentSteps, speed))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Booster Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopForegroundSimulation()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
