package com.example.cascade

import android.text.format.DateFormat
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp

private fun shadowStyle(t: LTheme) =
    if (t.light) TextStyle.Default
    else TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 10f))

// ───────────────────────── Root ─────────────────────────

@Composable
fun LauncherApp(vm: LauncherViewModel, window: Window) {
    val themes = themeList()
    val theme = themes.firstOrNull { it.id == vm.themeId } ?: themes.first()

    val view = LocalView.current
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = theme.light
            isAppearanceLightNavigationBars = theme.light
        }
    }
    BackHandler { vm.closeAll() }

    val scheme = if (theme.light) lightColorScheme(primary = theme.accent, onSurface = theme.text)
    else darkColorScheme(primary = theme.accent, onSurface = theme.text)

    MaterialTheme(colorScheme = scheme) {
        // Wallpaper dimming: normal → deeper while the A–Z list is open → none when favourites are hidden.
        val dimTarget = when {
            vm.letter != null -> (theme.scrimAlpha + 0.22f).coerceAtMost(1f)
            vm.favVisible -> theme.scrimAlpha
            else -> 0f
        }
        val dim by animateFloatAsState(dimTarget, tween(450), label = "dim")

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(theme.scrim.copy(alpha = dim)) }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { vm.closeAll() },
                        onLongPress = { vm.settingsOpen = true }, // long-press the wallpaper → themes
                    )
                },
        ) {
            Home(vm, theme)
            SettingsPanel(vm, themes, theme)
        }
    }
}

// ───────────────────────── Home screen ─────────────────────────

@Composable
private fun Home(vm: LauncherViewModel, theme: LTheme) {
    val clockAlpha by animateFloatAsState(if (vm.letter == null) 1f else 0f, tween(250), label = "clock")

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Clock(theme, Modifier.align(Alignment.TopStart).graphicsLayer { alpha = clockAlpha })

        // Favourites: bottom-left, big text. Hidden by the ★ button or while a letter list is open.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.8f).fillMaxHeight(0.6f)) {
            AnimatedVisibility(
                visible = vm.favVisible && vm.letter == null,
                enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 6 },
                exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 6 },
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 28.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                ) {
                    if (vm.favorites.isEmpty() && vm.apps.isNotEmpty()) {
                        item {
                            Text(
                                "Tap A–Z, pick a letter, then long-press an app to add it here.",
                                color = theme.text.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 24.dp),
                                style = shadowStyle(theme),
                            )
                        }
                    }
                    items(vm.favorites, key = { it.id }) { AppRow(it, vm, theme, inFavorites = true) }
                }
            }
        }

        // Apps for the selected letter – only exists while you slide on / tap a letter.
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.8f).fillMaxHeight()) {
            AnimatedContent(
                vm.letter,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                label = "letterList",
            ) { l ->
                if (l != null) {
                    val list = vm.byLetter[l].orEmpty()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 28.dp, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        if (list.isEmpty()) {
                            item {
                                Text(
                                    "No apps under $l",
                                    color = theme.text.copy(alpha = 0.6f),
                                    fontSize = 20.sp,
                                    style = shadowStyle(theme),
                                )
                            }
                        }
                        items(list, key = { it.id }) { AppRow(it, vm, theme, inFavorites = false) }
                    }
                }
            }
        }

        // Right rail: ★ (favourites), A–Z (opens the letters), then the letters themselves.
        val btn = 44.dp
        val gap = 8.dp
        val letterH = ((maxHeight * 0.86f - btn * 2 - gap * 2) / RailLetters.size).coerceIn(14.dp, 26.dp)
        val railH = btn * 2 + gap * 2 + letterH * RailLetters.size
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = ((maxHeight - railH) / 2).coerceAtLeast(0.dp), end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailButton(if (vm.favVisible) "★" else "☆", vm.favVisible, theme, 22.sp, vm::onStar)
            Spacer(Modifier.height(gap))
            RailButton("A–Z", vm.railOpen, theme, 13.sp, vm::toggleRail)
            Spacer(Modifier.height(gap))
            LetterRail(vm.railOpen, vm.byLetter.keys, vm.letter, letterH, theme, vm::select)
        }
    }
}

@Composable
private fun Clock(theme: LTheme, modifier: Modifier) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 50)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(now))
    Column(modifier.padding(start = 28.dp, top = 28.dp)) {
        Text(time, color = theme.text, fontSize = 80.sp, fontWeight = FontWeight.ExtraLight, style = shadowStyle(theme))
        Text(date, color = theme.text.copy(alpha = 0.85f), fontSize = 17.sp, style = shadowStyle(theme))
    }
}

// ───────────────────────── Rail ─────────────────────────

@Composable
private fun RailButton(label: String, active: Boolean, theme: LTheme, size: TextUnit, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(theme.text.copy(alpha = if (active) 0.24f else 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) theme.accent else theme.text, fontSize = size, fontWeight = FontWeight.Medium)
    }
}

/**
 * The A–Z column. Letters drop down one by one when [expanded]; touching or sliding on them
 * selects a letter and the column bends toward your finger.
 */
