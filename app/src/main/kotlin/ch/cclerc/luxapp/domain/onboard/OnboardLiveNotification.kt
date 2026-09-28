package ch.cclerc.luxapp.domain.onboard

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import ch.cclerc.luxapp.MainActivity
import ch.cclerc.luxapp.R
import ch.cclerc.luxapp.ui.itinerary.formatTime
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

data class OnboardActivityState(
    val phase: OnboardPhase,
    val title: String,
    val subtitle: String,
    val symbolName: String,
    val line: String? = null,
    val lineColor: Color? = null,
    val headsign: String? = null,
    val targetDate: Instant? = null,
    val countdownMinutes: Int? = null,
    val arrivalDate: Instant,
    val delayMinutes: Int? = null,
    val stopsRemaining: Int? = null,
    val totalStops: Int? = null,
    val passedStops: Int? = null,
    val fromName: String? = null,
    val toName: String? = null,
    val progress: Double,
    val segmentStart: Instant? = null,
    val segmentEnd: Instant? = null,
    val distanceMeters: Double? = null,
    val vehicleIsLive: Boolean,
    val vehicleDistanceMeters: Double? = null,
    val isUrgent: Boolean
) {
    val accent: Color
        get() = when (phase) {
            OnboardPhase.ARRIVED -> Color(0xFF34C759)
            OnboardPhase.WALKING -> lineColor ?: WalkBlue
            OnboardPhase.WAITING, OnboardPhase.RIDING -> lineColor ?: WalkBlue
        }

    companion object {
        val WalkBlue = Color(red = 0.1f, green = 0.42f, blue = 0.85f)
        val UrgentRed = Color(red = 1f, green = 0.27f, blue = 0.23f)
    }
}

fun formatMeters(meters: Double): String {
    if (meters >= 1000) return String.format(java.util.Locale.getDefault(), "%.1f km", meters / 1000)
    val step = if (meters > 300) 50.0 else 10.0
    return "${((meters / step).roundToInt() * step).toInt()} m"
}

class OnboardLiveActivityController(context: Context) {
    private val appContext = context.applicationContext
    private var lastState: OnboardActivityState? = null
    private var lastPush = 0L
    private var destinationName = ""
    private val handler = Handler(Looper.getMainLooper())

    var isActive: Boolean = false
        private set

    fun start(destinationName: String, state: OnboardActivityState) {
        if (isActive) return
        this.destinationName = destinationName
        OnboardAnnouncer.ensureChannels(appContext)
        handler.removeCallbacksAndMessages(null)
        currentNotification = build(state)
        lastState = state
        lastPush = System.currentTimeMillis()
        isActive = true
        runCatching {
            appContext.startForegroundService(Intent(appContext, OnboardService::class.java))
        }
        scheduleStale()
    }

    fun update(state: OnboardActivityState) {
        val needsRefresh = System.currentTimeMillis() - lastPush >= REFRESH_AFTER_MS
        if (!isActive || (state == lastState && !needsRefresh)) return
        val previous = lastState
        if (!needsRefresh && previous != null && isMinorChange(previous, state)) {
            val progressMoved = abs(previous.progress - state.progress) >= 0.02
            val sinceLastPush = System.currentTimeMillis() - lastPush
            if (!((progressMoved && sinceLastPush >= 5_000) || sinceLastPush >= 15_000)) return
        }
        lastState = state
        lastPush = System.currentTimeMillis()
        val notification = build(state)
        currentNotification = notification
        notify(notification)
        scheduleStale()
    }

