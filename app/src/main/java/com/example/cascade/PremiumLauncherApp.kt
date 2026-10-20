package com.example.cascade

import android.content.Context
import android.os.BatteryManager
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Noir = Color(0xFF080808)
private val WhiteInk = Color(0xFFF5F5F5)
private val MutedInk = Color(0xFFC8C8C8)
private val SubtleInk = Color(0xFF8E8E8E)

/**
 * Minimal, premium launcher screen inspired by the supplied reference image.
 * Existing ViewModel actions and app launching behaviour remain in use.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumLauncherApp(vm: LauncherViewModel, window: Window) {
    val context = LocalContext.current
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(readBatteryPercent(context)) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    LaunchedEffect(context) {
        while (true) {
            tick = System.currentTimeMillis()
            battery = readBatteryPercent(context)
            delay(15_000)
        }
    }

    SideEffect {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
    }

    val now = Date(tick)
    val timeText = remember(tick) {
        SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
    }
    val dateText = remember(tick) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
    }

    val baseApps = if (vm.favVisible && vm.favorites.isNotEmpty()) vm.favorites else vm.apps
    val displayApps = vm.letter?.let { chosen ->
        baseApps.filter { it.letter == chosen }
    } ?: baseApps

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF141414), Color(0xFF080808), Color(0xFF030303))
                )
            )
    ) {
        if (vm.showBackdrop) {
            NoirWaveBackdrop(Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 40.dp,
                    end = 28.dp,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 25.dp,
                    bottom = 30.dp
                )
        ) {
            // Thin, spacious system-style numerals like the reference.
            Text(
                text = timeText,
                color = WhiteInk,
                fontSize = 76.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-2.2).sp,
                lineHeight = 82.sp,
                maxLines = 1,
                modifier = Modifier.combinedClickable(
                    onClick = { /* Keep the face visually clean. */ },
                    onLongClick = { vm.settingsOpen = true },
                    onDoubleClick = { vm.toggleRail() }
                )
            )

            Spacer(Modifier.height(12.dp))

            // Tap the date line to switch between favourites and all installed apps.
            Text(
                text = "$dateText  $battery%",
                color = MutedInk,
                fontSize = 22.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.25).sp,
                maxLines = 1,
                modifier = Modifier.combinedClickable(
                    onClick = { vm.onStar() },
                    onLongClick = { vm.settingsOpen = true }
                )
            )

            Spacer(Modifier.height(56.dp))

            Column(modifier = Modifier.padding(start = 12.dp)) {
                if (displayApps.isEmpty()) {
                    Text(
                        text = "No apps to display",
                        color = SubtleInk,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                } else {
                    displayApps.forEach { app ->
                        AppTextRow(
                            app = app,
                            showIcon = vm.showIcons,
                            textScale = vm.textScale,
                            onClick = { vm.launch(app) },
                            onLongClick = { selectedApp = app }
                        )
                    }
                }
            }

        }

        // Alphabet rail is intentionally hidden until the clock is double-tapped.
        if (vm.railOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 7.dp, top = 18.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ('A'..'Z').forEach { c ->
                    Text(
                        text = c.toString(),
                        color = if (vm.letter == c) WhiteInk else SubtleInk,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = if (vm.letter == c) FontWeight.Medium else FontWeight.Light,
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { vm.select(c) },
                                onLongClick = { vm.select(c) }
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }

    if (vm.settingsOpen) {
        AlertDialog(
            onDismissRequest = { vm.settingsOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            containerColor = Color(0xFF171717),
            titleContentColor = WhiteInk,
            textContentColor = MutedInk,
            title = {
                Text(
                    text = "DISPLAY",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    fontSize = 17.sp,
                    letterSpacing = 2.2.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingSwitchRow(
                        label = "Show app logos",
                        checked = vm.showIcons,
                        onCheckedChange = vm::setIcons
                    )
                    SettingSwitchRow(
                        label = "Dark wave backdrop",
                        checked = vm.showBackdrop,
                        onCheckedChange = vm::setBackdrop
                    )
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Label size",
                                color = MutedInk,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(vm.textScale * 100).toInt()}%",
                                color = WhiteInk,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light
                            )
                        }
                        Slider(
                            value = vm.textScale,
                            onValueChange = vm::setScale,
                            valueRange = 0.8f..1.35f
                        )
                    }
                    Text(
                        text = "Tap the date to switch between favourites and all apps. Long-press an app for actions.",
                        color = SubtleInk,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.settingsOpen = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = WhiteInk)
                ) {
                    Text("DONE", letterSpacing = 1.4.sp, fontWeight = FontWeight.Light)
                }
            }
        )
    }

    selectedApp?.let { app ->
        AlertDialog(
            onDismissRequest = { selectedApp = null },
            containerColor = Color(0xFF171717),
            titleContentColor = WhiteInk,
            textContentColor = MutedInk,
            title = {
                Text(
                    text = app.label,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            vm.toggleFav(app)
                            selectedApp = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = WhiteInk)
                    ) {
                        Text(if (vm.isFav(app)) "Remove favourite" else "Add to favourites")
                    }
                    if (vm.isFav(app)) {
                        TextButton(
                            onClick = {
                                vm.moveUp(app)
                                selectedApp = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = WhiteInk)
                        ) { Text("Move up in favourites") }
                    }
                    TextButton(
                        onClick = {
                            selectedApp = null
                            vm.info(app)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = WhiteInk)
                    ) { Text("App info") }
                    TextButton(
                        onClick = {
                            selectedApp = null
                            vm.uninstall(app)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = WhiteInk)
                    ) { Text("Uninstall") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedApp = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = WhiteInk)
                ) { Text("CLOSE") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTextRow(
    app: AppInfo,
    showIcon: Boolean,
    textScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A fixed-width leading slot keeps labels aligned when logos are toggled off.
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showIcon) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Canvas(Modifier.size(16.dp)) {
                    drawCircle(color = WhiteInk.copy(alpha = 0.9f))
                }
            }
        }

        Spacer(Modifier.width(52.dp))

        Text(
            text = app.label,
            color = WhiteInk,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Thin,
            fontSize = (26f * textScale.coerceIn(0.8f, 1.35f)).sp,
            letterSpacing = 0.05.sp,
            lineHeight = (34f * textScale.coerceIn(0.8f, 1.35f)).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MutedInk,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NoirWaveBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun wave(y: Float, amplitude: Float, drift: Float, color: Color) {
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(
                    w * 0.18f, y - amplitude + drift,
                    w * 0.36f, y + amplitude * 0.72f,
                    w * 0.56f, y + amplitude * 0.16f
                )
                cubicTo(
                    w * 0.75f, y - amplitude * 0.72f,
                    w * 0.88f, y + amplitude * 0.62f,
                    w, y - amplitude * 0.12f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path = path, color = color, style = Fill)
        }

        wave(h * 0.31f, h * 0.075f, -h * 0.018f, Color(0xFF181818).copy(alpha = 0.68f))
        wave(h * 0.43f, h * 0.052f, h * 0.012f, Color(0xFF242424).copy(alpha = 0.44f))
        wave(h * 0.55f, h * 0.045f, -h * 0.008f, Color(0xFF101010).copy(alpha = 0.72f))
        wave(h * 0.70f, h * 0.034f, h * 0.008f, Color(0xFF1D1D1D).copy(alpha = 0.35f))
    }
}

private fun readBatteryPercent(context: Context): Int = runCatching {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}.getOrDefault(100)
