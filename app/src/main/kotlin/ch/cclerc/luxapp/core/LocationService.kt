package ch.cclerc.luxapp.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

enum class LocationAuthState {
    NotDetermined,
    Denied,
    Granted
}

object LocationService {
    private const val MIN_TIME_MS = 1000L
    private const val MIN_DISTANCE_M = 10f
    private const val ASKED_KEY = "location_permission_asked"

    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    private val _authorizationState = MutableStateFlow(LocationAuthState.NotDetermined)
    val authorizationState: StateFlow<LocationAuthState> = _authorizationState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var subscriberCount: Int = 0
    private var updating: Boolean = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(newLocation: Location) {
            val current = _location.value
            if (current == null || newLocation.time >= current.time) {
                _location.value = newLocation
                _errorMessage.value = null
            }
        }

    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val degrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            _heading.value = (degrees + 360f) % 360f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        locationManager = context.applicationContext
            .getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        sensorManager = context.applicationContext
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        refreshAuthState()
    }

    fun refreshAuthState() {
        val context = appContext ?: return
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val granted = fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
        val previous = _authorizationState.value
        val next = when {
            granted -> LocationAuthState.Granted
            hasAskedBefore(context) -> LocationAuthState.Denied
            else -> LocationAuthState.NotDetermined
        }
        if (next == previous) return
        _authorizationState.value = next
        when (next) {
            LocationAuthState.Granted -> {
                _errorMessage.value = null
                if (subscriberCount > 0) startUpdates()
            }
            LocationAuthState.Denied -> {
                _errorMessage.value =
                    "Location access was denied. Please enable it in Settings to use the app properly."
                stopUpdates()
            }
            LocationAuthState.NotDetermined -> {
                _errorMessage.value = "Waiting for location permission..."
            }
        }
    }

    fun markPermissionRequested() {
        val context = appContext ?: return
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ASKED_KEY, true)
            .apply()
    }

    private fun hasAskedBefore(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(ASKED_KEY, false)

    fun startMonitoring() {
        subscriberCount += 1
        if (subscriberCount == 1) {
            startUpdates()
        }
    }

    fun stopMonitoring() {
        subscriberCount = max(0, subscriberCount - 1)
        if (subscriberCount == 0) {
            stopUpdates()
        }
    }

    fun resumeUpdates() {
        refreshAuthState()
        if (subscriberCount > 0) {
            startUpdates()
        }
    }

    private fun startUpdates() {
        if (updating) return
        if (_authorizationState.value != LocationAuthState.Granted) return
        val manager = locationManager ?: return

        var attached = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) continue
            val ok = runCatching {
                manager.requestLocationUpdates(
                    provider,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper()
                )
            }.isSuccess
            if (ok) {
                attached = true
                if (_location.value == null) {
                    val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                    if (last != null) {
                        val current = _location.value
                        if (current == null || last.time > current.time) {
                            _location.value = last
                        }
                    }
                }
            }
        }

        sensorManager?.let { sensors ->
            val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (sensor != null) {
                sensors.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        updating = attached
        if (!attached) {
            _errorMessage.value = "Unable to determine your location. Please try again later."
        }
    }

    private fun stopUpdates() {
        val manager = locationManager
        if (manager != null) {
            runCatching { manager.removeUpdates(locationListener) }
        }
        sensorManager?.let { runCatching { it.unregisterListener(sensorListener) } }
        updating = false
    }
}
