package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.disruptionsUrl
import ch.cclerc.luxcom.model.Disruption
import ch.cclerc.luxcom.net.ApiClient

suspend fun getDisruptions(): List<Disruption> {
    return ApiClient.fetch(endpoint = "", apiVersion = "", baseUrl = disruptionsUrl)
}
