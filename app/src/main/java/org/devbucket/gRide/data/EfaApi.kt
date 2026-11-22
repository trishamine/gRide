package org.devbucket.gRide.data

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Client for the EFA (Mentz) API of Bahnland Bayern (DEFAS): stop search, departures, vehicle runs,
 * trips and the AVV / swa traffic news. gRide identifies itself honestly and keeps requests low;
 * when this API refuses, [DataSource] falls back to Transitous.
 */
object EfaApi {
    private const val BASE = "https://bahnland-bayern.de/efa"
    private const val WGS84 = "WGS84[dd.ddddd]"
    private const val USER_AGENT = "gRide/1.2 (Android)"

    private suspend fun request(endpoint: String, params: List<Pair<String, Any>>, timeoutMs: Int = 30_000): JSONObject =
        withContext(Dispatchers.IO) {
            val query = (listOf("outputFormat" to "rapidJSON") + params)
                .joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v.toString(), "UTF-8") }
            val conn = URL("$BASE/$endpoint?$query").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode !in 200..299) throw HttpStatusException(conn.responseCode)
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }

    /** One small request to see whether Bahnland Bayern answers right now. */
    suspend fun ping() {
        request("XML_STOPFINDER_REQUEST", listOf("type_sf" to "stop", "name_sf" to "de:09761:101"), timeoutMs = 10_000)
    }

    // ---------------------------------------------------------------- stops

    /** Without a town in the query, Augsburg comes first, then the AVV area, then Bavaria. */
    suspend fun findLocations(query: String, stopsOnly: Boolean): List<Place> = coroutineScope {
        val q = query.trim()
        val mentionsTown = q.contains(",") || q.contains("augsburg", ignoreCase = true)
        val plain = async { stopFinder(q) }
        val local: Deferred<List<JSONObject>>? =
            if (mentionsTown) null else async { runCatching { stopFinder("Augsburg $q") }.getOrDefault(emptyList()) }
        val all = (local?.await().orEmpty() + plain.await())
            .filter { !stopsOnly || it.optString("type") == "stop" }
            .distinctBy { it.optString("id") }
        all.sortedWith(
            compareBy<JSONObject>({ if (townMentioned(it, q)) 0 else 1 }, { regionRank(it) }, { -it.optInt("matchQuality") })
        ).map(::parsePlace)
    }

    private suspend fun stopFinder(q: String): List<JSONObject> = request(
        "XML_STOPFINDER_REQUEST", listOf(
            "type_sf" to "any",
            "name_sf" to q,
            "locationServerActive" to 1,
            "coordOutputFormat" to WGS84,
        )
    ).optJSONArray("locations").objects()

    private fun townMentioned(o: JSONObject, q: String): Boolean {
        val town = o.optJSONObject("parent")?.optString("name").orEmpty()
        return town.isNotBlank() && !town.equals("Augsburg", true) && q.contains(town, ignoreCase = true)
    }

    private fun regionRank(o: JSONObject): Int {
        val key = o.optString("id") + " " + o.optJSONObject("parent")?.optString("id").orEmpty()
        return when {
            key.contains("09761") || o.optJSONObject("parent")?.optString("name") == "Augsburg" -> 0
            listOf("09772", "09771", "09773", "09779").any { key.contains(it) } -> 1
            key.contains("de:09") || key.contains("placeID:9") -> 2
            else -> 3
        }
    }

    private fun parsePlace(o: JSONObject): Place {
        val props = o.optJSONObject("properties")
        val parent = o.optJSONObject("parent")
        val coord = o.optJSONArray("coord")
        val type = when (o.optString("type")) {
            "stop", "platform" -> LocationType.STOP
            "street", "address", "singlehouse" -> LocationType.ADDRESS
            "poi" -> LocationType.POI
            else -> LocationType.PLACE
        }
        val locality = parent?.takeIf { it.optString("type") == "locality" }?.optString("name").orEmpty()
        val full = props?.optString("STOP_NAME_WITH_PLACE")?.takeIf { it.isNotBlank() } ?: o.optString("name")
        var name = o.optString("disassembledName").ifBlank { full }
        if (locality.isNotBlank() && name.startsWith("$locality, ")) name = name.removePrefix("$locality, ")
        val classes = o.optJSONArray("productClasses")
        return Place(
            id = o.optString("id"),
            name = name,
            locality = locality,
            type = type,
            lat = coord?.optDouble(0),
            lon = coord?.optDouble(1),
            modes = classes?.let { a -> (0 until a.length()).map { modeOfClass(a.getInt(it)) }.toSet() } ?: emptySet(),
            distanceMeters = props?.optInt("distance", -1)?.takeIf { it >= 0 },
        )
    }

    suspend fun nearbyStops(lat: Double, lon: Double, radiusMeters: Int): List<Place> {
        val data = request(
            "XML_COORD_REQUEST", listOf(
                "coord" to "%.6f:%.6f:%s".format(Locale.ROOT, lon, lat, WGS84),
                "coordOutputFormat" to WGS84,
                "inclFilter" to 1,
                "type_1" to "STOP",
                "radius_1" to radiusMeters,
            )
        )
        return data.optJSONArray("locations").objects()
            .filter { it.has("coord") }
            .map(::parsePlace)
            .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
    }

    // ----------------------------------------------------------- departures

    suspend fun departures(stop: Place, limit: Int = 40, time: ZonedDateTime? = null): List<Departure> {
        val params = mutableListOf<Pair<String, Any>>(
            "mode" to "direct",
            "type_dm" to "stop",
            "name_dm" to stop.id,
            "useRealtime" to 1,
            "limit" to limit,
        )
        if (time != null) params += timeParams(time) + ("itdDateTimeDepArr" to "dep")
        val data = request("XML_DM_REQUEST", params)
        return data.optJSONArray("stopEvents").objects().mapNotNull { ev ->
            val planned = ev.optString("departureTimePlanned").toInstant()
            val est = ev.optString("departureTimeEstimated").toInstant()
            if (planned == null && est == null) return@mapNotNull null
            val trans = ev.optJSONObject("transportation") ?: JSONObject()
            val product = trans.optJSONObject("product")
            val props = ev.optJSONObject("location")?.optJSONObject("properties")
            val line = trans.optString("number").ifBlank { trans.optString("name") }
            Departure(
                planned = planned,
                estimated = est,
                line = line,
                lineName = line,
                mode = modeOfClass(product?.optInt("class", -1), line),
                destination = trans.optJSONObject("destination")?.optString("name").orEmpty(),
                platform = props?.optString("platformName")?.ifBlank { null } ?: props?.optString("platform").orEmpty(),
                realtime = ev.optBoolean("isRealtimeControlled"),
                cancelled = ev.optJSONArray("realtimeStatus")?.toString()?.contains("CANCELLED") == true,
                tripId = trans.optString("id"),
                stopId = stop.id,
                origin = trans.optJSONObject("origin")?.optString("name").orEmpty(),
                source = Source.BAHNLAND,
                tripCode = trans.optJSONObject("properties")?.optString("tripCode").orEmpty(),
            )
        }.sortedBy { it.time }
    }

    /** The whole run of one vehicle: every stop from its origin to its last stop. */
    suspend fun run(dep: Departure): VehicleRun {
        val local = (dep.planned ?: dep.time).atZone(TransitZone)
        val data = request(
            "XML_STOPSEQCOORD_REQUEST", listOf(
                "line" to dep.tripId,
                "stop" to dep.stopId,
                "tripCode" to dep.tripCode,
                "date" to local.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "time" to local.format(DateTimeFormatter.ofPattern("HHmm")),
                "tStOTType" to "all",
                "useRealtime" to 1,
                "coordOutputFormat" to WGS84,
            )
        )
        val leg = data.optJSONObject("leg") ?: throw IOException("No route data for this departure")
        val stops = leg.optJSONArray("stopSequence").objects().map { s ->
            val props = s.optJSONObject("properties")
            val coord = s.optJSONArray("coord")
            RunStop(
                id = s.optJSONObject("parent")?.optString("id").orEmpty().ifBlank { s.optString("id") },
                name = stopName(s),
                platform = props?.optString("platformName").orEmpty().ifBlank {
                    if (s.optString("type") == "platform") s.optString("disassembledName") else ""
                },
                arrPlanned = s.optString("arrivalTimePlanned").toInstant(),
                arrEstimated = s.optString("arrivalTimeEstimated").toInstant(),
                depPlanned = s.optString("departureTimePlanned").toInstant(),
                depEstimated = s.optString("departureTimeEstimated").toInstant(),
                coord = coord?.let { LatLon(it.getDouble(0), it.getDouble(1)) },
            )
        }
        if (stops.isEmpty()) throw IOException("No stops found for this departure")
        val path = leg.optJSONArray("coords")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONArray(i)?.let { LatLon(it.getDouble(0), it.getDouble(1)) } }
        }.orEmpty()
        val here = stops.indexOfFirst { it.id == dep.stopId }.takeIf { it >= 0 }
            ?: stops.indexOfFirst { it.depPlanned == dep.planned }.coerceAtLeast(0)
        return VehicleRun(dep, stops, here, path.ifEmpty { stops.mapNotNull { it.coord } })
    }

    // ---------------------------------------------------------------- trips

    suspend fun trips(from: Place, to: Place, time: ZonedDateTime, arriveBy: Boolean, excluded: Set<ModeGroup>): List<Journey> {
        val params = mutableListOf<Pair<String, Any>>()
        params += placeParams("origin", from) + placeParams("destination", to)
        params += timeParams(time)
        params += listOf(
            "itdTripDateTimeDepArr" to if (arriveBy) "arr" else "dep",
            "calcNumberOfTrips" to 6,
            "useRealtime" to 1,
            "coordOutputFormat" to WGS84,
            "routeType" to "LEASTTIME",
        )
        if (excluded.isNotEmpty()) {
            params += "excludedMeans" to "checkbox"
            excluded.flatMap { classesOf(it) }.forEach { params += "exclMOT_$it" to 1 }
        }
        val data = request("XML_TRIP_REQUEST2", params)
        return data.optJSONArray("journeys").objects().mapNotNull { j ->
            val legs = j.optJSONArray("legs").objects().map(::parseLeg)
            if (legs.isEmpty()) null else Journey(legs, j.optInt("interchanges"))
        }
    }

    private fun classesOf(g: ModeGroup): List<Int> = when (g) {
        ModeGroup.TRAIN -> listOf(0, 1, 2, 13, 14, 15, 16, 18)
        ModeGroup.TRAM -> listOf(3, 4)
        ModeGroup.BUS -> listOf(5, 6, 7, 17, 19)
        ModeGroup.ON_DEMAND -> listOf(10)
    }

    private fun parseLeg(leg: JSONObject): Leg {
        val trans = leg.optJSONObject("transportation") ?: JSONObject()
        val product = trans.optJSONObject("product")
        val line = trans.optString("number").ifBlank { trans.optString("name") }
        val mode = modeOfClass(product?.optInt("class", -1), line)
        val o = leg.optJSONObject("origin") ?: JSONObject()
        val d = leg.optJSONObject("destination") ?: JSONObject()
        val path = leg.optJSONArray("coords")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONArray(i)?.let { LatLon(it.getDouble(0), it.getDouble(1)) } }
        }.orEmpty()
        val seq = leg.optJSONArray("stopSequence").objects()
        val intermediate = if (seq.size > 2) seq.subList(1, seq.size - 1).map {
            StopTime(stopName(it), it.optString("departureTimeEstimated").ifBlank { it.optString("departureTimePlanned") }.toInstant())
        } else emptyList()
        val from = legPoint(o, departure = true)
        val to = legPoint(d, departure = false)
        return Leg(
            mode = mode,
            line = if (mode == Mode.WALK) "" else line,
            lineName = if (mode == Mode.WALK) "" else line,
            towards = trans.optJSONObject("destination")?.optString("name").orEmpty(),
            from = from,
            to = to,
            durationSeconds = leg.optInt("duration"),
            path = path.ifEmpty { listOfNotNull(from.coord, to.coord) },
            intermediateStops = intermediate,
            cancelled = leg.optJSONArray("realtimeStatus")?.toString()?.contains("CANCELLED") == true,
        )
    }

    private fun stopName(o: JSONObject): String =
        o.optJSONObject("parent")?.optString("name")?.ifBlank { null } ?: o.optString("name")

    private fun legPoint(o: JSONObject, departure: Boolean): LegPoint {
        val prefix = if (departure) "departureTime" else "arrivalTime"
        val coord = o.optJSONArray("coord")
        return LegPoint(
            name = stopName(o),
            platform = if (o.optString("type") == "platform") o.optString("disassembledName") else "",
            planned = o.optString("${prefix}Planned").toInstant(),
            estimated = o.optString("${prefix}Estimated").toInstant(),
            coord = coord?.let { LatLon(it.getDouble(0), it.getDouble(1)) },
        )
    }

    /** Stops by id; everything else (addresses, places from Transitous, current location) by coordinates. */
    private fun placeParams(role: String, p: Place): List<Pair<String, Any>> {
        val byCoord = p.type == LocationType.COORD || p.id.startsWith("geo:") || (p.type != LocationType.STOP && p.hasCoord && !p.id.contains("ID:"))
        return if (byCoord && p.lat != null && p.lon != null) listOf(
            "type_$role" to "coord",
            "name_$role" to "%.6f:%.6f:%s".format(Locale.ROOT, p.lon, p.lat, WGS84),
        ) else listOf("type_$role" to "any", "name_$role" to p.id)
    }

    private fun timeParams(t: ZonedDateTime): List<Pair<String, Any>> {
        val local = t.withZoneSameInstant(TransitZone)
        return listOf(
            "itdDate" to local.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            "itdTime" to local.format(DateTimeFormatter.ofPattern("HHmm")),
        )
    }

    // ---------------------------------------------------------- disruptions

    /** Current news of AVV (regional) and AVG / swa (Augsburg city buses and trams). */
    suspend fun disruptions(): List<Disruption> = coroutineScope {
        val results = listOf("AVV", "AVG").map { provider -> async { runCatching { disruptionsOf(provider) } } }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }.sortedByDescending { it.validFrom }
    }

    private suspend fun disruptionsOf(provider: String): List<Disruption> {
        val data = request(
            "XML_ADDINFO_REQUEST", listOf(
                "filterPublicationStatus" to "current",
                "filterProviderCode" to provider,
            ), timeoutMs = 45_000
        )
        return data.optJSONObject("infos")?.optJSONArray("current").objects().mapNotNull { info ->
            val link = info.optJSONArray("infoLinks").objects().firstOrNull() ?: return@mapNotNull null
            val title = link.optString("subtitle").ifBlank { stripHtml(link.optString("urlText")) }
                .ifBlank { stripHtml(link.optString("content")).take(90) }
            val affected = info.optJSONObject("affected")
            val lines = affected?.optJSONArray("lines").objects()
                .map { val n = it.optString("number").ifBlank { it.optString("name") }; LineRef(n, modeOfClass(it.optJSONObject("product")?.optInt("class", -1), n)) }
                .distinctBy { it.number }
            val stops = affected?.optJSONArray("stops").objects().map { it.optString("id") }.filter { it.isNotBlank() }.distinct()
            val validity = info.optJSONObject("timestamps")?.optJSONArray("validity").objects().firstOrNull()
            Disruption(
                id = info.optString("id"),
                kind = info.optString("type"),
                provider = provider,
                stopIds = stops,
                title = title,
                html = link.optString("content"),
                lines = lines,
                validFrom = validity?.optString("from").toInstant(),
                validTo = validity?.optString("to").toInstant(),
                attachments = link.optJSONArray("attachments").objects().map {
                    Attachment(it.optString("linkText").ifBlank { "PDF" }, it.optString("path"))
                },
            )
        }
    }

    private fun stripHtml(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&nbsp;", " ").trim()

    /** EFA product classes → our modes (buses with 3-digit numbers are regional buses). */
    private fun modeOfClass(cls: Int?, line: String = ""): Mode = when (cls) {
        0, 2, 13, 14, 15, 16, 18 -> Mode.TRAIN
        1 -> Mode.SUBURBAN
        3, 4 -> Mode.TRAM
        5, 7, 17, 19 -> if ((line.filter { it.isDigit() }.toIntOrNull() ?: 0) >= 100) Mode.REGIONAL_BUS else Mode.CITY_BUS
        6 -> Mode.REGIONAL_BUS
        10 -> Mode.ON_DEMAND
        99, 100, 107 -> Mode.WALK
        else -> Mode.OTHER
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun String?.toInstant(): Instant? =
    if (this.isNullOrBlank()) null else runCatching { Instant.parse(this) }.getOrNull()
