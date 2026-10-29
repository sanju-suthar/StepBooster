package com.example.stepbooster

/*
 * ============================================================================
 * 1. build.gradle.kts (Module :app) me ye dependencies add karein:
 * ============================================================================
 * dependencies {
 *     implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
 *     implementation("androidx.activity:activity-compose:1.8.2")
 *     implementation("androidx.compose.material3:material3:1.2.0")
 *     implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
 * }
 *
 * ============================================================================
 * 2. AndroidManifest.xml me ye Permissions aur Activity filter add karein:
 * ============================================================================
 * <uses-permission android:name="android.permission.health.READ_STEPS"/>
 * <uses-permission android:name="android.permission.health.WRITE_STEPS"/>
 *
 * <application ...>
 *     <activity android:name=".MainActivity" android:exported="true">
 *         <intent-filter>
 *             <action android:name="android.intent.action.MAIN" />
 *             <category android:name="android.intent.category.LAUNCHER" />
 *         </intent-filter>
 *
 *         <!-- Health Connect Permission Dialog ke liye zaroori intent-filter -->
 *         <intent-filter>
 *             <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
 *         </intent-filter>
 *     </activity>
 * </application>
 *
 * <queries>
 *     <package android:name="com.google.android.apps.healthdata" />
 * </queries>
 */

import android.os.Bundle
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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var healthConnectClient: HealthConnectClient
    private var simulatorJob: Job? = null

    // Health Connect permissions jo Google Fit me steps read/write karne ke liye chahiye
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

        // Check karein ki phone me Health Connect available hai ya nahi
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
        var isSimulating by remember { mutableStateOf(false) }
        var totalInjectedSteps by remember { mutableLongStateOf(0L) }
        var cadenceStepsPerMin by remember { mutableFloatStateOf(95f) } // Default: 95 steps/minute
        var statusMessage by remember { mutableStateOf("Simulator Ready Hai") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Auto Step Simulator",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusMessage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Is Session me Badhaye Gaye Steps", fontSize = 14.sp)
                    Text(
                        text = "$totalInjectedSteps",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Walking Speed (Cadence) Slider
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
                text = "Tip: 80-110 steps/min sabse realistic natural walk lagti hai",
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Start / Stop Auto-Step Simulation Button
            Button(
                onClick = {
                    if (!isSimulating) {
                        lifecycleScope.launch {
                            if (checkAndRequestPermissions()) {
                                isSimulating = true
                                statusMessage = "Running: Har 30 sec me steps Google Fit ko ja rahe hain..."
                                startStepAlgorithm(
                                    baseStepsPerMin = cadenceStepsPerMin.toInt(),
                                    onStepsAdded = { added ->
                                        totalInjectedSteps += added
                                    }
                                )
                            }
                        }
                    } else {
                        stopStepAlgorithm()
                        isSimulating = false
                        statusMessage = "Simulator Paused"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = if (isSimulating) "STOP AUTO STEPS" else "START AUTO STEPS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instant +500 Steps Test Button
            OutlinedButton(
                onClick = {
                    lifecycleScope.launch {
                        if (checkAndRequestPermissions()) {
                            val now = Instant.now()
                            val start = now.minus(5, ChronoUnit.MINUTES)
                            val success = writeStepsToHealthConnect(start, now, 500L)
                            if (success) {
                                totalInjectedSteps += 500L
                                statusMessage = "+500 Steps pichle 5 min ke window me add ho gaye!"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Instant +500 Steps Add Karein (5 Min Walk)")
            }
        }
    }

    /**
     * NATURAL STEP SIMULATION ALGORITHM:
     * Ek saath 10,000 steps dalne par Google Fit use unnatural (manual entry) maan sakta hai.
     * Isliye yeh algorithm har 30 seconds ke interval par thode-thode random variation (jitter)
     * ke saath steps insert karta hai, jisse lagta hai ki user sach me chal raha hai.
     */
    private fun startStepAlgorithm(
        baseStepsPerMin: Int,
        onStepsAdded: (Long) -> Unit
    ) {
        simulatorJob?.cancel()
        simulatorJob = lifecycleScope.launch {
            var batchStartTime = Instant.now()

            while (isActive) {
                // 30 seconds wait karein (1 batch interval)
                delay(30_000L)

                val batchEndTime = Instant.now()

                // 30 second me average steps + insaan ke chalne jaisa random variation (-4 se +5 steps)
                val halfMinBase = baseStepsPerMin / 2
                val naturalJitter = Random.nextInt(-4, 6)
                val simulatedSteps = (halfMinBase + naturalJitter).coerceAtLeast(15).toLong()

                val success = writeStepsToHealthConnect(
                    startTime = batchStartTime,
                    endTime = batchEndTime,
                    stepCount = simulatedSteps
                )

                if (success) {
                    onStepsAdded(simulatedSteps)
                }

                // Agle batch ka start time abhi ka end time hoga
                batchStartTime = batchEndTime
            }
        }
    }

    private fun stopStepAlgorithm() {
        simulatorJob?.cancel()
        simulatorJob = null
    }

    /**
     * Health Connect me StepsRecord save karta hai, jise Google Fit auto-sync kar leta hai.
     */
    private suspend fun writeStepsToHealthConnect(
        startTime: Instant,
        endTime: Instant,
        stepCount: Long
    ): Boolean {
        return try {
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)
            val stepsRecord = StepsRecord(
                count = stepCount,
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset
            )
            healthConnectClient.insertRecords(listOf(stepsRecord))
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
