package com.example.cascade

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app
    private val prefs = app.getSharedPreferences("cascade", Context.MODE_PRIVATE)

    // ---- saved settings ----
    var themeId by mutableStateOf(prefs.getString("theme", "auto") ?: "auto"); private set
    var showIcons by mutableStateOf(prefs.getBoolean("icons", true)); private set
    var textScale by mutableFloatStateOf(prefs.getFloat("scale", 1f)); private set
    var favVisible by mutableStateOf(prefs.getBoolean("favVisible", true)); private set
    var showBackdrop by mutableStateOf(prefs.getBoolean("backdrop", true)); private set

    /** Ordered favourite ids. null = first run, defaults get filled in once apps load. */
    private var favIds by mutableStateOf(
        prefs.getString("favs", null)?.split("\n")?.filter { it.isNotBlank() }
    )

    // ---- data ----
    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    val byLetter by derivedStateOf { apps.groupBy { it.letter } }
    val favorites by derivedStateOf {
        val byId = apps.associateBy { it.id }
        favIds.orEmpty().mapNotNull { byId[it] }
    }

    // ---- transient UI state ----
    var railOpen by mutableStateOf(false); private set
    var letter by mutableStateOf<Char?>(null); private set
    var settingsOpen by mutableStateOf(false)

    private var loadJob: Job? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        ContextCompat.registerReceiver(
            ctx,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    override fun onCleared() {
        ctx.unregisterReceiver(receiver)
    }

    // ---- rail / list behaviour ----
    fun toggleRail() {
        railOpen = !railOpen
        if (!railOpen) letter = null
    }

    fun select(c: Char) {
        if (letter != c) letter = c
    }

    fun closeAll() {
        railOpen = false
        letter = null
        settingsOpen = false
    }

    /** Star button: first tap leaves the A–Z list, otherwise shows/hides favourites. */
    fun onStar() {
        if (letter != null) {
            letter = null
            railOpen = false
        } else {
            favVisible = !favVisible
            prefs.edit().putBoolean("favVisible", favVisible).apply()
        }
    }

    // ---- settings ----
    fun setTheme(id: String) {
        themeId = id
        prefs.edit().putString("theme", id).apply()
    }

    fun setIcons(v: Boolean) {
        showIcons = v
        prefs.edit().putBoolean("icons", v).apply()
    }

    fun setScale(v: Float) {
        textScale = v
        prefs.edit().putFloat("scale", v).apply()
    }

    fun setBackdrop(v: Boolean) {
        showBackdrop = v
        prefs.edit().putBoolean("backdrop", v).apply()
    }

    // ---- favourites ----
    fun isFav(a: AppInfo) = a.id in favIds.orEmpty()

    fun toggleFav(a: AppInfo) {
        val l = favIds.orEmpty()
        saveFavs(if (a.id in l) l - a.id else l + a.id)
    }

    fun moveUp(a: AppInfo) {
        val shown = favorites.map { it.id }.toMutableList()
        val i = shown.indexOf(a.id)
        if (i > 0) {
            shown.removeAt(i)
            shown.add(i - 1, a.id)
            saveFavs(shown + favIds.orEmpty().filter { it !in shown })
        }
    }

    private fun saveFavs(l: List<String>) {
        favIds = l
        prefs.edit().putString("favs", l.joinToString("\n")).apply()
    }

    // ---- launching ----
    private fun start(i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun launch(a: AppInfo) {
        start(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.cls)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
        closeAll()
    }

    fun info(a: AppInfo) = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))

    fun uninstall(a: AppInfo) = start(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))

    // ---- loading ----
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { queryApps() }
            apps = list
            if (favIds == null) saveFavs(defaultFavs(list))
        }
    }

    @Suppress("DEPRECATION")
    private fun queryApps(): List<AppInfo> {
        val pm = ctx.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(probe, 0)
        }
        return found
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { r ->
                val a = r.activityInfo
                AppInfo(
                    id = "${a.packageName}/${a.name}",
                    pkg = a.packageName,
                    cls = a.name,
                    label = r.loadLabel(pm).toString(),
                    icon = r.loadIcon(pm).toBitmap(112, 112).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** First run: phone, messages, browser, camera – whatever the device uses. */
    @Suppress("DEPRECATION")
    private fun defaultFavs(list: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return probes.mapNotNull { p ->
            val pkg = pm.resolveActivity(p, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            list.firstOrNull { it.pkg == pkg }?.id
        }.distinct()
    }
}
