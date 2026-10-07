package ch.cclerc.luxapp.domain.onboard

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ch.cclerc.luxapp.domain.map.LatLng
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import java.time.Instant

data class OnboardFix(
    val coordinate: LatLng,
    val horizontalAccuracy: Double,
    val speed: Double,
    val speedAccuracy: Double = -1.0,
    val course: Double,
    val courseAccuracy: Double,
    val timestamp: Instant
)

class OnboardHeading(
    val trueHeading: Double,
    val accuracy: Double,
    val timestamp: Instant
)

class OnboardLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    var onLocation: ((OnboardFix) -> Unit)? = null
    var onHeading: ((OnboardHeading) -> Unit)? = null

    private var running = false
    private var declination = 0f
    private var headingAccuracy = -1.0
    private var lastHeading: Double? = null
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    var isSaving: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (running) requestUpdates()
        }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::deliver)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val magnetic = Math.toDegrees(orientation[0].toDouble())
            val heading = Angle360.normalized(magnetic + declination)
            val previous = lastHeading
            if (previous != null && kotlin.math.abs(Angle360.delta(previous, heading)) < 3) return
            lastHeading = heading
            onHeading?.invoke(OnboardHeading(heading, headingAccuracy, Instant.now()))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            headingAccuracy = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 10.0
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 20.0
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 35.0
                else -> -1.0
            }
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (running) startHeading()
        }

        override fun onStop(owner: LifecycleOwner) {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    fun start() {
        if (running) return
        running = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        if (hasPermission()) {
            try {
                fused.lastLocation.addOnSuccessListener { location ->
                    if (running && location != null) deliver(location)
                }
            } catch (_: SecurityException) {
            }
        }
        requestUpdates()
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startHeading()
        }
    }

    private fun startHeading() {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
            headingAccuracy = 20.0
            sensorManager.unregisterListener(sensorListener)
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        fused.removeLocationUpdates(callback)
        sensorManager?.unregisterListener(sensorListener)
    }

    private fun requestUpdates() {
        if (!hasPermission()) return
        fused.removeLocationUpdates(callback)
        val request = if (isSaving) {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L)
                .setMinUpdateDistanceMeters(10f)
                .build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .setWaitForAccurateLocation(false)
                .build()
        }
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
        }
    }

    private fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun deliver(location: Location) {
        declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            location.time
        ).declination
        onLocation?.invoke(
            OnboardFix(
                coordinate = LatLng(location.latitude, location.longitude),
                horizontalAccuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else -1.0,
                speed = if (location.hasSpeed()) location.speed.toDouble() else -1.0,
                speedAccuracy = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else -1.0,
                course = if (location.hasBearing()) location.bearing.toDouble() else -1.0,
                courseAccuracy = if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees.toDouble() else -1.0,
                timestamp = Instant.ofEpochMilli(location.time)
            )
        )
    }
}

class OnboardMotionDetector(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val recentSpeeds = ArrayDeque<Pair<Double, Instant>>()
    private val recentSteps = ArrayDeque<Long>()
    private var stride: Double? = null
    private var pedometerSpeed: Pair<Double, Instant>? = null
    private var lastVehicleActivity: Instant = Instant.EPOCH
    private var lastFootActivity: Instant = Instant.EPOCH
    private var receiverRegistered = false
    private var pendingIntent: PendingIntent? = null

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = System.currentTimeMillis()
            recentSteps.addLast(now)
            while (recentSteps.isNotEmpty() && now - recentSteps.first() > 6_000) recentSteps.removeFirst()
            val cadence = cadence(now) ?: return
            val length = stride ?: return
            val speed = cadence * length
            if (speed <= 0.3 || speed >= 3.5) return
            val smoothed = pedometerSpeed?.let { it.first * 0.7 + speed * 0.3 } ?: speed
            pedometerSpeed = smoothed to Instant.now()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ActivityRecognitionResult.hasResult(intent)) return
            val activity = ActivityRecognitionResult.extractResult(intent)?.mostProbableActivity ?: return
            if (activity.confidence < 50) return
            when (activity.type) {
                DetectedActivity.IN_VEHICLE -> lastVehicleActivity = Instant.now()
                DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT, DetectedActivity.STILL ->
                    lastFootActivity = Instant.now()
            }
        }
    }

    fun start() {
        if (hasActivityPermission()) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sensorManager.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            startActivityUpdates()
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(stepListener)
        pendingIntent?.let { intent ->
            runCatching { ActivityRecognition.getClient(appContext).removeActivityUpdates(intent) }
        }
        pendingIntent = null
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(activityReceiver) }
            receiverRegistered = false
        }
    }

    private fun startActivityUpdates() {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                appContext,
                activityReceiver,
                IntentFilter(ACTION_ACTIVITY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        val intent = PendingIntent.getBroadcast(
            appContext,
            7,
            Intent(ACTION_ACTIVITY).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        pendingIntent = intent
        runCatching {
            ActivityRecognition.getClient(appContext).requestActivityUpdates(5_000, intent)
        }
    }

    private fun hasActivityPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun cadence(now: Long): Double? {
        val steps = recentSteps.filter { now - it <= 6_000 }
        if (steps.size < 4) return null
        val span = (steps.last() - steps.first()) / 1000.0
        if (span <= 0) return null
        return (steps.size - 1) / span
    }

    val walkingSpeed: Double?
        get() {
            val speed = pedometerSpeed ?: return null
            if (Instant.now().toEpochMilli() - speed.second.toEpochMilli() >= 20_000) return null
            return speed.first
        }

    fun record(fix: OnboardFix) {
        if (fix.speed < 0 || fix.horizontalAccuracy < 0 || fix.horizontalAccuracy > 100) return
        recentSpeeds.addLast(fix.speed to fix.timestamp)
        while (recentSpeeds.isNotEmpty() &&
            fix.timestamp.toEpochMilli() - recentSpeeds.first().second.toEpochMilli() > 30_000
        ) {
            recentSpeeds.removeFirst()
        }
        if (fix.horizontalAccuracy <= 30 && fix.speed in 0.4..3.0) {
            val cadence = cadence(System.currentTimeMillis()) ?: return
            val measured = fix.speed / cadence
            if (measured in 0.3..1.5) stride = stride?.let { it * 0.9 + measured * 0.1 } ?: measured
        }
    }

    val isInVehicle: Boolean
        get() {
            val now = Instant.now().toEpochMilli()
            val motionSaysVehicle = now - lastVehicleActivity.toEpochMilli() < 30_000 &&
                !lastVehicleActivity.isBefore(lastFootActivity)
            val fast = recentSpeeds.filter { now - it.second.toEpochMilli() < 20_000 }
            val speedSaysVehicle = fast.size >= 3 && fast.all { it.first > 7 }
            return motionSaysVehicle || speedSaysVehicle
        }

    private companion object {
        const val ACTION_ACTIVITY = "ch.cclerc.luxapp.onboard.ACTIVITY"
    }
}
