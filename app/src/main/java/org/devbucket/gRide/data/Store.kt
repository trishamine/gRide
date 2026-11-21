package org.devbucket.gRide.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

enum class UiStyle(val label: String) { EXPRESSIVE("Material 3 Expressive"), LIQUID_GLASS("Liquid Glass") }
enum class ThemeMode(@androidx.annotation.StringRes val label: Int) {
    SYSTEM(org.devbucket.gRide.R.string.theme_system), LIGHT(org.devbucket.gRide.R.string.theme_light), DARK(org.devbucket.gRide.R.string.theme_dark)
}

data class Settings(
    val uiStyle: UiStyle = UiStyle.EXPRESSIVE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoRefresh: Boolean = true,
    /** false until the first-run setup was finished (or after "Replay setup"). */
    val setupDone: Boolean = false,
    /** Secret settings are shown after tapping the version 5 times. */
    val secretUnlocked: Boolean = false,
)

/** Favourites, history and settings, persisted in SharedPreferences as JSON. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("gride", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _favoriteStops = MutableStateFlow(loadPlaces("favoriteStops"))
    val favoriteStops: StateFlow<List<Place>> = _favoriteStops.asStateFlow()

    private val _stopHistory = MutableStateFlow(loadPlaces("stopHistory"))
    val stopHistory: StateFlow<List<Place>> = _stopHistory.asStateFlow()

    private val _trips = MutableStateFlow(loadTrips())
    val trips: StateFlow<List<TripEntry>> = _trips.asStateFlow()

    // ------------------------------------------------------------ settings
    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putString("uiStyle", s.uiStyle.name)
            .putString("themeMode", s.themeMode.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("autoRefresh", s.autoRefresh)
            .putBoolean("setupDone", s.setupDone)
            .putBoolean("secretUnlocked", s.secretUnlocked)
            .apply()
    }

    private fun loadSettings() = Settings(
        uiStyle = enumOr(prefs.getString("uiStyle", null), UiStyle.EXPRESSIVE),
        themeMode = enumOr(prefs.getString("themeMode", null), ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamicColor", false),
        autoRefresh = prefs.getBoolean("autoRefresh", true),
        setupDone = prefs.getBoolean("setupDone", false),
        secretUnlocked = prefs.getBoolean("secretUnlocked", false),
    )

    // --------------------------------------------------------------- stops
    fun isFavorite(place: Place) = _favoriteStops.value.any { it.id == place.id }

    fun toggleFavorite(place: Place) {
        val list = _favoriteStops.value
        _favoriteStops.value = if (list.any { it.id == place.id }) list.filterNot { it.id == place.id } else list + place.copy(distanceMeters = null)
        savePlaces("favoriteStops", _favoriteStops.value)
    }

    fun addStopToHistory(place: Place) {
        if (place.type == LocationType.COORD) return
        _stopHistory.value = (listOf(place.copy(distanceMeters = null)) + _stopHistory.value.filterNot { it.id == place.id }).take(20)
        savePlaces("stopHistory", _stopHistory.value)
    }

    fun removeStopFromHistory(place: Place) {
        _stopHistory.value = _stopHistory.value.filterNot { it.id == place.id }
        savePlaces("stopHistory", _stopHistory.value)
    }

    /** Stores a newer copy (e.g. with a resolved platform id) wherever this stop is saved. */
    fun updatePlace(place: Place) {
        fun List<Place>.upd() = map { if (it.id == place.id) place.copy(distanceMeters = null) else it }
        _favoriteStops.value = _favoriteStops.value.upd(); savePlaces("favoriteStops", _favoriteStops.value)
        _stopHistory.value = _stopHistory.value.upd(); savePlaces("stopHistory", _stopHistory.value)
        _trips.value = _trips.value.map { t ->
            t.copy(from = if (t.from.id == place.id) place else t.from, to = if (t.to.id == place.id) place else t.to)
        }
        saveTrips()
    }

    // --------------------------------------------------------------- trips
    fun addTrip(from: Place, to: Place) {
        if (from.type == LocationType.COORD || to.type == LocationType.COORD) return
        val existing = _trips.value.firstOrNull { it.from.id == from.id && it.to.id == to.id }
        val entry = TripEntry(from, to, existing?.favorite ?: false, System.currentTimeMillis())
        _trips.value = sortTrips(listOf(entry) + _trips.value.filterNot { it.key == entry.key }).take(30)
        saveTrips()
    }

    fun toggleTripFavorite(entry: TripEntry) {
        _trips.value = sortTrips(_trips.value.map { if (it.key == entry.key) it.copy(favorite = !it.favorite) else it })
        saveTrips()
    }

    fun removeTrip(entry: TripEntry) {
        _trips.value = _trips.value.filterNot { it.key == entry.key }
        saveTrips()
    }

    fun clearHistory() {
        _trips.value = _trips.value.filter { it.favorite }
        _stopHistory.value = emptyList()
        saveTrips()
        savePlaces("stopHistory", emptyList())
    }

    private fun sortTrips(list: List<TripEntry>) =
        list.sortedWith(compareByDescending<TripEntry> { it.favorite }.thenByDescending { it.lastUsed })

    // ------------------------------------------------------------- storage
    private fun loadPlaces(key: String): List<Place> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { Place.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun savePlaces(key: String, list: List<Place>) {
        prefs.edit().putString(key, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    private fun loadTrips(): List<TripEntry> = runCatching {
        val arr = JSONArray(prefs.getString("trips", "[]"))
        (0 until arr.length()).map { TripEntry.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveTrips() {
        prefs.edit().putString("trips", JSONArray(_trips.value.map { it.toJson() }).toString()).apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(default)
}