@Composable
private fun LetterRail(
    expanded: Boolean,
    enabled: Set<Char>,
    selected: Char?,
    letterH: Dp,
    theme: LTheme,
    onSelect: (Char) -> Unit,
) {
    val density = LocalDensity.current
    val hPx = with(density) { letterH.toPx() }
    val maxShift = with(density) { 36.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val onSelectState by rememberUpdatedState(onSelect)

    var rawY by remember { mutableFloatStateOf(0f) }
    var touching by remember { mutableStateOf(false) }
    var lastPick by remember { mutableIntStateOf(-1) }
    val touchY = animateFloatAsState(rawY, spring(dampingRatio = 0.85f, stiffness = 450f), label = "touchY")
    val bend = animateFloatAsState(if (touching) 1f else 0f, tween(220), label = "bend")

    Column(
        Modifier
            .width(48.dp)
            .height(letterH * RailLetters.size)
            .then(
                if (!expanded) Modifier else Modifier.pointerInput(hPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touching = true
                        lastPick = -1
                        var change = down
                        while (true) {
                            val y = change.position.y.coerceIn(0f, hPx * RailLetters.size - 1f)
                            rawY = y
                            val i = (y / hPx).toInt().coerceIn(0, RailLetters.lastIndex)
                            if (i != lastPick) {
                                lastPick = i
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectState(RailLetters[i])
                            }
                            change.consume()
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                        }
                        touching = false
                    }
                }
            ),
    ) {
        RailLetters.forEachIndexed { i, ch ->
            RailLetter(ch, i, expanded, ch in enabled, ch == selected, letterH, theme, touchY, bend, hPx, maxShift)
        }
    }
}

@Composable
private fun RailLetter(
    ch: Char,
    index: Int,
    expanded: Boolean,
    hasApps: Boolean,
    selected: Boolean,
    h: Dp,
    theme: LTheme,
    touchY: State<Float>,
    bend: State<Float>,
    hPx: Float,
    maxShift: Float,
) {
    // Staggered drop-down: each letter starts a little after the one above it (reverse when closing).
    val appear = animateFloatAsState(
        if (expanded) 1f else 0f,
        tween(260, delayMillis = if (expanded) index * 14 else (RailLetters.size - index) * 6, easing = FastOutSlowInEasing),
        label = "appear",
    )
    Box(
        Modifier
            .size(48.dp, h)
            .graphicsLayer {
                val d = (index * hPx + hPx / 2 - touchY.value) / hPx
                val bump = exp(-d * d / 5f) * bend.value
                val s = 1f + 0.8f * bump
                translationX = -bump * maxShift
                translationY = -(1f - appear.value) * 36.dp.toPx()
                scaleX = s
                scaleY = s
                alpha = appear.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ch.toString(),
            color = if (selected) theme.accent else theme.text.copy(alpha = if (hasApps) 0.92f else 0.32f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ───────────────────────── App row ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppInfo, vm: LauncherViewModel, theme: LTheme, inFavorites: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")

    Box {
        Row(
            Modifier
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { vm.launch(app) },
                    onLongClick = { menu = true },
                )
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (vm.showIcons) {
                Image(app.icon, null, Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
            }
            Text(
                app.label,
                color = theme.text,
                fontSize = (32 * vm.textScale).sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = shadowStyle(theme),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (vm.isFav(app)) "Remove from favorites" else "Add to favorites") },
                onClick = { menu = false; vm.toggleFav(app) },
            )
            if (inFavorites) {
                DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.moveUp(app) })
            }
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; vm.info(app) })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; vm.uninstall(app) })
        }
    }
}

// ───────────────────────── Theme panel ─────────────────────────

@Composable
private fun BoxScope.SettingsPanel(vm: LauncherViewModel, themes: List<LTheme>, theme: LTheme) {
    AnimatedVisibility(
        visible = vm.settingsOpen,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
    ) {
        val ink = if (theme.light) Color(0xFF15181D) else Color(0xFFF2F4F8)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(if (theme.light) Color(0xFFF6F7F9) else Color(0xFF181B20))
                .pointerInput(Unit) { detectTapGestures { } } // taps on the panel shouldn't close it
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("Theme", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            themes.chunked(5).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEach { t -> Swatch(t, t.id == vm.themeId, ink) { vm.setTheme(t.id) } }
                    repeat(5 - row.size) { Spacer(Modifier.width(60.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show app icons", color = ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(checked = vm.showIcons, onCheckedChange = vm::setIcons)
            }
            Spacer(Modifier.height(8.dp))
            Text("Text size", color = ink, fontSize = 16.sp)
            Slider(value = vm.textScale, onValueChange = vm::setScale, valueRange = 0.8f..1.3f)
        }
    }
}

@Composable
private fun Swatch(t: LTheme, selected: Boolean, ink: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(60.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(t.scrim.copy(alpha = 1f), t.accent)))
                .border(if (selected) 3.dp else 1.dp, if (selected) t.accent else ink.copy(alpha = 0.25f), CircleShape),
        )
        Spacer(Modifier.height(4.dp))
        Text(t.name, color = ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
