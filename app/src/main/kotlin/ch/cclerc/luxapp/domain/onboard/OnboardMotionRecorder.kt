package ch.cclerc.luxapp.domain.onboard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class OnboardMotionRecorder(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val writer = Executors.newSingleThreadExecutor()
    private var sensorThread: HandlerThread? = null
    private var stream: FileOutputStream? = null
    private val buffer = StringBuilder()
    private var lastContext = ""
    private var gravity = FloatArray(3)
    private var rotation = FloatArray(3)
    private var clockOffset = 0.0

    @Volatile
    private var isRecording = false

    fun start(tripName: String) {
        val linear = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: return
        if (isRecording) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val folder = folder(appContext).apply { mkdirs() }
        val output = runCatching { FileOutputStream(File(folder, "ride-$stamp.csv")) }.getOrNull() ?: return
        isRecording = true
        writer.execute { stream = output }
        write("# ${tripName.replace("\n", " ")}\n")
        write("# m,t,ax,ay,az,gx,gy,gz,rx,ry,rz | g,t,lat,lon,speed,course,accuracy | c,t,phase,leg,mode,line,trip,nextStop,along,locked\n")

        clockOffset = System.currentTimeMillis() / 1000.0 - SystemClock.elapsedRealtimeNanos() / 1e9
        gravity = FloatArray(3)
        rotation = FloatArray(3)
        val thread = HandlerThread("onboard-recorder").apply { start() }
        sensorThread = thread
        val handler = Handler(thread.looper)
        val period = 20_000
        sensorManager.registerListener(this, linear, period, handler)
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensorManager.registerListener(this, it, period, handler) }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensorManager.registerListener(this, it, period, handler) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRecording) return
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> gravity = floatArrayOf(event.values[0] / G, event.values[1] / G, event.values[2] / G)
            Sensor.TYPE_GYROSCOPE -> rotation = event.values.copyOf(3)
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val a = event.values
                val g = gravity
                val r = rotation
                write(
                    String.format(
                        Locale.US,
                        "m,%.3f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f\n",
                        event.timestamp / 1e9 + clockOffset,
                        a[0] / G, a[1] / G, a[2] / G, g[0], g[1], g[2], r[0], r[1], r[2]
                    )
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun record(fix: OnboardFix) {
        if (!isRecording) return
        write(
            String.format(
                Locale.US,
                "g,%.3f,%.6f,%.6f,%.2f,%.1f,%.1f\n",
                fix.timestamp.toEpochMilli() / 1000.0,
                fix.coordinate.latitude,
                fix.coordinate.longitude,
                fix.speed,
                fix.course,
                fix.horizontalAccuracy
            )
        )
    }

    fun context(phase: String, leg: Int, mode: String, line: String, trip: String, nextStop: Int, along: Double, locked: Boolean) {
        if (!isRecording) return
        val fields = "$phase,$leg,$mode,${line.replace(",", " ")},$trip,$nextStop,${along.toInt()},${if (locked) 1 else 0}"
        if (fields == lastContext) return
        lastContext = fields
        write(String.format(Locale.US, "c,%.3f,", System.currentTimeMillis() / 1000.0) + fields + "\n")
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        sensorManager?.unregisterListener(this)
        sensorThread?.quitSafely()
        sensorThread = null
        lastContext = ""
        writer.execute {
            flush()
            runCatching { stream?.close() }
            stream = null
        }
    }

    private fun write(text: String) {
        writer.execute {
            buffer.append(text)
            if (buffer.length > 64_000) flush()
        }
    }

    private fun flush() {
        val output = stream ?: return
        if (buffer.isEmpty()) return
        runCatching { output.write(buffer.toString().toByteArray()) }
        buffer.setLength(0)
    }

    companion object {
        private const val G = SensorManager.GRAVITY_EARTH

        fun folder(context: Context): File = File(context.filesDir, "MotionRecordings")

        fun recordings(context: Context): List<File> =
            folder(context).listFiles().orEmpty()
                .filter { it.extension == "csv" }
                .sortedByDescending { it.name }

        fun totalSize(context: Context): Long = recordings(context).sumOf { it.length() }

        fun deleteAll(context: Context) {
            recordings(context).forEach { it.delete() }
        }
    }
}
