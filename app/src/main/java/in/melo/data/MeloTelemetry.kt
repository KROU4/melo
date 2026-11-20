package `in`.melo.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import `in`.melo.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Aggregate-only opt-out gated telemetry. No account, track, artist, or query data is accepted here. */
object MeloTelemetry {
    private const val PREFS = "melo_telemetry"
    private const val INSTALL_ID = "install_id"
    private const val HEARTBEAT_MS = 5 * 60_000L
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var installationId = ""
    private var enabled = false
    private var appOpen = false
    private var playbackActive = false
    private var activeSource = "other"
    private var startedActivities = 0
    private var heartbeat: Job? = null
    private val metrics = ArrayList<JSONObject>()
    private val inFlight = LinkedHashSet<Call>()

    fun initialize(application: Application, analyticsEnabled: Boolean) {
        synchronized(lock) {
            if (installationId.isEmpty()) {
                val preferences = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                installationId = preferences.getString(INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
                    preferences.edit().putString(INSTALL_ID, it).apply()
                }
            }
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                val becameOpen = synchronized(lock) { startedActivities++ == 0 }
                if (becameOpen) setAppOpen(true)
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityStopped(activity: Activity) {
                val becameClosed = synchronized(lock) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                    startedActivities == 0
                }
                if (becameClosed) scope.launch {
                    delay(2_000)
                    if (synchronized(lock) { startedActivities == 0 }) setAppOpen(false)
                }
            }
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        setEnabled(analyticsEnabled)
    }

    fun setEnabled(value: Boolean) {
        if (BuildConfig.MELO_STATS_ENDPOINT.isBlank()) return
        val callsToCancel = synchronized(lock) {
            if (enabled == value) return
            enabled = value
            if (value) {
                heartbeat = scope.launch {
                    if (synchronized(lock) { appOpen || playbackActive }) sendNow()
                    while (isActive) {
                        delay(HEARTBEAT_MS)
                        if (synchronized(lock) { enabled && (appOpen || playbackActive) }) sendNow()
                    }
                }
            } else {
                heartbeat?.cancel()
                heartbeat = null
                metrics.clear()
            }
            if (value) emptyList() else inFlight.toList().also { inFlight.clear() }
        }
        callsToCancel.forEach(Call::cancel)
    }

    fun setAppOpen(value: Boolean) {
        val changed = synchronized(lock) {
            if (appOpen == value) false else { appOpen = value; true }
        }
        if (changed) sendNow()
    }

    fun setPlaybackActive(value: Boolean, source: String) {
        val normalizedSource = source.takeIf { it in SOURCES } ?: "other"
        val changed = synchronized(lock) {
            val didChange = playbackActive != value || (value && activeSource != normalizedSource)
            playbackActive = value
            if (value) activeSource = normalizedSource
            didChange
        }
        if (changed) sendNow()
    }

    fun recordPlaybackStart(source: String) = addMetric(JSONObject()
        .put("type", "play_start").put("source", source.normalizedSource()))

    fun recordListeningMs(amount: Long, source: String) {
        val bounded = amount.coerceIn(0L, HEARTBEAT_MS)
        if (bounded > 0) addMetric(JSONObject().put("type", "reported_listening_ms")
            .put("amount", bounded).put("source", source.normalizedSource()), send = false)
    }

    fun recordBufferingMs(amount: Long, source: String) {
        val bounded = amount.coerceIn(0L, HEARTBEAT_MS)
        if (bounded > 0) addMetric(JSONObject().put("type", "buffering_ms")
            .put("amount", bounded).put("source", source.normalizedSource()), send = false)
    }

    fun recordPlaybackError(source: String, category: String) {
        val safeCategory = category.takeIf { it in ERROR_CATEGORIES } ?: "other"
        addMetric(JSONObject().put("type", "playback_error").put("source", source.normalizedSource())
            .put("category", safeCategory))
    }

    fun recordFeature(feature: String) {
        val safeFeature = feature.takeIf { it in FEATURES } ?: "other"
        synchronized(lock) {
            if (!enabled || metrics.size >= MAX_PENDING_METRICS) return
            if (metrics.any { it.optString("type") == "feature_use" && it.optString("feature") == safeFeature }) return
            metrics += JSONObject().put("type", "feature_use").put("feature", safeFeature)
        }
    }

    private fun String.normalizedSource(): String = takeIf { it in SOURCES } ?: "other"

    private fun addMetric(metric: JSONObject, send: Boolean = true) {
        synchronized(lock) {
            if (!enabled || metrics.size >= MAX_PENDING_METRICS) return
            metrics += metric
        }
        if (send) sendNow()
    }

    private fun sendNow() {
        val endpoint = BuildConfig.MELO_STATS_ENDPOINT
        if (endpoint.isBlank()) return
        val payload = synchronized(lock) {
            if (!enabled || installationId.isEmpty()) return
            JSONObject()
                .put("id", installationId)
                .put("platform", "android")
                .put("appOpen", appOpen)
                .put("playbackActive", playbackActive)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("metrics", JSONArray(metrics.toList()).also { metrics.clear() })
                .toString()
        }
        scope.launch {
            val call = client.newCall(Request.Builder().url(endpoint)
                .post(payload.toRequestBody(jsonType)).build())
            val registered = synchronized(lock) {
                if (!enabled) false else inFlight.add(call)
            }
            if (!registered) return@launch
            try {
                call.execute().use { }
            } catch (_: Exception) {
                // Stats are best-effort; failed posts do not retry or retain a local event history.
            } finally {
                synchronized(lock) { inFlight.remove(call) }
            }
        }
    }

    private const val MAX_PENDING_METRICS = 24
    private val SOURCES = setOf("spotify", "youtube_music", "local", "cast", "other")
    private val ERROR_CATEGORIES = setOf("network", "decoder", "authorization", "unavailable", "other")
    private val FEATURES = setOf("search", "radio", "lyrics", "downloads", "queue", "podcasts", "other")
}
