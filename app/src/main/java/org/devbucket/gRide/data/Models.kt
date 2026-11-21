package org.devbucket.gRide.data

import androidx.annotation.StringRes
import org.devbucket.gRide.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

val TransitZone: ZoneId = ZoneId.of("Europe/Berlin")

/** Transport modes, derived from the MOTIS / GTFS mode of a trip. */
enum class Mode(@StringRes val label: Int) {
    TRAIN(R.string.mode_train),
    SUBURBAN(R.string.mode_sbahn),
    TRAM(R.string.mode_tram),
    CITY_BUS(R.string.mode_city_bus),
    REGIONAL_BUS(R.string.mode_regional_bus),
    ON_DEMAND(R.string.mode_on_demand),
    WALK(R.string.mode_walk),
    OTHER(R.string.mode_other);

    companion object {
        /** In Augsburg, buses with 3-digit numbers are AVV regional buses, the others swa city buses. */
        fun fromMotis(mode: String?, line: String = ""): Mode = when (mode) {
            "TRAM", "CABLE_CAR" -> TRAM
            "BUS", "COACH" -> if ((line.filter { it.isDigit() }.toIntOrNull() ?: 0) >= 100) REGIONAL_BUS else CITY_BUS
            "SUBURBAN", "METRO" -> SUBURBAN
            "SUBWAY", "RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL" -> TRAIN
            "ODM", "FLEX", "RIDE_SHARING" -> ON_DEMAND
            "WALK", "BIKE", "CAR" -> WALK
            else -> OTHER
        }
    }
}

/** Groups used for filters in the UI and for excluding modes in trip requests. */
enum class ModeGroup(@StringRes val label: Int, val motis: List<String>) {
    TRAIN(R.string.mode_train, listOf("SUBWAY", "RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "SUBURBAN")),
    TRAM(R.string.mode_tram, listOf("TRAM")),
    BUS(R.string.group_bus, listOf("BUS", "COACH")),
    ON_DEMAND(R.string.mode_on_demand, listOf("ODM"));

    companion object {
        fun of(mode: Mode): ModeGroup? = when (mode) {
            Mode.TRAIN, Mode.SUBURBAN -> TRAIN
            Mode.TRAM -> TRAM
            Mode.CITY_BUS, Mode.REGIONAL_BUS -> BUS
            Mode.ON_DEMAND -> ON_DEMAND
            else -> null
        }
    }
}

enum class LocationType { STOP, ADDRESS, POI, PLACE, COORD }

data class Place(
    val id: String,
    val name: String,          // e.g. "Königsplatz"
    val locality: String,      // e.g. "Augsburg"
    val type: LocationType,
    val lat: Double? = null,
    val lon: Double? = null,
    val modes: Set<Mode> = emptySet(),
    val distanceMeters: Int? = null,
    /** A concrete platform id for API requests (the [id] identifies the whole station). */
    val apiId: String = "",
) {
    val fullName: String
        get() = if (locality.isNotBlank() && !name.contains(locality)) "$name, $locality" else name

    val hasCoord: Boolean get() = lat != null && lon != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("locality", locality); put("type", type.name)
        lat?.let { put("lat", it) }; lon?.let { put("lon", it) }
        put("modes", JSONArray(modes.map { it.name }))
        put("apiId", apiId)
    }

    companion object {
        fun fromJson(o: JSONObject) = Place(
            id = o.getString("id"),
            name = o.getString("name"),
            locality = o.optString("locality"),
            type = runCatching { LocationType.valueOf(o.optString("type")) }.getOrDefault(LocationType.STOP),
            lat = if (o.has("lat")) o.getDouble("lat") else null,
            lon = if (o.has("lon")) o.getDouble("lon") else null,
            modes = o.optJSONArray("modes")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { Mode.valueOf(a.getString(it)) }.getOrNull() }.toSet()
            } ?: emptySet(),
            apiId = o.optString("apiId"),
        )

        fun currentLocation(lat: Double, lon: Double, name: String = "Current location") = Place(
            id = "coord:%.6f:%.6f".format(java.util.Locale.ROOT, lon, lat),
            name = name,
            locality = "",
            type = LocationType.COORD,
            lat = lat,
            lon = lon,
        )
    }
}

