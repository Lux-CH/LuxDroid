package ch.cclerc.luxapp.domain.intelligence

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.cclerc.luxapp.MainActivity
import ch.cclerc.luxapp.R
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.data.ShortcutStorage
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.onboard.OnboardSession
import ch.cclerc.luxapp.domain.onboard.isTransit
import ch.cclerc.luxapp.domain.onboard.savedRouteOptions
import ch.cclerc.luxapp.ui.itinerary.formatTime
import ch.cclerc.luxcom.api.geocode
import ch.cclerc.luxcom.api.getRoute
import ch.cclerc.luxcom.model.trip.RouteOptions
import com.google.android.gms.location.LocationServices
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object DepartureAlertPlanner {
    private const val WORK_NAME = "ch.cclerc.luxapp.departure-alerts"
    private const val CHANNEL_ID = "intelligent-departure"
    private const val IDS_KEY = "intelligentDepartureIds"
    private const val IDENTIFIER_PREFIX = "intelligent-departure-"
    private const val HORIZON_S = 2 * 3600L
    private const val MAX_ALERTS = 8

    private class Target(
        val id: String,
        val name: String,
        val destination: LatLng,
        val stopId: String?,
        val time: Instant,
        val arriveBy: Boolean
    )

    private class Alert(val id: String, val fireAt: Instant, val title: String, val body: String)

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var lastRefresh = Instant.EPOCH

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun setEnabled(enabled: Boolean) {
        IntelligenceStore.departureAlerts = enabled
        if (enabled) {
            refresh(force = true)
        } else {
            refreshJob?.cancel()
            clearPending()
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
        }
    }

    fun refresh(force: Boolean = false) {
        if (!IntelligenceStore.departureAlerts) return
        if (!force && Instant.now().epochSecond - lastRefresh.epochSecond <= 10 * 60) return
        lastRefresh = Instant.now()
        refreshJob?.cancel()
        refreshJob = scope.launch { plan() }
        scheduleBackgroundRefresh()
    }

    private fun scheduleBackgroundRefresh() {
        if (!IntelligenceStore.departureAlerts) return
        val request = PeriodicWorkRequestBuilder<DepartureAlertWorker>(45, TimeUnit.MINUTES).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    internal suspend fun plan() {
        if (!IntelligenceStore.departureAlerts) return
        val location = lastLocation()
        if (location == null || System.currentTimeMillis() - location.second > 15 * 60 * 1000) {
            clearPending()
            return
        }
        val origin = location.first

        val now = Instant.now()
        val underway = OnboardSession.active?.takeIf { it.isRunning }?.finalDestination
        val targets = (shortcutTargets(now) + calendarTargets(now))
            .filter { it.destination.distanceTo(origin) > 500 }
            .filter { target -> underway?.let { target.destination.distanceTo(it) > 500 } ?: true }
            .sortedBy { it.time }
            .take(MAX_ALERTS)

        val alerts = mutableListOf<Alert>()
        for (target in targets) {
            kotlin.coroutines.coroutineContext.ensureActive()
            alert(target, origin, now)?.let { alerts.add(it) }
        }
        kotlin.coroutines.coroutineContext.ensureActive()
        clearPending()
        alerts.forEach(::schedule)
    }

    private suspend fun alert(target: Target, origin: LatLng, now: Instant): Alert? {
        val destination = target.stopId?.let { RouteOptions.RouteLocation(it) }
            ?: RouteOptions.RouteLocation(target.destination.latitude, target.destination.longitude)
        val options = savedRouteOptions(
            RouteOptions.RouteLocation(origin.latitude, origin.longitude),
            destination,
            if (target.arriveBy) target.time else target.time.minusSeconds(10 * 60)
        ).copy(arriveBy = target.arriveBy, numLegAlternatives = null)
        val trip = runCatching { getRoute(options) }.getOrNull() ?: return null

        val candidates = (trip.itineraries + trip.direct).filter { itinerary ->
            if (target.arriveBy) {
                !itinerary.endTime.isAfter(target.time.plusSeconds(60))
            } else {
                !itinerary.startTime.isBefore(target.time.minusSeconds(10 * 60)) &&
                    !itinerary.startTime.isAfter(target.time.plusSeconds(25 * 60))
            }
        }
        val weather = WeatherService.snapshot(origin.latitude, origin.longitude, target.time)
        val context = TripIntelligence.Context(weather = weather, arriveBy = target.arriveBy, crowd = emptyMap())
        val suggestion = TripIntelligence.suggest(candidates, context) ?: return null

        val itinerary = suggestion.itinerary
        val fireAt = itinerary.startTime.minusSeconds(if (target.arriveBy) 5 * 60L else 3 * 60L)
        if (!fireAt.isAfter(now.plusSeconds(60))) return null

        val title = "Partez maintenant pour ${target.name}"
        val transit = itinerary.legs.firstOrNull { it.isTransit }
        var body = if (transit != null) {
            val line = transit.routeShortName ?: ""
            "Prenez $line à ${formatTime(transit.startTime)} depuis ${transit.from.name}, arrivée à ${formatTime(itinerary.endTime)}."
        } else {
            "À pied, arrivée à ${formatTime(itinerary.endTime)}."
        }
        val isFastest = suggestion.chosen.itinerary.intelligenceSignature == suggestion.fastest.itinerary.intelligenceSignature
        val reason = suggestion.reasons.firstOrNull()
        if (!isFastest && reason != null) {
            body += " " + reason.text + "."
        }
        return Alert(IDENTIFIER_PREFIX + target.id, fireAt, title, body)
    }

    private fun shortcutTargets(now: Instant): List<Target> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return ShortcutStorage().loadShortcuts().flatMap { shortcut ->
            val schedule = shortcut.timeSchedule ?: return@flatMap emptyList()
            (0..1).mapNotNull { offset ->
                val day = today.plusDays(offset.toLong())
                if (schedule.daysOfWeek.none { it.rawValue == day.dayOfWeek.value }) return@mapNotNull null
                val time = day.atTime(schedule.time.hour, schedule.time.minute).atZone(zone).toInstant()
                if (!time.isAfter(now.plusSeconds(10 * 60)) || !time.isBefore(now.plusSeconds(HORIZON_S))) return@mapNotNull null
                Target(
                    id = "shortcut-${shortcut.id}-${time.epochSecond}",
                    name = shortcut.name,
                    destination = LatLng(shortcut.coordinates.latitude, shortcut.coordinates.longitude),
                    stopId = shortcut.stopId,
                    time = time,
                    arriveBy = false
                )
            }
        }
    }

    fun hasCalendarAccess(context: Context = appContext): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private suspend fun calendarTargets(now: Instant): List<Target> {
        if (!hasCalendarAccess()) return emptyList()
        data class Event(val id: Long, val title: String?, val location: String, val begin: Long)

        val events = withContext(Dispatchers.IO) {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now.plusSeconds(20 * 60).toEpochMilli())
            android.content.ContentUris.appendId(builder, now.plusSeconds(HORIZON_S).toEpochMilli())
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME
            )
            val found = mutableListOf<Event>()
            runCatching {
                appContext.contentResolver.query(builder.build(), projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val location = cursor.getString(2)?.trim().orEmpty()
                        if (cursor.getInt(4) != 0 || location.isEmpty() || cursor.getString(5) == "Itinéraires Lux") continue
                        found.add(Event(cursor.getLong(0), cursor.getString(1), location, cursor.getLong(3)))
                    }
                }
            }
            found
        }

        return events.mapNotNull { event ->
            val place = runCatching { geocode(event.location) }.getOrNull()?.firstOrNull() ?: return@mapNotNull null
            val start = Instant.ofEpochMilli(event.begin)
            Target(
                id = "event-${event.id}-${start.epochSecond}",
                name = event.title ?: "votre rendez-vous",
                destination = LatLng(place.lat, place.lon),
                stopId = null,
                time = start.minusSeconds(5 * 60),
                arriveBy = true
            )
        }
    }

    private suspend fun lastLocation(): Pair<LatLng, Long>? {
        val granted = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                LocationServices.getFusedLocationProviderClient(appContext).lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { LatLng(it.latitude, it.longitude) to it.time })
                    }
                    .addOnFailureListener { continuation.resume(null) }
            } catch (_: SecurityException) {
                continuation.resume(null)
            }
        }
    }

    private fun alarmIntent(id: String, title: String? = null, body: String? = null): PendingIntent {
        val intent = Intent(appContext, DepartureAlertReceiver::class.java)
            .setAction(id)
            .putExtra("id", id)
            .apply {
                if (title != null) putExtra("title", title)
                if (body != null) putExtra("body", body)
            }
        return PendingIntent.getBroadcast(
            appContext,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun schedule(alert: Alert) {
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.fireAt.toEpochMilli(), alarmIntent(alert.id, alert.title, alert.body))
        val ids = pendingIds() + alert.id
        Settings.prefs.edit().putStringSet(IDS_KEY, ids).apply()
    }

    private fun pendingIds(): Set<String> = Settings.prefs.getStringSet(IDS_KEY, emptySet()).orEmpty().toSet()

    private fun clearPending() {
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        pendingIds().filter { it.startsWith(IDENTIFIER_PREFIX) }.forEach { manager.cancel(alarmIntent(it)) }
        Settings.prefs.edit().remove(IDS_KEY).apply()
    }

    internal fun post(context: Context, id: String, title: String, body: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Alertes de départ", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Quand partir pour vos raccourcis programmés et vos rendez-vous"
                }
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_onboard_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(CHANNEL_ID)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
    }
}

class DepartureAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: return
        DepartureAlertPlanner.post(context, id, title, body)
    }
}

class DepartureAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DepartureAlertPlanner.init(applicationContext)
        DepartureAlertPlanner.plan()
        return Result.success()
    }
}
