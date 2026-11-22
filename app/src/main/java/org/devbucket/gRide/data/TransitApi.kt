package org.devbucket.gRide.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.cos

/** Thrown while the server asks us to slow down (HTTP 429). */
class RateLimitedException(val until: Instant) : IOException(
    "Too many requests. gRide pauses requests until ${until.atZone(TransitZone).toLocalTime().withNano(0)}."
)

/**
 * Client for Transitous (https://transitous.org), a free community API based on MOTIS and
 * Germany's official DELFI timetable + realtime data. Non-commercial use; please keep the
 * number of requests modest (see https://transitous.org/api/).
 */
object TransitApi {
    private const val BASE = "https://api.transitous.org/api/v1"
    private const val USER_AGENT = "gRide/1.0 (Android; personal, non-commercial)"
    private const val CACHE_MS = 20_000L

    @Volatile private var blockedUntil: Instant = Instant.EPOCH
    private val cache = object : LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?) = size > 60
    }

    private suspend fun get(path: String, params: List<Pair<String, Any>>, cacheMs: Long = CACHE_MS): String =
        withContext(Dispatchers.IO) {
            val query = params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v.toString(), "UTF-8") }
            val url = "$BASE$path?$query"
            synchronized(cache) {
                cache[url]?.let { (t, body) -> if (System.currentTimeMillis() - t < cacheMs) return@withContext body }
            }
            if (Instant.now() < blockedUntil) throw RateLimitedException(blockedUntil)
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            try {
                val code = conn.responseCode
                if (code == 429) {
                    val wait = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(30, 3600) ?: 120
                    blockedUntil = Instant.now().plusSeconds(wait)
                    throw RateLimitedException(blockedUntil)
                }
                if (code !in 200..299) throw HttpStatusException(code)
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                synchronized(cache) { cache[url] = System.currentTimeMillis() to body }
                body
            } finally {
                conn.disconnect()
            }
        }

    // ---------------------------------------------------------------- stops

    /**
     * Searches stops (or any place). Results in Augsburg come first, then the AVV area, then Bavaria,
     * unless the query names another town.
     */
    suspend fun findLocations(query: String, stopsOnly: Boolean): List<Place> {
        val q = query.trim()
        val params = mutableListOf<Pair<String, Any>>("text" to q, "language" to "de", "place" to "48.3665,10.8986")
        if (stopsOnly) params += "type" to "STOP"
        val arr = JSONArray(get("/geocode", params, cacheMs = 120_000))
        val matches = arr.objects()
            .filter { !stopsOnly || it.optString("type") == "STOP" }
            .filter { it.optString("type") != "STOP" || it.optString("id").startsWith("de-") }
        return matches
            .withIndex()
            .sortedWith(compareBy({ if (townMentioned(it.value, q)) 0 else 1 }, { regionRank(it.value) }, { it.index }))
            .map { parseMatch(it.value) }
            .distinctBy { it.id }
    }

    private fun areas(o: JSONObject): List<String> = o.optJSONArray("areas").objects().map { it.optString("name") }

    /** The town of a result: the area MOTIS marks as default (e.g. "Augsburg", not the district). */
    private fun town(o: JSONObject): String {
        val list = o.optJSONArray("areas").objects()
        return (list.firstOrNull { it.optBoolean("default") }
            ?: list.lastOrNull { it.optDouble("adminLevel") in 6.0..8.0 })?.optString("name").orEmpty()
    }

    private fun townMentioned(o: JSONObject, q: String): Boolean {
        val town = town(o)
        return town.isNotBlank() && !town.equals("Augsburg", true) && q.contains(town, ignoreCase = true)
    }

    /** 0 = Augsburg city, 1 = AVV area (Schwaben), 2 = rest of Bavaria, 3 = elsewhere. */
    private fun regionRank(o: JSONObject): Int {
        val a = areas(o)
        val id = o.optString("id")
        return when {
            id.contains("de:09761:") || town(o) == "Augsburg" -> 0
            listOf("09772", "09771", "09773", "09779").any { id.contains("de:$it:") } || "Schwaben" in a -> 1
            id.contains("de:09") || "Bayern" in a -> 2
            else -> 3
        }
    }

    private fun parseMatch(o: JSONObject): Place {
        val type = when (o.optString("type")) {
            "STOP" -> LocationType.STOP
            "ADDRESS" -> LocationType.ADDRESS
            else -> if (o.optString("category").let { it.isNotBlank() && it != "none" }) LocationType.POI else LocationType.PLACE
        }
        val town = town(o)
        var name = o.optString("name")
        if (type == LocationType.ADDRESS) {
            val street = o.optString("street").ifBlank { name }
            val number = o.optString("houseNumber")
            name = if (number.isNotBlank() && !street.contains(number)) "$street $number" else street
        }
        val (locality, short) = splitName(name, town)
        val stopId = o.optString("id")
        return Place(
            id = if (type == LocationType.STOP) stationKey(stopId) else "geo:" + stopId,
            name = short,
            locality = locality,
            type = type,
            lat = o.optDouble("lat").takeIf { !it.isNaN() },
            lon = o.optDouble("lon").takeIf { !it.isNaN() },
            modes = o.optJSONArray("modes").strings().map { Mode.fromMotis(it) }.toSet(),
            apiId = if (type == LocationType.STOP) stopId else "",
        )
    }

    /** "Augsburg, Königsplatz" → ("Augsburg", "Königsplatz"). */
    private fun splitName(name: String, town: String): Pair<String, String> {
        val i = name.indexOf(", ")
        return when {
            i > 0 -> name.substring(0, i) to name.substring(i + 2)
            else -> town to name
        }
    }

    /** "de-DELFI_de:09761:101:51:C3" → "de:09761:101" (the whole station, all platforms). */
    fun stationKey(stopId: String): String {
        val raw = stopId.substringAfter('_', stopId)
        val parts = raw.split(':')
        return if (parts.size >= 3 && parts[0] == "de") parts.take(3).joinToString(":") else stopId
    }

    /** Stops inside a box, one entry per station. */
    suspend fun stopsInBox(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Place> {
        val arr = JSONArray(
            get(
                "/map/stops", listOf(
                    "min" to "%.5f,%.5f".format(Locale.ROOT, minLat, minLon),
                    "max" to "%.5f,%.5f".format(Locale.ROOT, maxLat, maxLon),
                ), cacheMs = 600_000
            )
        )
        return arr.objects()
            .filter { it.optString("stopId").startsWith("de-") }
            .groupBy { stationKey(it.optString("stopId")) }
            .map { (key, list) ->
                val first = list.first()
                val (locality, short) = splitName(first.optString("name"), "")
                Place(
                    id = key,
                    name = short,
                    locality = locality,
                    type = LocationType.STOP,
                    lat = list.map { it.optDouble("lat") }.average(),
                    lon = list.map { it.optDouble("lon") }.average(),
                    modes = list.flatMap { it.optJSONArray("modes").strings() }.map { Mode.fromMotis(it) }.toSet(),
                    apiId = first.optString("stopId"),
                )
            }
    }

    suspend fun nearbyStops(lat: Double, lon: Double, radiusMeters: Int): List<Place> {
        val dLat = radiusMeters / 111_000.0
        val dLon = radiusMeters / (111_000.0 * cos(Math.toRadians(lat)))
        val me = LatLon(lat, lon)
        return stopsInBox(lat - dLat, lon - dLon, lat + dLat, lon + dLon)
            .map { it.copy(distanceMeters = LocationProvider.distanceMeters(me, LatLon(it.lat!!, it.lon!!)).toInt()) }
            .filter { (it.distanceMeters ?: 0) <= radiusMeters }
            .sortedBy { it.distanceMeters }
    }

    /** Favourites saved by older versions have no platform id yet: look it up once by name. */
    suspend fun resolve(place: Place): Place {
        if (place.apiId.isNotBlank() || place.type != LocationType.STOP) return place
        val found = findLocations(place.fullName, stopsOnly = true)
        val match = found.firstOrNull { it.id == place.id } ?: found.firstOrNull()
            ?: throw IOException("Stop “${place.name}” not found")
        return place.copy(apiId = match.apiId, lat = place.lat ?: match.lat, lon = place.lon ?: match.lon)
    }

    // ----------------------------------------------------------- departures

    suspend fun departures(stop: Place, count: Int = 40, time: ZonedDateTime? = null): List<Departure> {
        val params = mutableListOf<Pair<String, Any>>("stopId" to stop.apiId, "n" to count)
        if (time != null) params += "time" to time.toInstant().toString()
        val data = JSONObject(get("/stoptimes", params))
        return data.optJSONArray("stopTimes").objects().mapNotNull { st ->
            val place = st.optJSONObject("place") ?: return@mapNotNull null
            val planned = place.optString("scheduledDeparture").toInstant()
            val actual = place.optString("departure").toInstant()
            if (planned == null && actual == null) return@mapNotNull null
            val realtime = st.optBoolean("realTime")
            val line = st.optString("routeShortName").ifBlank { st.optString("displayName") }
            val mode = Mode.fromMotis(st.optString("mode"), line)
            Departure(
                planned = planned,
                estimated = if (realtime) actual else null,
                line = line,
                lineName = line,
                mode = mode,
                destination = st.optString("headsign").ifBlank { st.optJSONObject("tripTo")?.optString("name").orEmpty() },
                platform = place.optString("track").ifBlank { place.optString("scheduledTrack") },
                realtime = realtime,
                cancelled = st.optBoolean("cancelled") || st.optBoolean("tripCancelled"),
                tripId = st.optString("tripId"),
                stopId = stop.id,
                origin = st.optJSONObject("tripFrom")?.optString("name").orEmpty(),
            )
        }.sortedBy { it.time }
    }

    /** The whole run of one vehicle: every stop from its origin to its last stop. */
    suspend fun run(dep: Departure): VehicleRun {
        val data = JSONObject(get("/trip", listOf("tripId" to dep.tripId)))
        val leg = data.optJSONArray("legs").objects().firstOrNull() ?: throw IOException("No route data for this departure")
        val points = buildList {
            leg.optJSONObject("from")?.let { add(it) }
            addAll(leg.optJSONArray("intermediateStops").objects())
            leg.optJSONObject("to")?.let { add(it) }
        }
        val stops = points.map { p ->
            RunStop(
                id = stationKey(p.optString("stopId")),
                name = p.optString("name"),
                platform = p.optString("track").ifBlank { p.optString("scheduledTrack") },
                arrPlanned = p.optString("scheduledArrival").toInstant(),
                arrEstimated = if (leg.optBoolean("realTime")) p.optString("arrival").toInstant() else null,
                depPlanned = p.optString("scheduledDeparture").toInstant(),
                depEstimated = if (leg.optBoolean("realTime")) p.optString("departure").toInstant() else null,
                coord = LatLon(p.optDouble("lat"), p.optDouble("lon")).takeIf { !it.lat.isNaN() },
            )
        }
        if (stops.isEmpty()) throw IOException("No stops found for this departure")
        val here = stops.indexOfFirst { it.id == dep.stopId && it.depPlanned == dep.planned }
            .takeIf { it >= 0 } ?: stops.indexOfFirst { it.id == dep.stopId }.coerceAtLeast(0)
        return VehicleRun(dep, stops, here, geometry(leg).ifEmpty { stops.mapNotNull { it.coord } })
    }

    // ---------------------------------------------------------------- trips

    suspend fun trips(
        from: Place,
        to: Place,
        time: ZonedDateTime,
        arriveBy: Boolean,
        excluded: Set<ModeGroup>,
    ): List<Journey> {
        val a = resolve(from)
        val b = resolve(to)
        val params = mutableListOf<Pair<String, Any>>(
            "fromPlace" to placeParam(a),
            "toPlace" to placeParam(b),
            "time" to time.toInstant().toString(),
            "arriveBy" to arriveBy,
            "numItineraries" to 5,
            "language" to "de",
        )
        if (excluded.isNotEmpty()) {
            params += "transitModes" to ModeGroup.entries.filter { it !in excluded }.flatMap { it.motis }.joinToString(",")
        }
        val data = JSONObject(get("/plan", params, cacheMs = 60_000))
        return data.optJSONArray("itineraries").objects().mapNotNull { it ->
            val legs = it.optJSONArray("legs").objects().map { l -> parseLeg(l, a, b) }
            if (legs.isEmpty()) null else Journey(legs, it.optInt("transfers"))
        }
    }

    private fun placeParam(p: Place): String =
        if (p.type == LocationType.STOP && p.apiId.isNotBlank()) p.apiId
        else "%.6f,%.6f".format(Locale.ROOT, p.lat ?: error("No position for ${p.name}"), p.lon!!)

    private fun parseLeg(leg: JSONObject, start: Place, end: Place): Leg {
        val line = leg.optString("routeShortName").ifBlank { leg.optString("displayName") }
        val mode = Mode.fromMotis(leg.optString("mode"), line)
        val realtime = leg.optBoolean("realTime")
        fun point(o: JSONObject?, departure: Boolean): LegPoint {
            val p = o ?: JSONObject()
            val name = when (p.optString("name")) {
                "START" -> start.fullName
                "END" -> end.fullName
                else -> p.optString("name")
            }
            val key = if (departure) "Departure" else "Arrival"
            return LegPoint(
                name = name,
                platform = p.optString("track").ifBlank { p.optString("scheduledTrack") },
                planned = p.optString("scheduled$key").toInstant(),
                estimated = if (realtime) p.optString(key.lowercase()).toInstant() else null,
                coord = LatLon(p.optDouble("lat"), p.optDouble("lon")).takeIf { !it.lat.isNaN() },
            )
        }
        val from = point(leg.optJSONObject("from"), true)
        val to = point(leg.optJSONObject("to"), false)
        return Leg(
            mode = mode,
            line = if (mode == Mode.WALK) "" else line,
            lineName = if (mode == Mode.WALK) "" else line,
            towards = leg.optString("headsign"),
            from = from,
            to = to,
            durationSeconds = leg.optInt("duration"),
            path = geometry(leg).ifEmpty { listOfNotNull(from.coord, to.coord) },
            intermediateStops = leg.optJSONArray("intermediateStops").objects().map {
                StopTime(it.optString("name"), (it.optString("departure").ifBlank { it.optString("arrival") }).toInstant())
            },
            cancelled = leg.optBoolean("cancelled"),
        )
    }

    // -------------------------------------------------------------- helpers

    private fun geometry(leg: JSONObject): List<LatLon> {
        val g = leg.optJSONObject("legGeometry") ?: return emptyList()
        return decodePolyline(g.optString("points"), g.optInt("precision", 6))
    }

    /** Google encoded polyline with the given precision (MOTIS uses 7). */
    fun decodePolyline(encoded: String, precision: Int): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            for (axis in 0..1) {
                var result = 0L
                var shift = 0
                var b: Int
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f).toLong() shl shift)
                    shift += 5
                } while (b >= 0x20 && index < encoded.length)
                val delta = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
                if (axis == 0) lat += delta else lon += delta
            }
            out += LatLon(lat / factor, lon / factor)
        }
        return out
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun String?.toInstant(): Instant? =
    if (this.isNullOrBlank()) null else runCatching { Instant.parse(this) }.getOrNull()