data class Departure(
    val planned: Instant?,
    val estimated: Instant?,
    val line: String,
    val lineName: String,
    val mode: Mode,
    val destination: String,
    val platform: String,
    val realtime: Boolean,
    val cancelled: Boolean,
    val tripId: String = "",
    val stopId: String = "",
    val origin: String = "",
) {
    val time: Instant get() = estimated ?: planned ?: Instant.EPOCH
    val delayMinutes: Long?
        get() = if (planned != null && estimated != null) Duration.between(planned, estimated).toMinutes() else null
}

data class LatLon(val lat: Double, val lon: Double)

data class StopTime(val name: String, val time: Instant?)

data class LegPoint(
    val name: String,
    val platform: String,
    val planned: Instant?,
    val estimated: Instant?,
    val coord: LatLon?,
) {
    val time: Instant? get() = estimated ?: planned
    val delayMinutes: Long?
        get() = if (planned != null && estimated != null) Duration.between(planned, estimated).toMinutes() else null
}

data class Leg(
    val mode: Mode,
    val line: String,
    val lineName: String,
    val towards: String,
    val from: LegPoint,
    val to: LegPoint,
    val durationSeconds: Int,
    val path: List<LatLon>,
    val intermediateStops: List<StopTime>,
    val cancelled: Boolean,
) {
    val isWalk: Boolean get() = mode == Mode.WALK
}

data class Journey(val legs: List<Leg>, val changes: Int) {
    val departure: Instant? get() = legs.firstOrNull()?.from?.time
    val arrival: Instant? get() = legs.lastOrNull()?.to?.time
    val durationMinutes: Long
        get() {
            val d = departure; val a = arrival
            return if (d != null && a != null) Duration.between(d, a).toMinutes() else 0
        }
    val firstTransit: Leg? get() = legs.firstOrNull { !it.isWalk }
    val departureDelay: Long? get() = firstTransit?.from?.delayMinutes
    val path: List<LatLon> get() = legs.flatMap { it.path }
}

data class LineRef(val number: String, val mode: Mode)

data class Attachment(val title: String, val url: String)

data class Disruption(
    val id: String,
    val kind: String,
    val provider: String,
    val stopIds: List<String>,
    val title: String,
    val html: String,
    val lines: List<LineRef>,
    val validFrom: Instant?,
    val validTo: Instant?,
    val attachments: List<Attachment>,
)

data class TripEntry(val from: Place, val to: Place, val favorite: Boolean, val lastUsed: Long) {
    val key: String get() = "${from.id}|${to.id}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("from", from.toJson()); put("to", to.toJson()); put("favorite", favorite); put("lastUsed", lastUsed)
    }

    companion object {
        fun fromJson(o: JSONObject) = TripEntry(
            Place.fromJson(o.getJSONObject("from")),
            Place.fromJson(o.getJSONObject("to")),
            o.optBoolean("favorite"),
            o.optLong("lastUsed"),
        )
    }
}

data class RunStop(
    val id: String,
    val name: String,
    val platform: String,
    val arrPlanned: Instant?,
    val arrEstimated: Instant?,
    val depPlanned: Instant?,
    val depEstimated: Instant?,
    val coord: LatLon?,
) {
    val planned: Instant? get() = depPlanned ?: arrPlanned
    val estimated: Instant? get() = depEstimated ?: arrEstimated
    val time: Instant? get() = estimated ?: planned
    val delayMinutes: Long?
        get() {
            val p = planned; val e = estimated
            return if (p != null && e != null) Duration.between(p, e).toMinutes() else null
        }
}

/** One vehicle's complete run; [here] is the index of the stop the departure was picked at. */
data class VehicleRun(val departure: Departure, val stops: List<RunStop>, val here: Int, val path: List<LatLon>) {
    /** Index of the last stop the vehicle has already left (by realtime when available), or -1. */
    fun lastPassed(now: Instant = Instant.now()): Int = stops.indexOfLast { s -> s.time?.let { it < now } == true }
}
