package org.devbucket.gRide.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.devbucket.gRide.data.Departure
import org.devbucket.gRide.data.VehicleRun
import org.devbucket.gRide.data.ApiChoice
import org.devbucket.gRide.data.DataSource
import org.devbucket.gRide.data.Disruption
import org.devbucket.gRide.data.HttpStatusException
import org.devbucket.gRide.data.NewsUnavailableException
import org.devbucket.gRide.data.RateLimitedException
import org.devbucket.gRide.R
import org.devbucket.gRide.data.AppRelease
import org.devbucket.gRide.data.Updater
import java.io.File
import kotlin.math.cos
import kotlin.math.floor
import org.devbucket.gRide.data.Journey
import org.devbucket.gRide.data.LatLon
import org.devbucket.gRide.data.LocationProvider
import org.devbucket.gRide.data.LocationType
import org.devbucket.gRide.data.ModeGroup
import org.devbucket.gRide.data.Place
import org.devbucket.gRide.data.Store
import org.devbucket.gRide.data.TransitZone
import org.devbucket.gRide.data.TripEntry
import java.time.ZonedDateTime

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Error(val message: String) : Load<Nothing>
}

enum class Tab { START, DEPARTURES, NEWS, SETTINGS }

enum class SearchTarget(val stopsOnly: Boolean, val title: String) {
    FROM(false, "Start"),
    TO(false, "Destination"),
    STOP(true, "Stop"),
    FOLLOW(true, "Stop to follow"),
}

sealed interface Overlay {
    /** [closesTrip]: going back from this search also closes the trip planner (opened by "Plan a trip"). */
    data class Search(val target: SearchTarget, val closesTrip: Boolean = false) : Overlay
    data class Run(val departure: Departure) : Overlay
    data object Trip : Overlay
    data class JourneyDetail(val index: Int) : Overlay
    data class StopBoard(val stop: Place) : Overlay
    data object Secret : Overlay
    data object Follow : Overlay
}

