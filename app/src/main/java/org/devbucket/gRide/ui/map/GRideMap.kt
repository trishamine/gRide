package org.devbucket.gRide.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.devbucket.gRide.data.Journey
import org.devbucket.gRide.data.LatLon
import org.devbucket.gRide.data.Mode
import org.devbucket.gRide.data.Place
import org.devbucket.gRide.ui.CameraRequest
import org.devbucket.gRide.ui.theme.modeColor
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.max

/**
 * OpenStreetMap map (osmdroid). It is a normal View, so Compose draws it into the
 * Liquid Glass backdrop layer and glass surfaces can refract it.
 */
@Composable
fun GRideMap(
    dark: Boolean,
    stops: Collection<Place>,
    showStops: Boolean,
    journey: Journey?,
    user: LatLon?,
    selectedStopId: String?,
    camera: CameraRequest?,
    bottomInsetPx: Int,
    onStopClick: (Place) -> Unit,
    onMoved: (center: LatLon, radiusMeters: Int, zoom: Double) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val holder = remember { MapHolder(context) }
    holder.onStopClick = onStopClick
    holder.onMoved = onMoved

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.map.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { holder.map.onDetach() } }

    LaunchedEffect(dark) { holder.setDark(dark) }
    holder.animate = interactive
    LaunchedEffect(stops, showStops, selectedStopId) { holder.setStops(if (showStops) stops else emptyList(), selectedStopId) }
    LaunchedEffect(journey) { holder.setJourney(journey) }
    LaunchedEffect(user) { holder.setUser(user) }
    LaunchedEffect(camera, bottomInsetPx) { camera?.let { holder.move(it, bottomInsetPx) } }

    AndroidView(factory = { holder.map }, modifier = modifier)
}

private class MapHolder(private val context: Context) {
    var onStopClick: (Place) -> Unit = {}
    var onMoved: (LatLon, Int, Double) -> Unit = { _, _, _ -> }

    private val density = context.resources.displayMetrics.density
    private val stopIcon = BitmapDrawable(context.resources, stopSignBitmap((26 * density).toInt()))
    private val stopIconBig = BitmapDrawable(context.resources, stopSignBitmap((40 * density).toInt()))
    private val userIcon = BitmapDrawable(context.resources, dotBitmap((22 * density).toInt(), 0xFF2F80ED.toInt()))

    private val stopMarkers = HashMap<String, Marker>()
    private var routeOverlays = listOf<org.osmdroid.views.overlay.Overlay>()
    private var userMarker: Marker? = null
    private var selectedId: String? = null
    var animate = true

    /** Stop icons only show when zoomed in far enough, so the map is not cluttered. */
    private var stopsVisible = true
    private val minStopZoom = 14.5

    /** The last camera request; applied once the map has a size (also after re-layouts). */
    private var pendingCamera: Pair<CameraRequest, Int>? = null

