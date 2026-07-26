package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.cbURL
import ch.cclerc.luxcom.model.feedback.InfoResponse
import ch.cclerc.luxcom.model.feedback.Report
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.feedback.ReportResponse
import ch.cclerc.luxcom.net.ApiClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun getLCBInfo(
    tripId: String,
    routeShortName: String,
    latitude: Double,
    longitude: Double,
    attribute: String? = null
): InfoResponse {
    val queryItems = mutableListOf(
        "tripId" to tripId,
        "routeShortName" to routeShortName,
        "location" to "{\"lat\":$latitude,\"long\":$longitude}"
    )

    if (attribute != null) {
        queryItems.add("attribute" to attribute)
    }

    return ApiClient.fetch(
        endpoint = "/info",
        apiVersion = "v1",
        queryItems = queryItems,
        baseUrl = cbURL
    )
}

suspend fun sendLCBReport(report: Report): ReportResponse {
    val endpoint: String
    val body: JsonObject

    when (report.attribute) {
        ReportAttribute.CROWD -> {
            endpoint = "/report/crowd"
            body = buildJsonObject {
                put("tripId", report.tripId)
                put("routeShortName", report.routeShortName)
                put("location", buildJsonObject {
                    put("lat", report.latitude)
                    put("long", report.longitude)
                })
                put("crowdedness", report.level)
            }
        }
        ReportAttribute.SMELL, ReportAttribute.CLEAN, ReportAttribute.HEAT, ReportAttribute.NOISE -> {
            endpoint = "/report/wellness"
            body = buildJsonObject {
                put("tripId", report.tripId)
                put("routeShortName", report.routeShortName)
                put("location", buildJsonObject {
                    put("lat", report.latitude)
                    put("long", report.longitude)
                })
                put("attribute", report.attribute.name.lowercase())
                put("level", report.level)
            }
        }
    }

    return ApiClient.fetch(
        endpoint = endpoint,
        apiVersion = "v1",
        baseUrl = cbURL,
        method = "POST",
        jsonBody = body.toString()
    )
}
