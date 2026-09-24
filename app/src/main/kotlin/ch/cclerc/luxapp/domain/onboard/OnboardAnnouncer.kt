package ch.cclerc.luxapp.domain.onboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import ch.cclerc.luxapp.MainActivity
import ch.cclerc.luxapp.R
import ch.cclerc.luxapp.core.HapticFeedback
import java.util.Locale
import java.util.UUID

class OnboardAnnouncer(context: Context, voiceEnabled: Boolean) {

    enum class Urgency { GUIDANCE, NOTICE, CRITICAL }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pending = mutableListOf<String>()
    private var lastSpoken: Pair<String, Long>? = null
    private var generation = 0
    private var hasFocus = false
    private var speaking = 0

    var voiceEnabled: Boolean = voiceEnabled
    var alertSink: ((title: String, body: String?) -> Boolean)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            handler.post {
                if (status != TextToSpeech.SUCCESS) return@post
                val engine = tts ?: return@post
                engine.setAudioAttributes(attributes)
                engine.language = voiceLocale
                bestVoice(engine)?.let { engine.voice = it }
                engine.setSpeechRate(1.02f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post { utteranceFinished() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post { utteranceFinished() }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        handler.post { utteranceFinished() }
                    }
                })
                ttsReady = true
                val queued = pending.toList()
                pending.clear()
                queued.forEach(::speakNow)
            }
        }
    }

    fun prepare() {
        ensureChannels(appContext)
    }

    fun announce(text: String, notificationTitle: String? = null, urgency: Urgency) {
        val isActive = isAppActive()

        when (urgency) {
            Urgency.GUIDANCE -> Unit
            Urgency.NOTICE -> if (isActive) HapticFeedback.warning()
            Urgency.CRITICAL -> if (isActive) {
                HapticFeedback.warning()
                handler.postDelayed({ HapticFeedback.warning() }, 350)
            }
        }

        if (urgency != Urgency.GUIDANCE && !isActive &&
            alertSink?.invoke(notificationTitle ?: text, if (notificationTitle == null) null else text) != true
        ) {
            postNotification(
                title = notificationTitle ?: text,
                body = if (notificationTitle == null) null else text,
                critical = urgency == Urgency.CRITICAL
            )
        }

        speak(text)
    }

    fun speak(text: String) {
        if (!voiceEnabled) return
        val now = System.currentTimeMillis()
        val last = lastSpoken
        if (last != null && last.first == text && now - last.second < 20_000) return
        lastSpoken = text to now
        if (!ttsReady) {
            pending.add(text)
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        if (!voiceEnabled) return
        if (!hasFocus) {
            hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        speaking += 1
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "onboard-${UUID.randomUUID()}")
    }

    private fun utteranceFinished() {
        speaking = maxOf(0, speaking - 1)
        if (speaking == 0) releaseAudio()
    }

    fun stop() {
        generation += 1
        pending.clear()
        tts?.stop()
        speaking = 0
        releaseAudio()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun releaseAudio() {
        if (!hasFocus) return
        hasFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun isAppActive(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun postNotification(title: String, body: String?, critical: Boolean) {
        if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannels(appContext)
        val intent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(appContext, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_onboard_notification)
            .setContentTitle(title)
            .apply { if (body != null) setContentText(body) }
            .setStyle(body?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setPriority(if (critical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setGroup("onboard")
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        runCatching {
            NotificationManagerCompat.from(appContext).notify(UUID.randomUUID().hashCode(), notification)
        }
    }

    private val voiceLocale: Locale
        get() {
            val language = Locale.getDefault().language
            return if (language == "fr" || language.isEmpty()) Locale.FRANCE else Locale.getDefault()
        }

    private fun bestVoice(engine: TextToSpeech): Voice? {
        val locale = voiceLocale
        val candidates = runCatching { engine.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == locale.language }
        fun rank(voice: Voice): Int {
            val exact = if (voice.locale.country == locale.country) 1 else 0
            val offline = if (!voice.isNetworkConnectionRequired) 1 else 0
            val installed = if (voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) -10 else 0
            return voice.quality / 100 * 10 + exact + offline + installed
        }
        return candidates.maxByOrNull(::rank)
    }

    companion object {
        const val ALERT_CHANNEL_ID = "onboard-alerts"
        const val LIVE_CHANNEL_ID = "onboard-live"

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(ALERT_CHANNEL_ID, "Alertes À bord", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Retards, changements de voie et quand descendre"
                    }
                )
            }
            if (manager.getNotificationChannel(LIVE_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(LIVE_CHANNEL_ID, "Trajet en cours", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Suivi du trajet en mode À bord"
                        setSound(null, null)
                        enableVibration(false)
                    }
                )
            }
        }
    }
}
