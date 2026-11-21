package org.devbucket.gRide.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.devbucket.gRide.data.UiStyle
import org.devbucket.gRide.ui.glass.GlassSurface
import org.devbucket.gRide.ui.glass.LocalGlassBackdrop
import org.devbucket.gRide.ui.glass.LocalUiStyle
import org.devbucket.gRide.ui.map.GRideMap
import org.devbucket.gRide.ui.screens.DeparturesScreen
import org.devbucket.gRide.ui.screens.RunScreen
import org.devbucket.gRide.ui.screens.SecretScreen
import org.devbucket.gRide.ui.screens.SetupScreen
import org.devbucket.gRide.ui.screens.JourneyScreen
import org.devbucket.gRide.ui.screens.SearchScreen
import org.devbucket.gRide.ui.screens.SettingsScreen
import org.devbucket.gRide.ui.screens.StartScreen
import org.devbucket.gRide.ui.screens.StopBoardScreen
import org.devbucket.gRide.ui.screens.TripScreen
import org.devbucket.gRide.ui.theme.isDark

/** Space the floating navigation bar takes at the bottom of the screen. */
val LocalBottomBarHeight = staticCompositionLocalOf { 0.dp }

private data class TabItem(val tab: Tab, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TabItem(Tab.START, "Start", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem(Tab.DEPARTURES, "Departures", Icons.Outlined.DepartureBoard, Icons.Filled.DepartureBoard),
    TabItem(Tab.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun GRideApp(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val glass = settings.uiStyle == UiStyle.LIQUID_GLASS
    val overlay = vm.overlay
    val showNav = settings.setupDone && (overlay == null || overlay is Overlay.StopBoard || overlay is Overlay.Run)
    val navInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomBar = if (showNav) (if (glass) 88.dp else 80.dp) + navInset else navInset

    BackHandler(enabled = vm.canGoBack) { vm.back() }
    if (vm.showUpdateDialog) UpdateDialog(vm)

    CompositionLocalProvider(
        LocalUiStyle provides settings.uiStyle,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        val wallpaper = rememberLayerBackdrop()
        val page = rememberLayerBackdrop()
        val chrome = rememberCombinedBackdrop(wallpaper, page)

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // 1) Wallpaper: the shared map (or a soft gradient in Liquid Glass mode on list pages).
            Box(Modifier.fillMaxSize().then(if (glass) Modifier.layerBackdrop(wallpaper) else Modifier)) {
                MapLayer(vm, bottomBar)
                AnimatedVisibility(!vm.mapVisible, enter = fadeIn(), exit = fadeOut()) {
                    if (glass) GlassWallpaper() else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                }
            }

            // 2) Pages. Their glass panels refract the wallpaper.
            CompositionLocalProvider(LocalGlassBackdrop provides wallpaper, LocalBottomBarHeight provides bottomBar) {
                Box(Modifier.fillMaxSize().then(if (glass) Modifier.layerBackdrop(page) else Modifier)) {
                    val key: Any = if (!settings.setupDone) "setup" else overlay ?: vm.tab
                    AnimatedContent(
                        targetState = key,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { k ->
                        when (k) {
                            Tab.START -> StartScreen(vm)
                            Tab.DEPARTURES -> DeparturesScreen(vm)
                            Tab.SETTINGS -> SettingsScreen(vm)
                            is Overlay.Search -> SearchScreen(vm, k.target)
                            is Overlay.Trip -> TripScreen(vm)
                            is Overlay.JourneyDetail -> JourneyScreen(vm, k.index)
                            is Overlay.StopBoard -> StopBoardScreen(vm, k.stop)
                            is Overlay.Run -> RunScreen(vm, k.departure)
                            is Overlay.Secret -> SecretScreen(vm)
                            "setup" -> SetupScreen(vm)
                            else -> Unit
                        }
                    }
                }
            }

            // 3) Floating chrome: refracts both the wallpaper and the page.
            CompositionLocalProvider(LocalGlassBackdrop provides chrome) {
                AnimatedVisibility(
                    showNav,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    val current = when (overlay) {
                        is Overlay.StopBoard -> Tab.DEPARTURES
                        else -> vm.tab
                    }
                    if (glass) GlassNavBar(current, vm::selectTab) else ExpressiveNavBar(current, vm::selectTab)
                }
            }
        }
    }
}

@Composable
private fun MapLayer(vm: AppViewModel, bottomBar: Dp) {
    val density = LocalDensity.current
    val sheetPeek = when (vm.overlay) {
        is Overlay.Trip -> 400.dp
        is Overlay.JourneyDetail -> 340.dp
        else -> 300.dp + bottomBar
    }
    val showStops = vm.overlay == null && vm.tab == Tab.START
    GRideMap(
        dark = isDark,
        stops = vm.mapStops.values,
        showStops = showStops,
        journey = if (vm.overlay is Overlay.Trip || vm.overlay is Overlay.JourneyDetail) vm.mapJourney else null,
        user = vm.userLocation,
        selectedStopId = vm.sheetStop?.id,
        camera = vm.camera,
        bottomInsetPx = with(density) { sheetPeek.roundToPx() },
        onStopClick = { vm.openStopSheet(it) },
        onMoved = vm::onMapMoved,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Liquid Glass wallpaper for pages without the map: a dark Material You surface with a few
 * slightly lighter, softly edged circles that slowly glide around (so the glass has something to refract).
 */
@Composable
private fun GlassWallpaper() {
    val scheme = MaterialTheme.colorScheme
    val base = if (isDark) scheme.surfaceContainerLowest else scheme.surfaceDim
    val tints = listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer, scheme.primaryContainer)
    val circleColors = tints.map { lerp(base, it, if (isDark) 0.55f else 0.7f) }
    val t = rememberInfiniteTransition(label = "wallpaper")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(48_000, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize().background(base)) {
        val w = size.width
        val h = size.height
        val circles = listOf(
            Triple(0.25f to 0.18f, 0.45f, 1f),
            Triple(0.80f to 0.42f, 0.38f, -1.3f),
            Triple(0.20f to 0.72f, 0.42f, 0.8f),
            Triple(0.75f to 0.88f, 0.36f, -0.7f),
        )
        circles.forEachIndexed { i, (pos, radius, speed) ->
            val a = phase * speed + i * 1.7f
            val c = Offset(
                w * (pos.first + 0.12f * cos(a)),
                h * (pos.second + 0.07f * sin(a * 1.3f)),
            )
            val r = w * radius
            drawCircle(
                Brush.radialGradient(
                    0f to circleColors[i], 0.72f to circleColors[i], 1f to circleColors[i].copy(alpha = 0f),
                    center = c, radius = r,
                ),
                r, c,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    ShortNavigationBar {
        tabs.forEach { item ->
            ShortNavigationBarItem(
                selected = item.tab == selected,
                onClick = { onSelect(item.tab) },
                icon = { Icon(if (item.tab == selected) item.selectedIcon else item.icon, null) },
                label = { Text(item.label, maxLines = 1) },
            )
        }
    }
}

/** Floating glass capsule; pressing any item makes the whole bar grow and light up. */
@Composable
private fun GlassNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.fillMaxWidth().height(64.dp), pressEffect = true) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
                val itemWidth = maxWidth / tabs.size
                val index = tabs.indexOfFirst { it.tab == selected }.coerceAtLeast(0)
                val x by animateDpAsState(itemWidth * index, label = "navIndicator")
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.38f else 0.18f))
                )
                Row(Modifier.fillMaxSize()) {
                    tabs.forEach { item ->
                        val sel = item.tab == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(interactionSource = null, indication = null) { onSelect(item.tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            val tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            Icon(if (sel) item.selectedIcon else item.icon, item.label, tint = tint)
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Spacer that keeps content clear of the floating navigation bar. */
@Composable
fun BottomBarSpacer() {
    Box(Modifier.height(LocalBottomBarHeight.current))
}

@Composable
fun NavigationBarSpacer() {
    Box(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}