    val map: MapView = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 6.0
        maxZoomLevel = 19.5
        isVerticalMapRepetitionEnabled = false
        controller.setZoom(14.0)
        controller.setCenter(GeoPoint(48.3665, 10.8986))
        overlays.add(CopyrightOverlay(context).apply { setAlignBottom(false); setAlignRight(true) })
        addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { scheduleMoved(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { updateStopVisibility(); scheduleMoved(); return false }
        })
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pendingCamera != null && width > 0 && height > 0) post { applyCamera() }
        }
    }

    private fun updateStopVisibility() {
        val visible = map.zoomLevelDouble >= minStopZoom
        if (visible == stopsVisible) return
        stopsVisible = visible
        stopMarkers.forEach { (id, m) -> m.isEnabled = visible || id == selectedId }
        map.invalidate()
    }

    private val movedRunnable = Runnable {
        val box = map.boundingBox
        val center = map.mapCenter
        val radius = (box.diagonalLengthInMeters / 2).toInt()
        onMoved(LatLon(center.latitude, center.longitude), radius, map.zoomLevelDouble)
    }

    private fun scheduleMoved() {
        map.removeCallbacks(movedRunnable)
        map.postDelayed(movedRunnable, 450)
    }

    fun setDark(dark: Boolean) {
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) darkFilter() else null)
        map.overlayManager.tilesOverlay.loadingBackgroundColor = if (dark) 0xFF1B1F24.toInt() else 0xFFEDEBE6.toInt()
        map.invalidate()
    }

    fun setStops(stops: Collection<Place>, selected: String?) {
        val wanted = stops.filter { it.hasCoord }.associateBy { it.id }
        val removed = stopMarkers.keys - wanted.keys
        removed.forEach { id -> stopMarkers.remove(id)?.let { m -> map.overlays.remove(m) } }
        for ((id, place) in wanted) {
            val marker = stopMarkers.getOrPut(id) {
                Marker(map).apply {
                    position = GeoPoint(place.lat!!, place.lon!!)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    title = place.fullName
                    setOnMarkerClickListener { _, _ -> onStopClick(place); true }
                    map.overlays.add(this)
                }
            }
            marker.icon = if (id == selected) stopIconBig else stopIcon
            marker.isEnabled = stopsVisible || id == selected
        }
        selectedId = selected
        map.invalidate()
    }

    fun setJourney(journey: Journey?) {
        map.overlays.removeAll(routeOverlays.toSet())
        val list = mutableListOf<org.osmdroid.views.overlay.Overlay>()
        journey?.legs?.forEach { leg ->
            if (leg.path.size < 2) return@forEach
            val color = modeColor(leg.mode).toArgb()
            if (leg.mode != Mode.WALK) {
                list += Polyline(map).apply {
                    setPoints(leg.path.map { GeoPoint(it.lat, it.lon) })
                    outlinePaint.color = 0xFFFFFFFF.toInt()
                    outlinePaint.strokeWidth = 11 * density
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    setOnClickListener { _, _, _ -> false }
                }
            }
            list += Polyline(map).apply {
                setPoints(leg.path.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color = color
                outlinePaint.strokeWidth = (if (leg.mode == Mode.WALK) 5 else 7) * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                if (leg.mode == Mode.WALK) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(4 * density, 8 * density), 0f)
                setOnClickListener { _, _, _ -> false }
            }
            // stop dots where we board / alight
            listOfNotNull(leg.from.coord, leg.to.coord).forEach { p ->
                if (leg.mode != Mode.WALK) list += Marker(map).apply {
                    position = GeoPoint(p.lat, p.lon)
                    icon = BitmapDrawable(context.resources, ringBitmap((16 * density).toInt(), color))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> true }
                }
            }
        }
        journey?.legs?.firstOrNull()?.from?.coord?.let { start ->
            list += pinMarker(start, 0xFF3FA34D.toInt())
        }
        journey?.legs?.lastOrNull()?.to?.coord?.let { end ->
            list += pinMarker(end, 0xFFE5484D.toInt())
        }
        routeOverlays = list
        map.overlays.addAll(list)
        map.invalidate()
    }

    private fun pinMarker(p: LatLon, color: Int) = Marker(map).apply {
        position = GeoPoint(p.lat, p.lon)
        icon = BitmapDrawable(context.resources, dotBitmap((20 * density).toInt(), color))
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
        setOnMarkerClickListener { _, _ -> true }
    }

    fun setUser(user: LatLon?) {
        userMarker?.let { map.overlays.remove(it) }
        userMarker = user?.let {
            Marker(map).apply {
                position = GeoPoint(it.lat, it.lon)
                icon = userIcon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> true }
                map.overlays.add(this)
            }
        }
        map.invalidate()
    }

    fun move(request: CameraRequest, bottomInsetPx: Int) {
        pendingCamera = request to bottomInsetPx
        if (map.isLayoutOccurred && map.width > 0 && map.height > 0) map.post { applyCamera() }
    }

    private fun applyCamera() {
        val (request, bottomInsetPx) = pendingCamera ?: return
        if (map.width <= 0 || map.height <= 0) return
        pendingCamera = null
        run {
            val points = request.points
            if (points.isEmpty()) return
            if (points.size == 1 || request.zoom != null) {
                val p = points.first()
                val zoom = request.zoom ?: map.zoomLevelDouble
                if (animate) map.controller.animateTo(GeoPoint(p.lat, p.lon), zoom, 600L)
                else { map.controller.setZoom(zoom); map.controller.setCenter(GeoPoint(p.lat, p.lon)) }
                updateStopVisibility()
                return
            }
            val north = points.maxOf { it.lat }
            val south = points.minOf { it.lat }
            val east = points.maxOf { it.lon }
            val west = points.minOf { it.lon }
            // keep the route above the bottom sheet: stretch the box downwards
            val h = map.height
            val visibleFraction = ((h - bottomInsetPx).toFloat() / h).coerceIn(0.3f, 1f)
            val latSpan = max(north - south, 0.002)
            val extendedSouth = north - latSpan / visibleFraction
            val box = BoundingBox(north + latSpan * 0.08, east + 0.002, extendedSouth, west - 0.002)
            map.zoomToBoundingBox(box, animate, (32 * density).toInt())
            map.invalidate()
        }
    }

    // ------------------------------------------------------------ bitmaps

    private fun stopSignBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        p.color = 0x55000000
        c.drawCircle(r, r + size * 0.03f, r * 0.96f, p)
        p.color = 0xFF1B7A3E.toInt(); c.drawCircle(r, r, r * 0.94f, p)
        p.color = 0xFFF7C91E.toInt(); c.drawCircle(r, r, r * 0.76f, p)
        p.color = 0xFF1B7A3E.toInt(); p.strokeWidth = r * 0.2f; p.strokeCap = Paint.Cap.BUTT
        val h = r * 0.9f; val w = r * 0.58f
        c.drawLine(r - w / 2, r - h / 2, r - w / 2, r + h / 2, p)
        c.drawLine(r + w / 2, r - h / 2, r + w / 2, r + h / 2, p)
        c.drawLine(r - w / 2, r, r + w / 2, r, p)
        return bmp
    }

    private fun dotBitmap(size: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        p.color = 0x44000000; c.drawCircle(r, r, r, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(r, r, r * 0.86f, p)
        p.color = color; c.drawCircle(r, r, r * 0.6f, p)
        return bmp
    }

    private fun ringBitmap(size: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        p.color = color; c.drawCircle(r, r, r, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(r, r, r * 0.55f, p)
        return bmp
    }

    /** Inverts the light OSM style into a calm dark map while keeping hues. */
    private fun darkFilter(): ColorMatrixColorFilter {
        val invert = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        // rotate hue by 180° so water stays blue and parks stay green after inverting
        val hue = ColorMatrix()
        val cos = -1f
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        hue.set(
            floatArrayOf(
                lr + cos * (1 - lr) + 0f, lg + cos * (-lg) + 0f, lb + cos * (-lb) + 0f, 0f, 0f,
                lr + cos * (-lr) + 0f, lg + cos * (1 - lg) + 0f, lb + cos * (-lb) + 0f, 0f, 0f,
                lr + cos * (-lr) + 0f, lg + cos * (-lg) + 0f, lb + cos * (1 - lb) + 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        val dim = ColorMatrix().apply { setScale(0.82f, 0.85f, 0.9f, 1f) }
        val sat = ColorMatrix().apply { setSaturation(0.6f) }
        invert.postConcat(hue)
        invert.postConcat(sat)
        invert.postConcat(dim)
        return ColorMatrixColorFilter(invert)
    }
}
