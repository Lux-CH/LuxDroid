package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.map.getMapStops
import ch.cclerc.luxcom.model.PedestrianProfile
import ch.cclerc.luxcom.model.trip.RouteOptions
import ch.cclerc.luxcom.net.ApiClient
import ch.cclerc.luxcom.net.ApiState
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class QueryBuildingTest {

    private lateinit var server: MockWebServer
    private lateinit var originalPrimary: String
    private lateinit var originalBackup: String

    private val time = Instant.parse("2026-07-26T10:15:30Z")

    private val emptyStopTimesJson = """{"stopTimes":[],"previousPageCursor":"","nextPageCursor":""}"""
    private val placeJson = """{"name":"P","lat":0.0,"lon":0.0,"vertexType":"NORMAL"}"""
    private val emptyTripJson =
        """{"from":$placeJson,"to":$placeJson,"direct":[],"itineraries":[],"previousPageCursor":"","nextPageCursor":""}"""

    @BeforeTest
    fun setUp() {
        originalPrimary = ApiClient.primaryBaseUrl
        originalBackup = ApiClient.backupBaseUrl
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        ApiClient.primaryBaseUrl = base
        ApiClient.backupBaseUrl = base
        runBlocking { ApiState.reset() }
    }

    @AfterTest
    fun tearDown() {
        ApiClient.primaryBaseUrl = originalPrimary
        ApiClient.backupBaseUrl = originalBackup
        server.shutdown()
    }

    private fun recordedPath(): String? = server.takeRequest(2, TimeUnit.SECONDS)?.path

    private fun defaultRouteOptions(
        pedestrianSpeed: Double? = null
    ) = RouteOptions(
        from = RouteOptions.RouteLocation("from1"),
        to = RouteOptions.RouteLocation("to1"),
        via = null,
        viaMinimumStay = emptyList(),
        time = null,
        arriveBy = false,
        maxTransfers = 2,
        minTransferTime = 5,
        pedestrianProfile = PedestrianProfile.FOOT,
        pedestrianSpeed = pedestrianSpeed,
        transitModes = null,
        numItineraries = null,
        timetableView = true,
        maxPreTransitTime = null,
        maxPostTransitTime = null
    )

    @Test
    fun stoptimesDefaultQuery() = runBlocking {
        server.enqueue(MockResponse().setBody(emptyStopTimesJson))
        getDeparturesForStop(stopId = "stop1", time = time, numberOfEvents = 6)
        assertEquals(
            "/v4/stoptimes?stopId=stop1&time=2026-07-26T10%3A15%3A30Z&arriveBy=false&n=6&mode=TRANSIT",
            recordedPath()
        )
    }

    @Test
    fun stoptimesBothRadiusAndModeRepetition() = runBlocking {
        server.enqueue(MockResponse().setBody(emptyStopTimesJson))
        getDeparturesForStop(
            stopId = "stop1",
            time = time,
            arriveBy = true,
            both = true,
            mode = listOf("TRANSIT", "WALK"),
            numberOfEvents = 12,
            radius = 250
        )
        assertEquals(
            "/v4/stoptimes?stopId=stop1&time=2026-07-26T10%3A15%3A30Z&arriveBy=true&n=12" +
                "&both=true&mode=TRANSIT&mode=WALK&exactRadius=true&radius=250",
            recordedPath()
        )
    }

    @Test
    fun stoptimesNilModeOmitsModeParameter() = runBlocking {
        server.enqueue(MockResponse().setBody(emptyStopTimesJson))
        getDeparturesForStop(stopId = "stop1", time = time, mode = null, numberOfEvents = 6)
        assertEquals(
            "/v4/stoptimes?stopId=stop1&time=2026-07-26T10%3A15%3A30Z&arriveBy=false&n=6",
            recordedPath()
        )
    }

    @Test
    fun planAlwaysParams() = runBlocking {
        server.enqueue(MockResponse().setBody(emptyTripJson))
        getRoute(defaultRouteOptions())
        assertEquals(
            "/v4/plan?fromPlace=from1&toPlace=to1&arriveBy=false&maxTransfers=2" +
                "&additionalTransferTime=5&pedestrianProfile=FOOT&useRoutedTransfers=true" +
                "&maxMatchingDistance=250&detailedTransfers=true&timetableView=true" +
                "&fastestDirectFactor=1.5",
            recordedPath()
        )
    }

    @Test
    fun planOmitsDefaultPedestrianSpeed() = runBlocking {
        server.enqueue(MockResponse().setBody(emptyTripJson))
        getRoute(defaultRouteOptions(pedestrianSpeed = 1.2))
        assertEquals(
            "/v4/plan?fromPlace=from1&toPlace=to1&arriveBy=false&maxTransfers=2" +
                "&additionalTransferTime=5&pedestrianProfile=FOOT&useRoutedTransfers=true" +
                "&maxMatchingDistance=250&detailedTransfers=true&timetableView=true" +
                "&fastestDirectFactor=1.5",
            recordedPath()
        )
    }

    @Test
    fun planIncludesNonDefaultPedestrianSpeed() = runBlocking {
        server.enqueue(MockResponse().setBody(emptyTripJson))
        getRoute(defaultRouteOptions(pedestrianSpeed = 1.0))
        assertEquals(
            "/v4/plan?fromPlace=from1&toPlace=to1&arriveBy=false&maxTransfers=2" +
                "&additionalTransferTime=5&pedestrianProfile=FOOT&useRoutedTransfers=true" +
                "&maxMatchingDistance=250&detailedTransfers=true&timetableView=true" +
                "&fastestDirectFactor=1.5&pedestrianSpeed=1.0",
            recordedPath()
        )
    }

    @Test
    fun geocodePlaceUsesCommaSpaceFormat() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        geocode(text = "Rive", place = 46.2 to 6.15)
        assertEquals(
            "/v1/geocode?text=Rive&language=fr&place=46.2%2C%206.15",
            recordedPath()
        )
    }

    @Test
    fun mapStopsMinMaxUseCommaSpaceFormat() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        getMapStops(minLat = 46.1, minLon = 6.1, maxLat = 46.3, maxLon = 6.2)
        assertEquals(
            "/v1/map/stops?min=46.1%2C%206.1&max=46.3%2C%206.2",
            recordedPath()
        )
    }
}