enum class TimeMode(@androidx.annotation.StringRes val label: Int) {
    NOW(R.string.leave_now), DEPART(R.string.depart), ARRIVE(R.string.arrive)
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    /** Downloaded, but Android still needs "Install unknown apps" for gRide. */
    data class NeedsPermission(val release: AppRelease, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** One-shot camera instruction for the map. */
data class CameraRequest(val points: List<LatLon>, val zoom: Double? = null, val seq: Long = System.nanoTime())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    // ------------------------------------------------------------ navigation
    var tab by mutableStateOf(Tab.START)
        private set
    val overlays = mutableStateListOf<Overlay>()
    val overlay: Overlay? get() = overlays.lastOrNull()

    /** Stop shown in the bottom sheet on the Start tab (tapped on the map or in a list). */
    var sheetStop by mutableStateOf<Place?>(null)
        private set

    val canGoBack: Boolean get() = overlays.isNotEmpty() || sheetStop != null || tab != Tab.START

    val mapVisible: Boolean
        get() = if (!store.settings.value.setupDone) false else when (overlay) {
            null -> tab == Tab.START
            is Overlay.Trip, is Overlay.JourneyDetail -> true
            else -> false
        }

    fun selectTab(t: Tab) {
        overlays.clear()
        tab = t
        if (t == Tab.NEWS && news !is Load.Ok) loadNews()
        if (t == Tab.START) refreshBoardLoop()
    }

    fun push(o: Overlay) {
        overlays.add(o)
    }

    fun back() {
        when {
            overlays.isNotEmpty() -> {
                val removed = overlays.removeAt(overlays.lastIndex)
                if (removed is Overlay.Search && removed.closesTrip) {
                    val missing = if (removed.target == SearchTarget.TO) tripTo == null else tripFrom == null
                    if (missing) {
                        overlays.removeAll { it is Overlay.Trip }
                        journeys = Load.Idle
                        mapJourney = null
                    }
                }
                if (removed is Overlay.Run) runJob?.cancel()
                if (removed is Overlay.StopBoard) stopBoardLoop()
                if (removed is Overlay.JourneyDetail) showJourneysOnMap()
                if (removed is Overlay.Trip) mapJourney = null
            }
            sheetStop != null -> closeStopSheet()
            tab != Tab.START -> selectTab(Tab.START)
        }
    }

    /** Auto-refresh only runs while the app is visible. */
    var inForeground = true
        private set

    fun setForeground(visible: Boolean) {
        inForeground = visible
        if (visible) {
            if (store.settings.value.setupDone) checkForUpdates(manual = false)
            (update as? UpdateState.NeedsPermission)?.let { if (Updater.canInstall(getApplication())) installUpdate(it.release, it.file) }
            boardStop?.let { if (sheetStop != null || overlay is Overlay.StopBoard) startBoard(it, showLoading = false) }
            (overlay as? Overlay.Run)?.let { loadRun(it.departure) }
        } else {
            boardJob?.cancel()
            runJob?.cancel()
        }
    }

    // ----------------------------------------------------------------- map
    var userLocation by mutableStateOf<LatLon?>(null)
        private set
    var mapStops by mutableStateOf<Map<String, Place>>(emptyMap())
        private set
    var mapJourney by mutableStateOf<Journey?>(null)
        private set
    var camera by mutableStateOf<CameraRequest?>(CameraRequest(listOf(LatLon(48.3665, 10.8986)), 14.0))
        private set
    private var mapJob: Job? = null
    private val loadedCells = HashSet<Pair<Int, Int>>()

    /**
     * Stops for the map are loaded per grid cell (about 1 x 1 km) and every cell only once,
     * so panning back and forth does not send new requests.
     */
    fun onMapMoved(center: LatLon, radiusMeters: Int, zoom: Double) {
        if (zoom < 15.0) return
        mapJob?.cancel()
        mapJob = viewModelScope.launch {
            delay(300)
            val r = radiusMeters.coerceIn(200, 1500)
            val dLat = r / 111_000.0
            val dLon = r / (111_000.0 * cos(Math.toRadians(center.lat)))
            val cells = buildList {
                for (y in cell(center.lat - dLat, CELL_LAT)..cell(center.lat + dLat, CELL_LAT))
                    for (x in cell(center.lon - dLon, CELL_LON)..cell(center.lon + dLon, CELL_LON)) add(y to x)
            }.filter { it !in loadedCells }.take(6)
            for ((y, x) in cells) {
                val found = try {
                    DataSource.stopsInBox(y * CELL_LAT, x * CELL_LON, (y + 1) * CELL_LAT, (x + 1) * CELL_LON)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    break
                }
                loadedCells += y to x
                mapStops = mapStops + found.associateBy { it.id }
            }
        }
    }

    private fun cell(v: Double, size: Double) = floor(v / size).toInt()

    fun locateMe(moveCamera: Boolean = true) {
        viewModelScope.launch {
            val loc = LocationProvider.current(getApplication()) ?: return@launch
            userLocation = loc
            if (moveCamera) camera = CameraRequest(listOf(loc), 16.5)
        }
    }

    fun focus(place: Place) {
        if (place.lat != null && place.lon != null) camera = CameraRequest(listOf(LatLon(place.lat, place.lon)), 17.0)
    }

    // -------------------------------------------------------- stop / board
    var board by mutableStateOf<Load<List<Departure>>>(Load.Idle)
        private set
    var boardStop by mutableStateOf<Place?>(null)
        private set
    var boardTime by mutableStateOf<ZonedDateTime?>(null)
        private set
    var boardUpdated by mutableStateOf<ZonedDateTime?>(null)
        private set
    private var boardJob: Job? = null

    fun openStopSheet(place: Place) {
        overlays.clear()
        tab = Tab.START
        sheetStop = place
        store.addStopToHistory(place)
        focus(place)
        startBoard(place)
    }

    fun closeStopSheet() {
        sheetStop = null
        stopBoardLoop()
    }

    fun openStopBoard(place: Place) {
        store.addStopToHistory(place)
        overlays.removeAll { it is Overlay.StopBoard || it is Overlay.Search }
        push(Overlay.StopBoard(place))
        startBoard(place)
    }

    fun changeBoardTime(time: ZonedDateTime?) {
        boardTime = time
        boardStop?.let { startBoard(it) }
    }

    fun refreshBoard() {
        boardStop?.let { startBoard(it, showLoading = false) }
    }

    private fun startBoard(place: Place, showLoading: Boolean = true) {
        if (boardStop?.id != place.id) {
            boardTime = null
            board = Load.Idle
        }
        boardStop = place
        boardJob?.cancel()
        boardJob = viewModelScope.launch {
            if (showLoading && board !is Load.Ok) board = Load.Loading
            while (isActive) {
                try {
                    val list = DataSource.departures(place, 40, boardTime)
                    board = Load.Ok(list)
                    boardUpdated = ZonedDateTime.now(TransitZone)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (board !is Load.Ok) board = Load.Error(errorText(e, R.string.err_departures))
                }
                if (!store.settings.value.autoRefresh || boardTime != null || !inForeground) break
                delay(REFRESH_MS)
            }
        }
    }

    private fun refreshBoardLoop() {
        sheetStop?.let { startBoard(it, showLoading = false) }
    }

    private fun stopBoardLoop() {
        boardJob?.cancel()
    }

    // ------------------------------------------------------------- nearby
    var nearby by mutableStateOf<Load<List<Place>>>(Load.Idle)
        private set

    fun loadNearby() {
        viewModelScope.launch {
            nearby = Load.Loading
            val loc = LocationProvider.current(getApplication())
            if (loc == null) {
                nearby = Load.Error(str(R.string.err_location))
                return@launch
            }
            userLocation = loc
            nearby = try {
                Load.Ok(DataSource.nearbyStops(loc.lat, loc.lon, 1200))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(errorText(e, R.string.err_nearby))
            }
        }
    }

    fun distanceTo(place: Place): Float? {
        val me = userLocation ?: return null
        if (place.lat == null || place.lon == null) return null
        return LocationProvider.distanceMeters(me, LatLon(place.lat, place.lon))
    }

    // ------------------------------------------------------------- search
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<Load<List<Place>>>(Load.Idle)
        private set
    private var searchJob: Job? = null

    fun openSearch(target: SearchTarget, closesTrip: Boolean = false) {
        searchQuery = ""
        searchResults = Load.Idle
        push(Overlay.Search(target, closesTrip))
    }

    fun onSearchQuery(q: String, target: SearchTarget) {
        searchQuery = q
        searchJob?.cancel()
        if (q.trim().length < 3) {
            searchResults = Load.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // wait until typing pauses
            searchResults = Load.Loading
            searchResults = try {
                Load.Ok(DataSource.findLocations(q.trim(), target.stopsOnly))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(errorText(e, R.string.err_search))
            }
        }
    }

    fun choose(place: Place, target: SearchTarget) {
        overlays.removeAll { it is Overlay.Search }
        when (target) {
            SearchTarget.STOP -> openStopBoard(place)
            SearchTarget.FOLLOW -> if (!store.isFollowing(place)) store.toggleFollowStop(place)
            SearchTarget.FROM -> {
                tripFrom = place
                afterTripPointChosen()
            }
            SearchTarget.TO -> {
                tripTo = place
                afterTripPointChosen()
            }
        }
    }

    fun chooseCurrentLocation(target: SearchTarget) {
        viewModelScope.launch {
            val loc = LocationProvider.current(getApplication())
            if (loc == null) {
                searchResults = Load.Error(str(R.string.err_location))
                return@launch
            }
            userLocation = loc
            if (target.stopsOnly) {
                val stop = runCatching { DataSource.nearbyStops(loc.lat, loc.lon, 1000).firstOrNull() }.getOrNull()
                if (stop != null) choose(stop, target) else searchResults = Load.Error(str(R.string.err_no_stop_nearby))
            } else {
                choose(Place.currentLocation(loc.lat, loc.lon, str(R.string.current_location)), target)
            }
        }
    }

    // --------------------------------------------------------------- trips
    var tripFrom by mutableStateOf<Place?>(null)
    var tripTo by mutableStateOf<Place?>(null)
    var timeMode by mutableStateOf(TimeMode.NOW)
        private set
    var tripTime by mutableStateOf<ZonedDateTime>(ZonedDateTime.now(TransitZone))
        private set
    var excludedModes by mutableStateOf<Set<ModeGroup>>(emptySet())
        private set
    var journeys by mutableStateOf<Load<List<Journey>>>(Load.Idle)
        private set
    private var tripJob: Job? = null

    /** "Plan a trip": start at the current location (if allowed) and ask for the destination. */
    /** A new trip always starts at "Leave now", not at a time picked for an earlier search. */
    private fun resetTripTime() {
        timeMode = TimeMode.NOW
        tripTime = ZonedDateTime.now(TransitZone)
    }

    fun startTripPlanning() {
        resetTripTime()
        tripTo = null
        journeys = Load.Idle
        mapJourney = null
        // Always start from where the user is right now.
        tripFrom = userLocation?.let { Place.currentLocation(it.lat, it.lon, str(R.string.current_location)) }
        if (LocationProvider.hasPermission(getApplication())) {
            viewModelScope.launch {
                LocationProvider.current(getApplication())?.let {
                    userLocation = it
                    if (tripFrom == null || tripFrom?.type == LocationType.COORD) tripFrom = Place.currentLocation(it.lat, it.lon, str(R.string.current_location))
                }
            }
        }
        overlays.clear()
        push(Overlay.Trip)
        openSearch(SearchTarget.TO, closesTrip = true)
    }

    fun planFrom(place: Place, asDestination: Boolean = false) {
        if (overlays.none { it is Overlay.Trip }) resetTripTime()
        if (asDestination) tripTo = place else tripFrom = place
        overlays.removeAll { it !is Overlay.Trip }
        if (overlays.none { it is Overlay.Trip }) push(Overlay.Trip)
        if (tripFrom != null && tripTo != null) planTrip() else openSearch(if (asDestination) SearchTarget.FROM else SearchTarget.TO)
    }

    fun planSaved(entry: TripEntry, swap: Boolean = false) {
        tripFrom = if (swap) entry.to else entry.from
        tripTo = if (swap) entry.from else entry.to
        resetTripTime()
        overlays.clear()
        push(Overlay.Trip)
        planTrip()
    }

    private fun afterTripPointChosen() {
        if (overlays.none { it is Overlay.Trip }) push(Overlay.Trip)
        if (tripFrom != null && tripTo != null) planTrip()
    }

    fun swapTrip() {
        val f = tripFrom
        tripFrom = tripTo
        tripTo = f
        if (tripFrom != null && tripTo != null) planTrip()
    }

    fun setTripTime(mode: TimeMode, time: ZonedDateTime) {
        timeMode = mode
        tripTime = time
        planTrip()
    }

    fun toggleMode(group: ModeGroup) {
        excludedModes = if (group in excludedModes) excludedModes - group else excludedModes + group
        planTrip()
    }

    fun shiftTrips(later: Boolean) {
        val list = (journeys as? Load.Ok)?.data ?: return
        val ref = if (later) list.lastOrNull()?.departure else list.firstOrNull()?.departure
        ref ?: return
        val t = ZonedDateTime.ofInstant(ref, TransitZone).plusMinutes(if (later) 1 else -45)
        timeMode = TimeMode.DEPART
        tripTime = t
        planTrip()
    }

    fun planTrip() {
        val from = tripFrom ?: return
        val to = tripTo ?: return
        store.addTrip(from, to)
        val time = if (timeMode == TimeMode.NOW) ZonedDateTime.now(TransitZone) else tripTime
        tripJob?.cancel()
        tripJob = viewModelScope.launch {
            journeys = Load.Loading
            journeys = try {
                Load.Ok(DataSource.trips(from, to, time, timeMode == TimeMode.ARRIVE, excludedModes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(errorText(e, R.string.err_connections))
            }
            showJourneysOnMap()
        }
    }

    fun openJourney(index: Int) {
        val j = (journeys as? Load.Ok)?.data?.getOrNull(index) ?: return
        mapJourney = j
        camera = CameraRequest(j.path)
        push(Overlay.JourneyDetail(index))
    }

    fun journey(index: Int): Journey? = (journeys as? Load.Ok)?.data?.getOrNull(index)

    private fun showJourneysOnMap() {
        val first = (journeys as? Load.Ok)?.data?.firstOrNull()
        mapJourney = first
        if (first != null) camera = CameraRequest(first.path)
    }

    fun previewJourney(index: Int) {
        val j = journey(index) ?: return
        mapJourney = j
        camera = CameraRequest(j.path)
    }

    // ----------------------------------------------------------- vehicle run
    var run by mutableStateOf<Load<VehicleRun>>(Load.Idle)
        private set
    private var runJob: Job? = null

    fun openRun(dep: Departure) {
        run = Load.Loading
        push(Overlay.Run(dep))
        loadRun(dep)
    }

    fun loadRun(dep: Departure) {
        runJob?.cancel()
        runJob = viewModelScope.launch {
            while (isActive) {
                try {
                    run = Load.Ok(DataSource.run(dep))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (run !is Load.Ok) run = Load.Error(errorText(e, R.string.err_run))
                }
                if (!store.settings.value.autoRefresh || !inForeground) break
                delay(REFRESH_MS)
            }
        }
    }

    // -------------------------------------------------------------- updates
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var showUpdateDialog by mutableStateOf(false)
        private set
    val installedVersion: String = Updater.installedVersion(app)
    private val updatePrefs = app.getSharedPreferences("gride_updates", android.content.Context.MODE_PRIVATE)

    private var updateCheckRunning = false

    /**
     * Runs quietly in the background every time the app is opened or comes back to the screen
     * (at most once per 2 minutes, to stay well within GitHub's limit of 60 checks per hour).
     * [manual] checks always run and always report the result.
     */
    fun checkForUpdates(manual: Boolean) {
        if (updateCheckRunning || update is UpdateState.Downloading || update is UpdateState.NeedsPermission) return
        val now = System.currentTimeMillis()
        if (!manual && now - updatePrefs.getLong("lastCheck", 0) < 2 * 60_000L) return
        updateCheckRunning = true
        viewModelScope.launch {
            if (manual) update = UpdateState.Checking
            try {
                val release = Updater.latest()
                updatePrefs.edit().putLong("lastCheck", now).apply()
                if (release != null && Updater.isNewer(release.version, installedVersion)) {
                    update = UpdateState.Available(release)
                    if (manual || release.version != updatePrefs.getString("skipped", null)) showUpdateDialog = true
                } else {
                    update = UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update = if (manual) UpdateState.Error(errorText(e, R.string.err_update_check)) else UpdateState.Idle
            } finally {
                updateCheckRunning = false
            }
        }
    }

    fun startUpdate() {
        val release = when (val u = update) {
            is UpdateState.Available -> u.release
            is UpdateState.NeedsPermission -> return installUpdate(u.release, u.file)
            else -> return
        }
        viewModelScope.launch {
            update = UpdateState.Downloading(release, 0f)
            try {
                val file = Updater.download(getApplication(), release) { p -> update = UpdateState.Downloading(release, p) }
                installUpdate(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update = UpdateState.Error(errorText(e, R.string.err_download))
            }
        }
    }

    private fun installUpdate(release: AppRelease, file: File) {
        if (!Updater.canInstall(getApplication())) {
            update = UpdateState.NeedsPermission(release, file)
            Updater.openInstallPermission(getApplication())
            return
        }
        update = UpdateState.Available(release)
        showUpdateDialog = false
        Updater.install(getApplication(), file)
    }

    fun skipUpdate() {
        (update as? UpdateState.Available)?.let { updatePrefs.edit().putString("skipped", it.release.version).apply() }
        showUpdateDialog = false
    }

    fun dismissUpdate() {
        showUpdateDialog = false
        if (update is UpdateState.Error) update = UpdateState.Idle
    }

    fun openUpdateDialog() {
        if (update is UpdateState.Available || update is UpdateState.NeedsPermission) showUpdateDialog = true
    }

    // ---------------------------------------------------------------- setup
    fun finishSetup() {
        store.updateSettings { it.copy(setupDone = true) }
        overlays.clear()
        tab = Tab.START
        if (LocationProvider.hasPermission(getApplication())) locateMe(moveCamera = true)
        checkForUpdates(manual = false)
    }

    // ------------------------------------------------------------------ news
    var news by mutableStateOf<Load<List<Disruption>>>(Load.Idle)
        private set

    /** Loads AVV / swa news from Bahnland Bayern. On failure the error keeps its HTTP code (e.g. 429). */
    fun loadNews() {
        viewModelScope.launch {
            if (news !is Load.Ok) news = Load.Loading
            news = try {
                Load.Ok(DataSource.disruptions())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(newsError(e))
            }
        }
    }

    /** "Try again" in the traffic news: ask Bahnland Bayern once more, then reload the news. */
    fun retryBahnland() {
        viewModelScope.launch {
            news = Load.Loading
            if (DataSource.probe()) loadNews() else news = Load.Error(newsError(DataSource.lastBahnlandError))
        }
    }

    private fun newsError(e: Throwable?): String = when (e) {
        is HttpStatusException -> str(R.string.server_error, e.code)
        is NewsUnavailableException -> str(R.string.news_need_bahnland)
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> str(R.string.err_offline)
        else -> str(R.string.server_error, 429)
    }

    fun setApiChoice(choice: ApiChoice) {
        store.updateSettings { it.copy(apiChoice = choice) }
        DataSource.choice = choice
        if (choice != ApiChoice.TRANSITOUS) viewModelScope.launch { DataSource.probe() }
        news = Load.Idle
    }

    // ------------------------------------------------------------ fake update
    /** Secret settings: show the update dialog for the latest release, even if it is installed already. */
    fun fakeUpdate() {
        viewModelScope.launch {
            update = UpdateState.Checking
            update = try {
                val release = Updater.latest()
                if (release == null) UpdateState.Error(str(R.string.err_update_check))
                else { showUpdateDialog = true; UpdateState.Available(release) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Error(errorText(e, R.string.err_update_check))
            }
        }
    }

    fun replaySetup() {
        overlays.clear()
        sheetStop = null
        tab = Tab.START
        store.updateSettings { it.copy(setupDone = false) }
    }

    init {
        DataSource.choice = store.settings.value.apiChoice
        // one request to Bahnland Bayern at start, to know whether it is available
        if (DataSource.choice != ApiChoice.TRANSITOUS) viewModelScope.launch { DataSource.probe() }
        if (LocationProvider.hasPermission(app)) locateMe(moveCamera = true)
    }

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    /** A message in the app's language: rate limit and offline get their own text, else [fallback]. */
    private fun errorText(e: Exception, fallback: Int): String = when (e) {
        is RateLimitedException -> str(R.string.err_rate_limited, e.until.atZone(TransitZone).toLocalTime().withNano(0).toString())
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> str(R.string.err_offline)
        else -> str(fallback)
    }

    private companion object {
        const val REFRESH_MS = 60_000L
        const val CELL_LAT = 0.01
        const val CELL_LON = 0.015
    }
}