    private fun scheduleStale() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!isActive) return@postDelayed
            val notification = buildInterrupted()
            currentNotification = notification
            notify(notification)
        }, STALE_AFTER_MS)
    }

    private fun buildInterrupted(): Notification =
        NotificationCompat.Builder(appContext, OnboardAnnouncer.LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_onboard_notification)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle("Navigation interrompue")
            .setContentText(destinationName)
            .setShortCriticalText("—")
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        1,
        Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun end(finalState: OnboardActivityState?) {
        if (!isActive) return
        isActive = false
        handler.removeCallbacksAndMessages(null)
        if (finalState != null && finalState.phase == OnboardPhase.ARRIVED) {
            val notification = build(finalState, ongoing = false)
            currentNotification = notification
            OnboardService.stop(appContext, keepNotification = true)
            notify(notification)
            handler.postDelayed({ NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID) }, 120_000)
        } else {
            OnboardService.stop(appContext, keepNotification = false)
        }
        lastState = null
    }

    private fun notify(notification: Notification) {
        if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification) }
    }

    private fun isMinorChange(old: OnboardActivityState, new: OnboardActivityState): Boolean =
        old.phase == new.phase &&
            old.title == new.title &&
            old.subtitle == new.subtitle &&
            old.stopsRemaining == new.stopsRemaining &&
            old.delayMinutes == new.delayMinutes &&
            old.isUrgent == new.isUrgent &&
            old.vehicleIsLive == new.vehicleIsLive &&
            abs((old.distanceMeters ?: 0.0) - (new.distanceMeters ?: 0.0)) < 25 &&
            old.passedStops == new.passedStops &&
            old.countdownMinutes == new.countdownMinutes &&
            old.segmentStart == new.segmentStart &&
            old.segmentEnd == new.segmentEnd &&
            abs((old.targetDate?.epochSecond ?: 0) - (new.targetDate?.epochSecond ?: 0)) < 30

    private fun build(state: OnboardActivityState, ongoing: Boolean = true): Notification {
        val intent = contentIntent()
        val builder = NotificationCompat.Builder(appContext, OnboardAnnouncer.LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_onboard_notification)
            .setContentIntent(intent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(state.accent.toArgb())
            .setRequestPromotedOngoing(ongoing)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val lineHeader = listOfNotNull(
            state.line,
            state.headsign?.let { "→ $it" }
        ).joinToString(" ")

        when (state.phase) {
            OnboardPhase.RIDING -> {
                val stops = state.stopsRemaining ?: 0
                builder.setContentTitle(
                    if (state.isUrgent) "Descendez au prochain arrêt · ${state.title}" else "Descendez à ${state.title}"
                )
                val delay = state.delayMinutes?.takeIf { it != 0 }?.let { if (it > 0) "+$it'" else "$it'" }
                val arrival = state.targetDate?.let { "Arrivée ${formatTime(it)}" }
                builder.setContentText(
                    listOfNotNull(
                        lineHeader.ifEmpty { null },
                        if (state.isUrgent) "Préparez-vous à descendre" else state.subtitle,
                        arrival,
                        delay
                    ).joinToString(" · ")
                )
                builder.setShortCriticalText(if (state.isUrgent) "Descendez" else "$stops arr.")
                builder.setStyle(ridingStyle(state))
                state.targetDate?.let { builder.setWhen(it.toEpochMilli()).setShowWhen(true) }
            }
            OnboardPhase.WAITING -> {
                builder.setContentTitle("Départ de ${state.title}")
                val live = if (state.vehicleIsLive) {
                    state.vehicleDistanceMeters?.let { "En direct · ${formatMeters(it)}" } ?: "En direct"
                } else {
                    null
                }
                builder.setContentText(
                    listOfNotNull(
                        lineHeader.ifEmpty { null },
                        state.subtitle.ifEmpty { null },
                        live
                    ).joinToString(" · ")
                )
                state.targetDate?.let { target ->
                    builder.setWhen(target.toEpochMilli())
                        .setShowWhen(true)
                        .setUsesChronometer(target.isAfter(Instant.now()))
                        .setChronometerCountDown(true)
                }
                builder.setShortCriticalText(countdownText(state))
            }
            OnboardPhase.WALKING -> {
                val distance = state.distanceMeters?.let(::formatMeters)
                builder.setContentTitle(listOfNotNull(distance, state.title).joinToString(" · "))
                builder.setContentText(
                    if (state.line != null) {
                        listOfNotNull("Ensuite ${state.line}", state.subtitle.ifEmpty { null }, countdownText(state))
                            .joinToString(" · ")
                    } else {
                        "$destinationName · Arrivée ${formatTime(state.arrivalDate)}"
                    }
                )
                builder.setShortCriticalText(distance ?: countdownText(state))
                builder.setStyle(
                    NotificationCompat.ProgressStyle()
                        .setStyledByProgress(false)
                        .addProgressSegment(
                            NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX).setColor(state.accent.toArgb())
                        )
                        .setProgress((state.progress.coerceIn(0.0, 1.0) * PROGRESS_MAX).roundToInt())
                )
            }
            OnboardPhase.ARRIVED -> {
                builder.setContentTitle("Vous êtes arrivé")
                builder.setContentText(destinationName)
                builder.setWhen(state.arrivalDate.toEpochMilli()).setShowWhen(true)
            }
        }
        return builder.build()
    }

    private fun countdownText(state: OnboardActivityState): String? {
        val target = state.targetDate ?: return null
        val minutes = state.countdownMinutes
        return when {
            minutes != null && minutes > 0 -> "$minutes'"
            target.isAfter(Instant.now()) -> "<1'"
            else -> "0'"
        }
    }

    private fun ridingStyle(state: OnboardActivityState): NotificationCompat.ProgressStyle {
        val total = maxOf(2, state.totalStops ?: 2)
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX)
                    .setColor((if (state.isUrgent) OnboardActivityState.UrgentRed else state.accent).toArgb())
            )
            .setProgress((state.progress.coerceIn(0.0, 1.0) * PROGRESS_MAX).roundToInt())
        if (total <= 16) {
            for (index in 1 until total - 1) {
                style.addProgressPoint(
                    NotificationCompat.ProgressStyle.Point(index * PROGRESS_MAX / (total - 1))
                        .setColor(state.accent.toArgb())
                )
            }
        }
        return style
    }

    companion object {
        const val NOTIFICATION_ID = 4_242
        private const val PROGRESS_MAX = 1_000
        private const val STALE_AFTER_MS = 180_000L
        private const val REFRESH_AFTER_MS = 60_000L

        fun endAll(context: Context) {
            currentNotification = null
            runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        }

        @Volatile
        var currentNotification: Notification? = null
            private set
    }
}

class OnboardService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val keep = intent.getBooleanExtra(EXTRA_KEEP, false)
            ServiceCompat.stopForeground(
                this,
                if (keep) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = OnboardLiveActivityController.currentNotification
        if (notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                OnboardLiveActivityController.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        OnboardLiveActivityController.endAll(this)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val ACTION_STOP = "ch.cclerc.luxapp.onboard.STOP"
        private const val EXTRA_KEEP = "keep"

        fun stop(context: Context, keepNotification: Boolean) {
            runCatching {
                context.startService(
                    Intent(context, OnboardService::class.java)
                        .setAction(ACTION_STOP)
                        .putExtra(EXTRA_KEEP, keepNotification)
                )
            }
        }
    }
}
