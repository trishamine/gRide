package org.devbucket.gRide.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime

/** Which API to use: automatic (Bahnland Bayern first, Transitous as fallback) or one forced. */
enum class ApiChoice { AUTO, BAHNLAND, TRANSITOUS }

/**
 * Picks the API for every request. In AUTO mode Bahnland Bayern is used first; when it fails (for
 * example HTTP 429) it is marked unavailable and Transitous takes over until [probe] succeeds again.
 */
object DataSource {
    @Volatile var choice: ApiChoice = ApiChoice.AUTO

    private val _bahnlandAvailable = MutableStateFlow<Boolean?>(null)  // null = not checked yet
    val bahnlandAvailable: StateFlow<Boolean?> = _bahnlandAvailable.asStateFlow()

    /** The last error from Bahnland Bayern (shown in the traffic news). */
    @Volatile var lastBahnlandError: Throwable? = null
        private set

    /** One request to Bahnland Bayern (done at app start and on "Try again"). */
    suspend fun probe(): Boolean {
        val ok = try {
            EfaApi.ping(); true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastBahnlandError = e; false
        }
        _bahnlandAvailable.value = ok
        if (ok) lastBahnlandError = null
        return ok
    }

    private suspend fun <T> pick(bahnland: suspend () -> T, transitous: suspend () -> T): T = when (choice) {
        ApiChoice.BAHNLAND -> bahnland()
        ApiChoice.TRANSITOUS -> transitous()
        ApiChoice.AUTO ->
            if (_bahnlandAvailable.value == false) transitous()
            else try {
                bahnland()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastBahnlandError = e
                _bahnlandAvailable.value = false
                transitous()
            }
    }

    suspend fun findLocations(query: String, stopsOnly: Boolean): List<Place> =
        pick({ EfaApi.findLocations(query, stopsOnly) }, { TransitApi.findLocations(query, stopsOnly) })

    suspend fun nearbyStops(lat: Double, lon: Double, radiusMeters: Int): List<Place> =
        pick({ EfaApi.nearbyStops(lat, lon, radiusMeters) }, { TransitApi.nearbyStops(lat, lon, radiusMeters) })

    /** Map stops come from Transitous, which can list all stops inside a box cheaply. */
    suspend fun stopsInBox(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Place> =
        TransitApi.stopsInBox(minLat, minLon, maxLat, maxLon)

    suspend fun departures(stop: Place, limit: Int, time: ZonedDateTime?): List<Departure> =
        pick({ EfaApi.departures(stop, limit, time) }, { TransitApi.departures(TransitApi.resolve(stop), limit, time) })

    suspend fun trips(from: Place, to: Place, time: ZonedDateTime, arriveBy: Boolean, excluded: Set<ModeGroup>): List<Journey> =
        pick({ EfaApi.trips(from, to, time, arriveBy, excluded) }, { TransitApi.trips(from, to, time, arriveBy, excluded) })

    /** A vehicle's run comes from the API that listed the departure. */
    suspend fun run(dep: Departure): VehicleRun =
        if (dep.source == Source.BAHNLAND) EfaApi.run(dep) else TransitApi.run(dep)

    /** Traffic news only exist at Bahnland Bayern. */
    suspend fun disruptions(): List<Disruption> {
        if (choice == ApiChoice.TRANSITOUS) throw NewsUnavailableException()
        return try {
            EfaApi.disruptions().also { _bahnlandAvailable.value = true; lastBahnlandError = null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastBahnlandError = e
            _bahnlandAvailable.value = false
            throw e
        }
    }
}

/** Traffic news need Bahnland Bayern, but Transitous is forced in the secret settings. */
class NewsUnavailableException : java.io.IOException("Traffic news need Bahnland Bayern")
