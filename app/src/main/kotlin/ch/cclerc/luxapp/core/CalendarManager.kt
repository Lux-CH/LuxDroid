package ch.cclerc.luxapp.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxcom.luxtrip.ItinerarySharer
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Itinerary
import java.time.Instant
import kotlin.math.ceil

sealed class CalendarResult {
    object Launched : CalendarResult()
    data class Failed(val error: CalendarError) : CalendarResult()
}

sealed class CalendarError(val description: String) {
    object NotAuthorized : CalendarError("Accès au calendrier refusé")
    object CalendarCreationFailed :
        CalendarError("Une erreur s'est produite lors de la création du calendrier")

    class SaveFailed(message: String) :
        CalendarError("Impossible de sauvegarder l'événement : $message")
}

object CalendarManager {
    const val calendarTitle = "Itinéraires Lux"
    private const val shareBaseUrl = "https://lux.cclerc.ch/share.html#"
    private const val expirationPaddingSeconds = 21600L

    suspend fun saveItineraryToCalendar(context: Context, itinerary: Itinerary): CalendarResult {
        val shareUrl = uploadShareUrl(itinerary)

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, generateItineraryTitle(itinerary))
            .putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                itinerary.startTime.toEpochMilli()
            )
            .putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                itinerary.endTime.toEpochMilli()
            )
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                generateItineraryNotes(itinerary, shareUrl)
            )
            .putExtra(CalendarContract.Events.HAS_ALARM, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            CalendarResult.Launched
        } catch (error: ActivityNotFoundException) {
            CalendarResult.Failed(CalendarError.CalendarCreationFailed)
        } catch (error: Throwable) {
            CalendarResult.Failed(CalendarError.SaveFailed(error.message ?: error.toString()))
        }
    }

    private suspend fun uploadShareUrl(itinerary: Itinerary): String? {
        val secondsUntilExpiration =
            itinerary.endTime.plusSeconds(expirationPaddingSeconds).epochSecond - Instant.now().epochSecond
        val expiresHours = ceil(secondsUntilExpiration / 3600.0).toInt().coerceAtLeast(1)
        return runCatching {
            shareBaseUrl + ItinerarySharer.uploadItinerary(itinerary, expiresHours)
        }.getOrNull()
    }

    private fun generateItineraryTitle(itinerary: Itinerary): String {
        val firstLeg = itinerary.legs.firstOrNull() ?: return "Itinéraire"
        val lastLeg = itinerary.legs.lastOrNull() ?: return "Itinéraire"

        var fromName = firstLeg.from.name
        var toName = lastLeg.to.name

        if (fromName == "START") {
            fromName = firstLeg.to.name
        }

        if (toName == "END") {
            toName = lastLeg.from.name
        }

        return "$fromName → $toName"
    }

    private fun generateItineraryNotes(itinerary: Itinerary, shareUrl: String?): String {
        val notes = StringBuilder()
        notes.append("--Itinéraire Lux--\n")
        notes.append("Durée : ${formatDuration(itinerary.duration)}\n")
        notes.append("Nombre de transferts : ${itinerary.transfers}\n")
        notes.append("-- --\n")
        notes.append("\n")

        itinerary.legs.forEachIndexed { index, leg ->
            if (leg.mode == TransportationMode.WALK) {
                notes.append("  ${index + 1}. Marchez jusqu'à ${leg.to.name}, ${getTrackType(leg.to.track ?: "inconnu")}\n")
            } else {
                val routeShortName = leg.routeShortName
                val headsign = leg.headsign
                if (routeShortName != null && headsign != null) {
                    notes.append("  ${index + 1}. Prenez le ($routeShortName) en direction de $headsign\n")
                }
                notes.append("      Montez à ${leg.from.name}\n")
                notes.append("      Descendez à ${leg.to.name}\n")
            }
            notes.append("\n")
        }

        if (shareUrl != null) {
            notes.append(shareUrl)
            notes.append("\n")
        }

        return notes.toString()
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
    }
}
