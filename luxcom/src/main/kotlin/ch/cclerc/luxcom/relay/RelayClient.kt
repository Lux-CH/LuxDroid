package ch.cclerc.luxcom.relay

import ch.cclerc.luxcom.model.Disruption
import ch.cclerc.luxcom.model.stop.StopTimes
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.relayUrl
import ch.cclerc.luxcom.serialization.LuxJson
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class RelayClient private constructor() {

    companion object {
        val shared = RelayClient()
    }

    private data class SubscriptionKey(
        val channel: String,
        val src: String,
        val id: String,
        val extra: String
    )

    private class Subscriber(
        val deliver: (String) -> Unit,
        val subscribeMessage: String,
        val unsubscribeMessage: String
    )

    @Serializable
    private data class EnvelopeHeader(
        val ch: String,
        val src: String? = null,
        val stopId: String? = null,
        val tripId: String? = null,
        val n: Int? = null,
        val radius: Int? = null
    )

    @Serializable
    private data class Envelope<T>(val data: T)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val client = OkHttpClient.Builder()
        .pingInterval(45, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private var suspendedForBackground = false
    private val subscribers = mutableMapOf<SubscriptionKey, MutableMap<UUID, Subscriber>>()
    private var idleDisconnectJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun departures(stopId: String, n: Int, radius: Int?): Flow<StopTimes> {
        val key = SubscriptionKey("dep", "motis", stopId, "$n|${radius ?: 0}")
        val subscribeMessage = buildJsonObject {
            put("action", "sub_dep")
            put("src", "motis")
            put("stopId", stopId)
            put("n", n)
            if (radius != null) put("radius", radius)
        }.toString()
        val unsubscribeMessage = buildJsonObject {
            put("action", "unsub_dep")
            put("src", "motis")
            put("stopId", stopId)
            put("n", n)
            if (radius != null) put("radius", radius)
        }.toString()
        return stream(key, subscribeMessage, unsubscribeMessage, StopTimes.serializer())
    }

    fun trip(tripId: String): Flow<Itinerary> {
        val key = SubscriptionKey("trip", "motis", tripId, "")
        val subscribeMessage = buildJsonObject {
            put("action", "sub_trip")
            put("src", "motis")
            put("tripId", tripId)
        }.toString()
        val unsubscribeMessage = buildJsonObject {
            put("action", "unsub_trip")
            put("src", "motis")
            put("tripId", tripId)
        }.toString()
        return stream(key, subscribeMessage, unsubscribeMessage, Itinerary.serializer())
    }

    fun disruptions(): Flow<List<Disruption>> {
        val key = SubscriptionKey("dis", "shared", "global", "")
        val subscribeMessage = buildJsonObject {
            put("action", "sub_dis")
        }.toString()
        val unsubscribeMessage = buildJsonObject {
            put("action", "unsub_dis")
        }.toString()
        return stream(key, subscribeMessage, unsubscribeMessage, ListSerializer(Disruption.serializer()))
    }

    fun onAppBackground() {
        scope.launch {
            suspendedForBackground = true
            disconnect()
        }
    }

    fun onAppForeground() {
        scope.launch {
            suspendedForBackground = false
            reconnectAttempt = 0
            if (subscribers.isNotEmpty()) {
                connectIfNeeded()
            }
        }
    }

    private fun <T> stream(
        key: SubscriptionKey,
        subscribeMessage: String,
        unsubscribeMessage: String,
        serializer: KSerializer<T>
    ): Flow<T> = callbackFlow {
        val subscriberId = UUID.randomUUID()
        val envelopeSerializer = Envelope.serializer(serializer)
        val subscriber = Subscriber(
            deliver = { text ->
                val envelope = runCatching { LuxJson.decodeFromString(envelopeSerializer, text) }.getOrNull()
                if (envelope != null) {
                    trySend(envelope.data)
                }
            },
            subscribeMessage = subscribeMessage,
            unsubscribeMessage = unsubscribeMessage
        )
        scope.launch { addSubscriber(subscriber, subscriberId, key) }
        awaitClose { scope.launch { removeSubscriber(subscriberId, key) } }
    }

    private fun addSubscriber(subscriber: Subscriber, id: UUID, key: SubscriptionKey) {
        val isNewKey = subscribers[key] == null
        subscribers.getOrPut(key) { mutableMapOf() }[id] = subscriber

        idleDisconnectJob?.cancel()
        idleDisconnectJob = null
        connectIfNeeded()
        if (_isConnected.value || isNewKey) {
            send(subscriber.subscribeMessage)
        }
    }

    private fun removeSubscriber(id: UUID, key: SubscriptionKey) {
        val keySubscribers = subscribers[key] ?: return
        val subscriber = keySubscribers.remove(id) ?: return

        if (keySubscribers.isEmpty()) {
            subscribers.remove(key)
            send(subscriber.unsubscribeMessage)
            scheduleIdleDisconnect()
        }
    }

    private fun scheduleIdleDisconnect() {
        idleDisconnectJob?.cancel()
        idleDisconnectJob = scope.launch {
            delay(2000)
            idleDisconnectJob = null
            if (subscribers.isEmpty()) {
                disconnect()
            }
        }
    }

    private fun connectIfNeeded() {
        if (webSocket != null || suspendedForBackground) return
        val request = Request.Builder().url(relayUrl).build()
        webSocket = client.newWebSocket(request, listener)
    }

    private fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
        _isConnected.value = false
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            scope.launch { handleOpen(webSocket) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleMessage(webSocket, text) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            scope.launch { handleMessage(webSocket, bytes.utf8()) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch { handleClose(webSocket) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scope.launch { handleClose(webSocket) }
        }
    }

    private fun handleOpen(socket: WebSocket) {
        if (webSocket !== socket) return
        _isConnected.value = true
        reconnectAttempt = 0
        resubscribeAll()
    }

    private fun handleMessage(socket: WebSocket, text: String) {
        if (webSocket !== socket) return
        val header = runCatching {
            LuxJson.decodeFromString(EnvelopeHeader.serializer(), text)
        }.getOrNull() ?: return

        val channel = header.ch
        val src = header.src ?: "shared"
        val id = header.stopId ?: header.tripId ?: "global"
        val extra = header.n?.let { "$it|${header.radius ?: 0}" }

        for ((candidate, keySubscribers) in subscribers) {
            if (candidate.channel != channel || candidate.src != src || candidate.id != id) continue
            if (extra != null && candidate.extra != extra) continue
            for (subscriber in keySubscribers.values) {
                subscriber.deliver(text)
            }
        }
    }

    private fun handleClose(socket: WebSocket) {
        if (webSocket !== socket) return
        disconnect()
        if (subscribers.isEmpty() || suspendedForBackground) return

        reconnectAttempt += 1
        val delaySeconds = min(30.0, 2.0.pow(reconnectAttempt)) * Random.nextDouble(0.8, 1.2)
        scope.launch {
            delay((delaySeconds * 1000).toLong())
            reconnectIfNeeded()
        }
    }

    private fun reconnectIfNeeded() {
        if (webSocket != null || subscribers.isEmpty() || suspendedForBackground) return
        connectIfNeeded()
    }

    private fun resubscribeAll() {
        for (keySubscribers in subscribers.values) {
            val message = keySubscribers.values.firstOrNull()?.subscribeMessage ?: continue
            send(message)
        }
    }

    private fun send(message: String) {
        webSocket?.send(message)
    }
}
