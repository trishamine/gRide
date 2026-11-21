package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.devbucket.gRide.data.LocationProvider
import org.devbucket.gRide.data.ThemeMode
import org.devbucket.gRide.data.UiStyle
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.PrimaryAction
import org.devbucket.gRide.ui.components.SecondaryAction
import org.devbucket.gRide.ui.components.SegmentedChoice
import org.devbucket.gRide.ui.glass.GlassSurface
import org.devbucket.gRide.ui.glass.isGlass

private const val STEPS = 5
private const val INTRO = 1

/** First-run setup: language → animated intro → design → theme → location. Can be replayed from the secret settings. */
@Composable
fun SetupScreen(vm: AppViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    fun previous() { step = if (step == INTRO + 1) 0 else step - 1 } // going back skips the intro animation
    BackHandler(enabled = step > 0) { previous() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 300f)) { it * dir / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir / 3 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "setup",
        ) { s ->
            when (s) {
                0 -> LanguageStep()
                INTRO -> IntroStep(onNext = { step = INTRO + 1 })
                2 -> StyleStep(vm)
                3 -> ThemeStep(vm)
                else -> LocationStep(vm)
            }
        }
        if (step != INTRO) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dots(step)
                Spacer(Modifier.weight(1f))
                if (step > 0) TextButton(onClick = { previous() }) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.width(8.dp))
                if (step < STEPS - 1) PrimaryAction(stringResource(R.string.next), { step++ })
                else PrimaryAction(stringResource(R.string.lets_ride), vm::finishSetup)
            }
        }
    }
}

@Composable
private fun Dots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until STEPS) {
            if (i == INTRO) continue
            val w by animateDpAsState(if (i == step) 24.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dot")
            Box(
                Modifier
                    .size(width = w, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

// ------------------------------------------------------------ language

@Composable
private fun LanguageStep() {
    StepPage(stringResource(R.string.choose_language), stringResource(R.string.change_later)) {
        LanguagePicker()
    }
}

// --------------------------------------------------------------- intro

@Composable
private fun IntroStep(onNext: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    var showRest by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        delay(350)
        title.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 200f))
        showRest = true
    }
    // the logo keeps morphing between a circle and a rounded square while it slowly turns
    val t = rememberInfiniteTransition(label = "logo")
    val corner by t.animateFloat(50f, 28f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "corner")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "turn")

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(148.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = turn }
                .clip(RoundedCornerShape(corner.toInt()))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Tram, null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(76.dp).graphicsLayer { rotationZ = -turn },
            )
        }
        Spacer(Modifier.height(36.dp))
        Text(
            "gRide",
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer {
                alpha = title.value.coerceIn(0f, 1f)
                translationY = (1f - title.value) * 60f
            },
        )
        AnimatedVisibility(showRest, enter = fadeIn(tween(500)) + slideInVertically { it / 2 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.intro_tagline),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(40.dp))
                PrimaryAction(stringResource(R.string.get_started), onNext, Modifier.fillMaxWidth(0.7f))
            }
        }
    }
}

// ---------------------------------------------------------------- style

@Composable
private fun StyleStep(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    StepPage(stringResource(R.string.pick_style), stringResource(R.string.change_later)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StyleOption(
                title = stringResource(R.string.style_expressive),
                text = stringResource(R.string.expressive_short),
                selected = settings.uiStyle == UiStyle.EXPRESSIVE,
                modifier = Modifier.weight(1f),
                onClick = { vm.store.updateSettings { it.copy(uiStyle = UiStyle.EXPRESSIVE) } },
            ) { ExpressivePreview() }
            StyleOption(
                title = stringResource(R.string.style_glass),
                text = stringResource(R.string.glass_short),
                selected = settings.uiStyle == UiStyle.LIQUID_GLASS,
                modifier = Modifier.weight(1f),
                onClick = { vm.store.updateSettings { it.copy(uiStyle = UiStyle.LIQUID_GLASS) } },
            ) { GlassPreview() }
        }
    }
}

@Composable
private fun StyleOption(
    title: String,
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val border by animateDpAsState(if (selected) 3.dp else 0.dp, label = "border")
    AppCard(
        modifier.border(border, MaterialTheme.colorScheme.primary, RoundedCornerShape(26.dp)),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { preview() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpressivePreview() {
    val t = rememberInfiniteTransition(label = "exp")
    val c by t.animateFloat(50f, 20f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "c")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(c.toInt())).background(MaterialTheme.colorScheme.primary)
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(stringResource(R.string.tab_departures), Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GlassPreview() {
    if (isGlass) {
        GlassSurface(Modifier.fillMaxWidth().height(56.dp), pressEffect = true) {
            Text(stringResource(R.string.glass), fontWeight = FontWeight.Bold)
        }
    } else {
        // not active yet: hint at it with translucent layers
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.glass), fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------------------------------------------------------------- theme

@Composable
private fun ThemeStep(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    StepPage(stringResource(R.string.light_or_dark), stringResource(R.string.light_or_dark_desc)) {
        Icon(
            Icons.Filled.DarkMode, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(
            options = ThemeMode.entries.map { stringResource(it.label) },
            selected = settings.themeMode.ordinal,
            onSelect = { i -> vm.store.updateSettings { it.copy(themeMode = ThemeMode.entries[i]) } },
        )
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dynamic_colours), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.dynamic_colours_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.dynamicColor,
                    onCheckedChange = { v -> vm.store.updateSettings { it.copy(dynamicColor = v) } },
                )
            }
        }
    }
}

// ------------------------------------------------------------- location

@Composable
private fun LocationStep(vm: AppViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
    }
    StepPage(stringResource(R.string.stops_near_you), stringResource(R.string.location_desc)) {
        Icon(
            Icons.Filled.MyLocation, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        if (granted) {
            Text(
                stringResource(R.string.location_on),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            SecondaryAction(
                stringResource(R.string.allow_location),
                { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                Modifier.align(Alignment.CenterHorizontally),
                icon = Icons.Filled.MyLocation,
            )
        }
    }
}

@Composable
private fun StepPage(title: String, subtitle: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
