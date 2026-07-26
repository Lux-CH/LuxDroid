package ch.cclerc.luxapp.domain

import ch.cclerc.luxcom.luxtrip.ExpiredLinkException
import ch.cclerc.luxcom.luxtrip.ItinerarySharer
import ch.cclerc.luxcom.luxtrip.LuxTripCodec
import ch.cclerc.luxcom.model.trip.Itinerary
import java.io.IOException

sealed class UrlHandlerResult {
    data class ItineraryReady(val itinerary: Itinerary) : UrlHandlerResult()
    data class StopDetail(val stopId: String, val name: String) : UrlHandlerResult()
    data class StopPlace(val name: String, val stopId: String) : UrlHandlerResult()
    data class ConfirmationRequired(val id: String) : UrlHandlerResult()
    object LocalFileConfirmationRequired : UrlHandlerResult()
    data class Error(val error: UrlHandlerError) : UrlHandlerResult()
}

sealed class UrlHandlerError(val description: String) {
    object InvalidUrl : UrlHandlerError("URL invalide")
    object FileTooLarge : UrlHandlerError(
        "L'itinéraire partagé est trop volumineux. Pour des raisons de sécurité, Lux ne peut ouvrir ce dernier."
    )
    object InvalidFile : UrlHandlerError("L'itinéraire partagé semble être invalide.")
    object NetworkError : UrlHandlerError(
        "Une erreur est survenue lors du téléchargement. Assurez vous d'avoir une connexion stable."
    )
    object ExpiredLink : UrlHandlerError(
        "Il est possible que l'itinéraire ait expiré ou qu'il soit invalide."
    )
}

object UrlHandler {
    private const val maxFileSize = 51200

    fun process(url: String): UrlHandlerResult {
        if (hasLuxTripExtension(url)) {
            return UrlHandlerResult.LocalFileConfirmationRequired
        }

        if (hasScheme(url, "lux") && hasHost(url, "itinerary")) {
            val identifier = parseItineraryIdentifier(url)
                ?: return UrlHandlerResult.Error(UrlHandlerError.InvalidUrl)
            return UrlHandlerResult.ConfirmationRequired(identifier)
        }

        if (hasScheme(url, "lux") && hasHost(url, "place")) {
            parsePlaceDetailUrl(url)?.let { (stopId, name) ->
                return UrlHandlerResult.StopPlace(name = name, stopId = stopId)
            }
        }

        parseStopDetailUrl(url)?.let { (stopId, name) ->
            return UrlHandlerResult.StopDetail(stopId = stopId, name = name)
        }

        return UrlHandlerResult.Error(UrlHandlerError.InvalidUrl)
    }

    suspend fun handleConfirmedItinerary(id: String): UrlHandlerResult {
        if (id.isEmpty()) {
            return UrlHandlerResult.Error(UrlHandlerError.InvalidUrl)
        }

        return try {
            UrlHandlerResult.ItineraryReady(ItinerarySharer.downloadItinerary(id))
        } catch (error: ExpiredLinkException) {
            UrlHandlerResult.Error(UrlHandlerError.ExpiredLink)
        } catch (error: IOException) {
            UrlHandlerResult.Error(UrlHandlerError.NetworkError)
        } catch (error: Throwable) {
            UrlHandlerResult.Error(UrlHandlerError.InvalidFile)
        }
    }

    fun handleLocalItinerary(data: ByteArray): UrlHandlerResult {
        if (data.size > maxFileSize) {
            return UrlHandlerResult.Error(UrlHandlerError.FileTooLarge)
        }

        val itinerary = runCatching { LuxTripCodec.decode(data) }.getOrNull()
            ?: return UrlHandlerResult.Error(UrlHandlerError.InvalidFile)

        if (!ItinerarySharer.validateItinerary(itinerary)) {
            return UrlHandlerResult.Error(UrlHandlerError.InvalidFile)
        }

        return UrlHandlerResult.ItineraryReady(itinerary)
    }

    private fun hasLuxTripExtension(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').endsWith(".luxtrip")

    private fun hasScheme(url: String, scheme: String): Boolean =
        url.contains("://") && url.substringBefore("://").lowercase() == scheme

    private fun hasHost(url: String, host: String): Boolean =
        body(url)?.substringBefore('/') == host

    private fun body(url: String): String? {
        if (!url.contains("://")) {
            return null
        }
        return url.split("://").last()
    }

    private fun parseItineraryIdentifier(url: String): String? {
        val body = body(url) ?: return null
        val remainder = body.removePrefix("itinerary").trimStart('/')
        val identifier = remainder.substringBefore('/')
        return identifier.takeIf { it.isNotEmpty() }
    }

    private fun parseStopDetailUrl(url: String): Pair<String, String>? {
        val body = body(url) ?: url
        if (!body.contains("//")) {
            return null
        }

        val components = body.split("//")
        if (components.size != 2) {
            return null
        }

        val decodedName = removingPercentEncoding(components[1]) ?: return null
        return components[0] to decodedName
    }

    private fun parsePlaceDetailUrl(url: String): Pair<String, String>? {
        val body = body(url) ?: return null
        val cleaned = body.replace("place/", "")

        val lastHyphenIndex = cleaned.lastIndexOf('-')
        if (lastHyphenIndex < 0) {
            return null
        }

        val encodedName = cleaned.substring(0, lastHyphenIndex)
        val stopIdSuffix = cleaned.substring(lastHyphenIndex + 1)

        val decodedName = removingPercentEncoding(encodedName) ?: return null
        if (decodedName.isEmpty() || stopIdSuffix.isEmpty()) {
            return null
        }

        return "ch_Parent$stopIdSuffix" to decodedName
    }

    private fun removingPercentEncoding(value: String): String? {
        if (!value.contains('%')) {
            return value
        }

        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '%') {
                if (index + 2 >= value.length) {
                    return null
                }
                val hex = value.substring(index + 1, index + 3)
                val byte = hex.toIntOrNull(16) ?: return null
                bytes.add(byte.toByte())
                index += 3
            } else {
                for (encoded in character.toString().toByteArray(Charsets.UTF_8)) {
                    bytes.add(encoded)
                }
                index += 1
            }
        }

        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
