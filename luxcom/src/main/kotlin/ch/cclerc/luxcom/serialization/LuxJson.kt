package ch.cclerc.luxcom.serialization

import kotlinx.serialization.json.Json

val LuxJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
