package ch.cclerc.luxapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object Settings {
    internal lateinit var prefs: SharedPreferences
    private var reduceSpacerDefault: Boolean = true

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        reduceSpacerDefault = context.resources.configuration.smallestScreenWidthDp < 600
        if (prefs.getBoolean("firstLaunch", true)) {
            prefs.edit().putBoolean("firstLaunch", false).apply()
        }
    }

    var firstLaunch: Boolean by boolPref("firstLaunch") { true }
    var appLaunchCount: Int by intPref("appLaunchCount", 1)

    var showShortcutLabel: Boolean by boolPref("showShortcutLabel") { true }
    var useTimeBasedRelevance: Boolean by boolPref("useTimeBasedRelevance") { true }

    var reduceSpacerBtwnStopContent: Boolean by boolPref("reduceSpacerBtwnStopContent") { reduceSpacerDefault }
    var highContrastButAccurateLinePill: Boolean by boolPref("highContrastButAccurateLinePill") { false }
    var showDelayInsteadOfDirectTime: Boolean by boolPref("showDelayInsteadOfDirectTime") { false }
    var autoColorScheme: Boolean by boolPref("autoColorScheme") { false }
    var customScheme: Boolean by boolPref("customScheme") { false }
    var customSchemeSelection: String by stringPref("customSchemeSelection", "")
    var easyOnTheEyes: Boolean by boolPref("easyOnTheEyes") { false }
    var showHistory: Boolean by boolPref("showHistory") { true }

    var luxTripShareExpiryTimeH: Int by intPref("luxTripShareExpiryTimeH", 24)
    var crowdbackAllowed: Boolean by boolPref("crowdbackAllowed") { true }
    var showDebug: Boolean by boolPref("showDebug") { false }
}

internal fun boolPref(key: String, default: () -> Boolean): ReadWriteProperty<Any?, Boolean> =
    object : ReadWriteProperty<Any?, Boolean> {
        val state: MutableState<Boolean> by lazy { mutableStateOf(Settings.prefs.getBoolean(key, default())) }
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            Settings.prefs.edit().putBoolean(key, value).apply()
        }
    }

internal fun intPref(key: String, default: Int): ReadWriteProperty<Any?, Int> =
    object : ReadWriteProperty<Any?, Int> {
        val state: MutableState<Int> by lazy { mutableStateOf(Settings.prefs.getInt(key, default)) }
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.value = value
            Settings.prefs.edit().putInt(key, value).apply()
        }
    }

internal fun stringPref(key: String, default: String): ReadWriteProperty<Any?, String> =
    object : ReadWriteProperty<Any?, String> {
        val state: MutableState<String> by lazy { mutableStateOf(Settings.prefs.getString(key, default) ?: default) }
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value
            Settings.prefs.edit().putString(key, value).apply()
        }
    }

internal fun doublePref(key: String, default: Double): ReadWriteProperty<Any?, Double> =
    object : ReadWriteProperty<Any?, Double> {
        val state: MutableState<Double> by lazy {
            val stored = Settings.prefs.getLong(key, default.toRawBits())
            mutableStateOf(Double.fromBits(stored))
        }
        override fun getValue(thisRef: Any?, property: KProperty<*>): Double = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
            state.value = value
            Settings.prefs.edit().putLong(key, value.toRawBits()).apply()
        }
    }
