package com.romshortcutmaker

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.romshortcutmaker.data.COMMON_CORES
import com.romshortcutmaker.data.CorePreset
import com.romshortcutmaker.ui.theme.RomShortcutMakerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private enum class ShortcutTarget(val displayName: String) {
    RETROARCH("RetroArch"),
    DOLPHIN("Dolphin"),
    MELONDS("melonDS"),
    PPSSPP_GOLD("PPSSPP Gold"),
    EDEN("Eden (Switch)"),
    NETHERSX2("NetherSX2 (PS2)"),
    HAKUX("hakuX (Xbox)")
}

private enum class ShortcutIconMode {
    STRETCH,
    SCALE
}

private const val THUMBNAIL_CACHE_DIR = "thumbnail_cache"
private const val THUMBNAIL_BASE_URL = "https://thumbnails.libretro.com/"
private const val THUMBNAIL_CACHE_VERSION = "v4"
private const val ACTION_BULK_PIN_CONFIRMED = "com.romshortcutmaker.action.BULK_PIN_CONFIRMED"
private const val EDEN_PACKAGE_NAME = "dev.eden.eden_emulator"
private const val EDEN_ACTIVITY_NAME = "org.yuzu.yuzu_emu.activities.EmulationActivity"
private const val NETHERSX2_PACKAGE_NAME = "xyz.aethersx2.android"
private const val NETHERSX2_ACTIVITY_NAME = "xyz.aethersx2.android.EmulationActivity"
private const val HAKUX_PACKAGE_NAME = "com.rfandango.haku_x"
private const val HAKUX_ACTIVITY_NAME = "com.rfandango.haku_x.LauncherActivity"
internal const val MAIN_ACTIVITY_FACADE_ANCHOR = "MainActivityKt"
private val MANUAL_THUMBNAIL_DATABASE_OPTIONS = listOf(
    "Nintendo - Game Boy",
    "Nintendo - Game Boy Advance",
    "Nintendo - Game Boy Color",
    "Nintendo - GameCube",
    "Nintendo - Nintendo 3DS",
    "Nintendo - Nintendo 64",
    "Nintendo - Nintendo DS",
    "Nintendo - Nintendo Entertainment System",
    "Nintendo - Super Nintendo Entertainment System",
    "Nintendo - Virtual Boy",
    "Nintendo - Wii",
    "Nintendo - Wii U"
)

private data class ThumbnailListingEntry(
    val imageName: String,
    val normalizedName: String,
    val comparisonKey: String
)

private data class RomSelection(
    val shortcutName: String,
    val path: String,
    val uri: Uri?
)

private val cachedThumbnailListings = mutableMapOf<String, List<ThumbnailListingEntry>>()

private object BulkShortcutQueue {
    private var active = false
    private var queue: List<RomSelection> = emptyList()
    private var index = 0
    private var shortcutTarget = ShortcutTarget.RETROARCH
    private var corePath = ""
    private var retroArchPackage = "com.retroarch.aarch64"
    private var selectedCorePreset: CorePreset? = null
    private var selectedThumbnailDatabaseOverride: String? = null
    private var shortcutIconMode = ShortcutIconMode.STRETCH
    private var edenPackageName = EDEN_PACKAGE_NAME
    private var edenActivityName = EDEN_ACTIVITY_NAME
    private var netherSx2PackageName = NETHERSX2_PACKAGE_NAME
    private var netherSx2ActivityName = NETHERSX2_ACTIVITY_NAME

    @Synchronized
    fun start(
        queue: List<RomSelection>,
        shortcutTarget: ShortcutTarget,
        corePath: String,
        retroArchPackage: String,
        edenPackageName: String,
        edenActivityName: String,
        netherSx2PackageName: String,
        netherSx2ActivityName: String,
        selectedCorePreset: CorePreset?,
        selectedThumbnailDatabaseOverride: String?,
        shortcutIconMode: ShortcutIconMode
    ) {
        this.queue = queue
        this.index = 0
        this.shortcutTarget = shortcutTarget
        this.corePath = corePath
        this.retroArchPackage = retroArchPackage
        this.edenPackageName = edenPackageName
        this.edenActivityName = edenActivityName
        this.netherSx2PackageName = netherSx2PackageName
        this.netherSx2ActivityName = netherSx2ActivityName
        this.selectedCorePreset = selectedCorePreset
        this.selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride
        this.shortcutIconMode = shortcutIconMode
        this.active = queue.isNotEmpty()
    }

    @Synchronized
    fun stop() {
        active = false
        queue = emptyList()
        index = 0
    }

    @Synchronized
    fun isActive(): Boolean = active

    suspend fun requestNext(context: Context, pinConfirmationIntentSender: IntentSender?): String {
        val snapshot = synchronized(this) {
            if (!active || index >= queue.size) {
                active = false
                return "Bulk queue finished."
            }

            val currentSelection = queue[index]
            val currentIndex = index
            val total = queue.size
            index += 1
            if (index >= queue.size) {
                active = false
            }

            RequestSnapshot(
                selection = currentSelection,
                currentIndex = currentIndex,
                total = total,
                shortcutTarget = shortcutTarget,
                corePath = corePath,
                retroArchPackage = retroArchPackage,
                edenPackageName = edenPackageName,
                edenActivityName = edenActivityName,
                netherSx2PackageName = netherSx2PackageName,
                netherSx2ActivityName = netherSx2ActivityName,
                selectedCorePreset = selectedCorePreset,
                selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride,
                shortcutIconMode = shortcutIconMode
            )
        }

        val derivedName = snapshot.selection.shortcutName
            .ifBlank { buildShortcutNameFromRomPath(snapshot.selection.path) }
            .ifBlank { "ROM ${snapshot.currentIndex + 1}" }

        val result = createPinnedShortcut(
            context = context,
            shortcutName = derivedName,
            romPath = snapshot.selection.path,
            romUri = snapshot.selection.uri,
            shortcutTarget = snapshot.shortcutTarget,
            corePath = snapshot.corePath,
            retroArchPackage = snapshot.retroArchPackage,
            edenPackageName = snapshot.edenPackageName,
            edenActivityName = snapshot.edenActivityName,
            netherSx2PackageName = snapshot.netherSx2PackageName,
            netherSx2ActivityName = snapshot.netherSx2ActivityName,
            selectedCorePreset = snapshot.selectedCorePreset,
            selectedThumbnailDatabaseOverride = snapshot.selectedThumbnailDatabaseOverride,
            shortcutIconMode = snapshot.shortcutIconMode,
            pinConfirmationIntentSender = pinConfirmationIntentSender
        )

        val progress = "${snapshot.currentIndex + 1}/${snapshot.total}"
        return if (snapshot.currentIndex + 1 >= snapshot.total) {
            "$result Finished bulk queue ($progress)."
        } else {
            "$result Queue progress: $progress. Confirm launcher prompt to continue automatically."
        }
    }

    private data class RequestSnapshot(
        val selection: RomSelection,
        val currentIndex: Int,
        val total: Int,
        val shortcutTarget: ShortcutTarget,
        val corePath: String,
        val retroArchPackage: String,
        val edenPackageName: String,
        val edenActivityName: String,
        val netherSx2PackageName: String,
        val netherSx2ActivityName: String,
        val selectedCorePreset: CorePreset?,
        val selectedThumbnailDatabaseOverride: String?,
        val shortcutIconMode: ShortcutIconMode
    )
}

class BulkPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_BULK_PIN_CONFIRMED || !BulkShortcutQueue.isActive()) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            val appContext = context.applicationContext
            val result = BulkShortcutQueue.requestNext(
                context = appContext,
                pinConfirmationIntentSender = buildBulkPinConfirmationIntentSender(appContext)
            )
            Toast.makeText(appContext, result, Toast.LENGTH_SHORT).show()
            pendingResult.finish()
        }
    }
}

private fun buildBulkPinConfirmationIntentSender(context: Context): IntentSender {
    val callbackIntent = Intent(context, BulkPinResultReceiver::class.java).apply {
        action = ACTION_BULK_PIN_CONFIRMED
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        1001,
        callbackIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return pendingIntent.intentSender
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RomShortcutMakerTheme {
                ShortcutBuilderScreen()
            }
        }
    }
}

@Composable
private fun ShortcutBuilderScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val defaultCorePreset = COMMON_CORES.first()
    var shortcutName by remember { mutableStateOf("Super Metroid") }
    var romPath by remember { mutableStateOf("/storage/emulated/0/ROMs/super_metroid.sfc") }
    var romUri by remember { mutableStateOf<Uri?>(null) }
    var shortcutTarget by remember { mutableStateOf(ShortcutTarget.RETROARCH) }
    var retroArchPackage by remember { mutableStateOf("com.retroarch.aarch64") }
    var edenPackageName by remember { mutableStateOf(EDEN_PACKAGE_NAME) }
    var edenActivityName by remember { mutableStateOf(EDEN_ACTIVITY_NAME) }
    var netherSx2PackageName by remember { mutableStateOf(NETHERSX2_PACKAGE_NAME) }
    var netherSx2ActivityName by remember { mutableStateOf(NETHERSX2_ACTIVITY_NAME) }
    var netherSx2BootExtraKey by remember { mutableStateOf("bootPath") }
    var corePath by remember { mutableStateOf(defaultCorePreset.resolveCorePath(retroArchPackage)) }
    var selectedCorePreset by remember { mutableStateOf<CorePreset?>(defaultCorePreset) }
    var selectedThumbnailDatabaseOverride by remember { mutableStateOf<String?>(null) }
    var shortcutIconMode by remember { mutableStateOf(ShortcutIconMode.STRETCH) }
    var selectedRoms by remember { mutableStateOf<List<RomSelection>>(emptyList()) }
    var pendingBulkRoms by remember { mutableStateOf<List<RomSelection>>(emptyList()) }
    var pendingBulkIndex by remember { mutableStateOf(0) }
    var autoAdvanceBulk by remember { mutableStateOf(false) }
    var isPreloadingBoxArt by remember { mutableStateOf(false) }
    var preloadCompletedCount by remember { mutableStateOf(0) }
    var preloadTotalCount by remember { mutableStateOf(0) }
    var preloadFoundCount by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Create a pinned shortcut that starts your selected game in the chosen emulator.") }
    var showCoreSelector by remember { mutableStateOf(false) }
    var showThumbnailDatabaseSelector by remember { mutableStateOf(false) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }
            val romSelections = uris.map { uri ->
                val path = FilePickerHelper.uriToPath(context, uri) ?: uri.toString()
                RomSelection(
                    shortcutName = buildShortcutNameFromRomPath(path),
                    path = path,
                    uri = uri
                )
            }
            selectedRoms = romSelections
                pendingBulkRoms = emptyList()
                pendingBulkIndex = 0
            val first = romSelections.first()
            romPath = first.path
            romUri = first.uri
            if (romSelections.size == 1) {
                shortcutName = first.shortcutName
            }
            val inferredCorePreset = if (shortcutTarget == ShortcutTarget.RETROARCH) {
                inferCorePresetFromRomFolder(first.path)
            } else {
                null
            }
            if (inferredCorePreset != null) {
                selectedCorePreset = inferredCorePreset
                corePath = inferredCorePreset.resolveCorePath(retroArchPackage)
                message = "Selected ${romSelections.size} ROM file(s). Auto core: ${inferredCorePreset.name}."
            } else {
                message = "Selected ${romSelections.size} ROM file(s)."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF111827),
                        Color(0xFF1F2937),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Retro Shortcut Maker",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Create Android home-screen shortcuts that launch games in RetroArch, Dolphin, melonDS, PPSSPP Gold, Eden, or NetherSX2.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE5E7EB)
            )

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF111827).copy(alpha = 0.9f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Shortcut target", color = Color.White, fontWeight = FontWeight.SemiBold)
                    var targetMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { targetMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(shortcutTarget.displayName)
                        }
                        DropdownMenu(
                            expanded = targetMenuExpanded,
                            onDismissRequest = { targetMenuExpanded = false }
                        ) {
                            ShortcutTarget.entries.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target.displayName) },
                                    onClick = {
                                        shortcutTarget = target
                                        if (target != ShortcutTarget.RETROARCH) {
                                            selectedCorePreset = null
                                            showCoreSelector = false
                                        }
                                        message = "${target.displayName} shortcut mode selected."
                                        targetMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val isBulkMode = selectedRoms.size > 1

                    if (!isBulkMode) {
                        OutlinedTextField(
                            value = shortcutName,
                            onValueChange = { shortcutName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Shortcut name") },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = romPath,
                            onValueChange = {
                                romPath = it
                                romUri = null
                                pendingBulkRoms = emptyList()
                                pendingBulkIndex = 0
                                selectedRoms = if (it.isBlank()) {
                                    emptyList()
                                } else {
                                    listOf(
                                        RomSelection(
                                            shortcutName = buildShortcutNameFromRomPath(it),
                                            path = it,
                                            uri = null
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ROM path") },
                            singleLine = true
                        )
                    } else {
                        Text(
                            text = "Selected ROMs: ${selectedRoms.size} (bulk mode)",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )

                        selectedRoms.forEachIndexed { index, selection ->
                            OutlinedTextField(
                                value = selection.shortcutName,
                                onValueChange = { updatedName ->
                                    selectedRoms = selectedRoms.mapIndexed { listIndex, item ->
                                        if (listIndex == index) item.copy(shortcutName = updatedName) else item
                                    }
                                    pendingBulkRoms = emptyList()
                                    pendingBulkIndex = 0
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Shortcut name ${index + 1}") },
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = selection.path,
                                onValueChange = { updatedPath ->
                                    selectedRoms = selectedRoms.mapIndexed { listIndex, item ->
                                        if (listIndex == index) item.copy(path = updatedPath, uri = null) else item
                                    }
                                    pendingBulkRoms = emptyList()
                                    pendingBulkIndex = 0
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("ROM path ${index + 1}") },
                                singleLine = true
                            )
                        }

                        if (isPreloadingBoxArt) {
                            val percent = if (preloadTotalCount > 0) {
                                (preloadCompletedCount * 100) / preloadTotalCount
                            } else {
                                0
                            }
                            Text(
                                text = "Preloading box art: $preloadCompletedCount/$preloadTotalCount ($percent%)",
                                color = Color(0xFFFCD34D),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Matches found: $preloadFoundCount",
                                color = Color(0xFFFCD34D),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text(
                            text = "Auto-next after launcher confirmation: ${if (autoAdvanceBulk) "On" else "Off"}",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (shortcutTarget == ShortcutTarget.RETROARCH) {
                        val autoFolderDatabase = inferThumbnailDatabaseFromRomFolder(romPath)
                        val effectiveDatabase = selectedThumbnailDatabaseOverride ?: autoFolderDatabase

                        OutlinedTextField(
                            value = corePath,
                            onValueChange = {
                                corePath = it
                                selectedCorePreset = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Libretro core path") },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = retroArchPackage,
                            onValueChange = {
                                retroArchPackage = it
                                selectedCorePreset?.let { preset ->
                                    corePath = preset.resolveCorePath(it)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("RetroArch package name") },
                            singleLine = true
                        )

                        Text(
                            text = "Auto database (ROM folder): ${autoFolderDatabase ?: "Not detected"}",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Selected database: ${effectiveDatabase ?: "Auto (core-based fallback)"}",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (shortcutTarget == ShortcutTarget.DOLPHIN) {
                        Text(
                            text = "Dolphin uses package org.dolphinemu.dolphinemu and activity org.dolphinemu.dolphinemu.ui.main.MainActivity.",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (shortcutTarget == ShortcutTarget.MELONDS) {
                        Text(
                            text = "melonDS uses package me.magnum.melonds and activity me.magnum.melonds.ui.emulator.EmulatorActivity. Launches with action me.magnum.melonds.LAUNCH_ROM, sets ROM as data URI, and passes it as 'uri' extra.",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (shortcutTarget == ShortcutTarget.PPSSPP_GOLD) {
                        Text(
                            text = "PPSSPP Gold uses package org.ppsspp.ppssppgold. Launches with ACTION_VIEW and sets the selected ROM as data URI.",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (shortcutTarget == ShortcutTarget.EDEN) {
                        OutlinedTextField(
                            value = edenPackageName,
                            onValueChange = { edenPackageName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Eden package name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = edenActivityName,
                            onValueChange = { edenActivityName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Eden activity class") },
                            singleLine = true
                        )
                        Text(
                            text = "Eden uses ACTION_VIEW with the ROM content URI (application/octet-stream) targeting EmulationActivity. Nightly builds use package dev.eden.eden_nightly.",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (shortcutTarget == ShortcutTarget.NETHERSX2) {
                        OutlinedTextField(
                            value = netherSx2PackageName,
                            onValueChange = { netherSx2PackageName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("NetherSX2 package name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = netherSx2ActivityName,
                            onValueChange = { netherSx2ActivityName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("NetherSX2 activity class") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = netherSx2BootExtraKey,
                            onValueChange = { netherSx2BootExtraKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("NetherSX2 boot extra key (try: bootPath, gamePath, filePath, romPath, path)") },
                            singleLine = true
                        )
                        Text(
                            text = "NetherSX2 uses ACTION_VIEW with a '$netherSx2BootExtraKey' extra containing the ROM file path. Patched builds target EmulationActivity; older builds may need MainActivity. Use Test Launch to find the right combination.",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (shortcutTarget == ShortcutTarget.HAKUX) {
                        Text(
                            text = "hakuX uses package com.rfandango.haku_x and activity com.rfandango.haku_x.LauncherActivity. Launches with ACTION_VIEW and the selected ROM as data URI. hakuX setup must be completed (a games folder added) for direct game launches.",
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text(text = "Icon image mode", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .clickable { shortcutIconMode = ShortcutIconMode.STRETCH }
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = shortcutIconMode == ShortcutIconMode.STRETCH,
                                onClick = { shortcutIconMode = ShortcutIconMode.STRETCH }
                            )
                            Text(text = "Stretch", color = Color(0xFFF9FAFB))
                        }
                        Row(
                            modifier = Modifier
                                .clickable { shortcutIconMode = ShortcutIconMode.SCALE }
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = shortcutIconMode == ShortcutIconMode.SCALE,
                                onClick = { shortcutIconMode = ShortcutIconMode.SCALE }
                            )
                            Text(text = "Scale", color = Color(0xFFF9FAFB))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(if (shortcutTarget == ShortcutTarget.RETROARCH) 1f else 2f)
                        ) {
                            Text("Pick ROM")
                        }

                        if (shortcutTarget == ShortcutTarget.RETROARCH) {
                            Button(
                                onClick = {
                                    showCoreSelector = !showCoreSelector
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pick Core")
                            }

                            Button(
                                onClick = {
                                    showThumbnailDatabaseSelector = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pick DB")
                            }
                        }

                        if (isBulkMode) {
                            Button(
                                onClick = {
                                    if (isPreloadingBoxArt) return@Button
                                    isPreloadingBoxArt = true
                                    preloadCompletedCount = 0
                                    preloadFoundCount = 0
                                    preloadTotalCount = selectedRoms.size
                                    message = "Preloading box art for ${selectedRoms.size} ROM(s)..."
                                    coroutineScope.launch {
                                        val (availableCount, total) = prefetchBulkThumbnails(
                                            context = context,
                                            selections = selectedRoms,
                                            shortcutTarget = shortcutTarget,
                                            corePath = corePath.trim(),
                                            selectedCorePreset = selectedCorePreset,
                                            selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride,
                                            onProgress = { completed, queueTotal, found ->
                                                preloadCompletedCount = completed
                                                preloadTotalCount = queueTotal
                                                preloadFoundCount = found
                                                message = "Preparing box art... $completed/$queueTotal complete ($found found)"
                                            }
                                        )
                                        isPreloadingBoxArt = false
                                        message = "Box art ready for $availableCount/$total ROM(s). Create Next Shortcut will now be faster."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isPreloadingBoxArt) "Preloading..." else "Preload Art")
                            }

                            TextButton(
                                onClick = {
                                    autoAdvanceBulk = !autoAdvanceBulk
                                    message = if (autoAdvanceBulk) {
                                        "Auto-next enabled. Confirm each launcher prompt and the app will request the next shortcut automatically."
                                    } else {
                                        "Auto-next disabled. Tap Create Next Shortcut manually."
                                    }
                                }
                            ) {
                                Text(if (autoAdvanceBulk) "Auto Next: On" else "Auto Next: Off")
                            }
                        }

                        TextButton(
                            onClick = {
                                shortcutName = "Super Metroid"
                                romPath = "/storage/emulated/0/ROMs/super_metroid.sfc"
                                romUri = null
                                shortcutTarget = ShortcutTarget.RETROARCH
                                retroArchPackage = "com.retroarch.aarch64"
                                edenPackageName = EDEN_PACKAGE_NAME
                                edenActivityName = EDEN_ACTIVITY_NAME
                                netherSx2PackageName = NETHERSX2_PACKAGE_NAME
                                netherSx2ActivityName = NETHERSX2_ACTIVITY_NAME
                                selectedCorePreset = defaultCorePreset
                                corePath = defaultCorePreset.resolveCorePath("com.retroarch.aarch64")
                                selectedThumbnailDatabaseOverride = null
                                shortcutIconMode = ShortcutIconMode.STRETCH
                                selectedRoms = emptyList()
                                pendingBulkRoms = emptyList()
                                pendingBulkIndex = 0
                                preloadCompletedCount = 0
                                preloadTotalCount = 0
                                preloadFoundCount = 0
                                autoAdvanceBulk = false
                                BulkShortcutQueue.stop()
                                message = "Fields reset to default example values."
                                showCoreSelector = false
                                showThumbnailDatabaseSelector = false
                            }
                        ) {
                            Text("Reset")
                        }

                    }

                    Text(
                        text = "Box art lookup searches the online thumbnail database directly.",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = {
                            val intent = buildLaunchIntent(
                                context, shortcutTarget, romPath.trim(), romUri, corePath.trim(),
                                retroArchPackage.trim(), edenPackageName.trim(), edenActivityName.trim(),
                                netherSx2PackageName.trim(), netherSx2ActivityName.trim(), netherSx2BootExtraKey.trim()
                            )
                            if (intent == null) {
                                message = "Cannot build launch intent (emulator not installed?)"
                            } else {
                                try {
                                    context.startActivity(intent)
                                    message = "Test launch sent to ${shortcutTarget.displayName}."
                                } catch (e: Exception) {
                                    message = "Test launch failed: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Launch")
                    }

                    Button(
                        onClick = {
                            message = if (selectedRoms.size > 1) "Creating shortcuts..." else "Creating shortcut..."
                            coroutineScope.launch {
                                if (selectedRoms.size <= 1) {
                                    BulkShortcutQueue.stop()
                                    val resolvedSelection = selectedRoms.firstOrNull()
                                        ?: RomSelection(
                                            shortcutName = shortcutName.trim(),
                                            path = romPath.trim(),
                                            uri = romUri
                                        )
                                    val resolvedName = shortcutName.trim().ifBlank {
                                        resolvedSelection.shortcutName.ifBlank {
                                            buildShortcutNameFromRomPath(resolvedSelection.path)
                                        }
                                    }
                                    val result = createPinnedShortcut(
                                        context = context,
                                        shortcutName = resolvedName,
                                        romPath = resolvedSelection.path,
                                        romUri = resolvedSelection.uri,
                                        shortcutTarget = shortcutTarget,
                                        corePath = corePath.trim(),
                                        retroArchPackage = retroArchPackage.trim(),
                                        edenPackageName = edenPackageName.trim(),
                                        edenActivityName = edenActivityName.trim(),
                                        netherSx2PackageName = netherSx2PackageName.trim(),
                                        netherSx2ActivityName = netherSx2ActivityName.trim(),
                                        netherSx2BootExtraKey = netherSx2BootExtraKey.trim(),
                                        selectedCorePreset = selectedCorePreset,
                                        selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride,
                                        shortcutIconMode = shortcutIconMode
                                    )
                                    message = result
                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                } else {
                                    if (autoAdvanceBulk) {
                                        pendingBulkRoms = selectedRoms
                                        pendingBulkIndex = 0

                                        val queue = pendingBulkRoms
                                        if (queue.isEmpty()) {
                                            message = "Select ROM files first."
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        BulkShortcutQueue.start(
                                            queue = queue,
                                            shortcutTarget = shortcutTarget,
                                            corePath = corePath.trim(),
                                            retroArchPackage = retroArchPackage.trim(),
                                            edenPackageName = edenPackageName.trim(),
                                            edenActivityName = edenActivityName.trim(),
                                            netherSx2PackageName = netherSx2PackageName.trim(),
                                            netherSx2ActivityName = netherSx2ActivityName.trim(),
                                            selectedCorePreset = selectedCorePreset,
                                            selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride,
                                            shortcutIconMode = shortcutIconMode
                                        )

                                        val result = BulkShortcutQueue.requestNext(
                                            context = context,
                                            pinConfirmationIntentSender = buildBulkPinConfirmationIntentSender(context)
                                        )
                                        pendingBulkIndex = 1
                                        val combined = "$result Follow launcher prompts to continue auto-next."
                                        message = combined
                                        Toast.makeText(context, combined, Toast.LENGTH_LONG).show()
                                        return@launch
                                    }

                                    if (pendingBulkRoms != selectedRoms || pendingBulkIndex >= pendingBulkRoms.size) {
                                        pendingBulkRoms = selectedRoms
                                        pendingBulkIndex = 0
                                    }

                                    val queue = pendingBulkRoms
                                    if (queue.isEmpty()) {
                                        message = "Select ROM files first."
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        return@launch
                                    }

                                    val index = pendingBulkIndex.coerceIn(0, queue.lastIndex)
                                    val selection = queue[index]
                                    val derivedName = selection.shortcutName
                                        .ifBlank { buildShortcutNameFromRomPath(selection.path) }
                                        .ifBlank { "ROM ${index + 1}" }
                                    val result = createPinnedShortcut(
                                        context = context,
                                        shortcutName = derivedName,
                                        romPath = selection.path,
                                        romUri = selection.uri,
                                        shortcutTarget = shortcutTarget,
                                        corePath = corePath.trim(),
                                        retroArchPackage = retroArchPackage.trim(),
                                        edenPackageName = edenPackageName.trim(),
                                        edenActivityName = edenActivityName.trim(),
                                        netherSx2PackageName = netherSx2PackageName.trim(),
                                        netherSx2ActivityName = netherSx2ActivityName.trim(),
                                        selectedCorePreset = selectedCorePreset,
                                        selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride,
                                        shortcutIconMode = shortcutIconMode
                                    )

                                    val nextIndex = index + 1
                                    pendingBulkIndex = nextIndex
                                    val progress = "$nextIndex/${queue.size}"
                                    val combined = if (nextIndex >= queue.size) {
                                        pendingBulkRoms = emptyList()
                                        pendingBulkIndex = 0
                                        "$result Finished bulk queue ($progress)."
                                    } else {
                                        "$result Queue progress: $progress. Tap Create Next Shortcut after confirming this popup."
                                    }
                                    message = combined
                                    Toast.makeText(context, combined, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val buttonLabel = if (selectedRoms.size > 1) {
                            if (autoAdvanceBulk) {
                                "Create + Auto Next"
                            } else {
                            val total = if (pendingBulkRoms == selectedRoms && pendingBulkRoms.isNotEmpty()) pendingBulkRoms.size else selectedRoms.size
                            val next = if (pendingBulkRoms == selectedRoms && pendingBulkRoms.isNotEmpty()) {
                                (pendingBulkIndex + 1).coerceAtMost(total)
                            } else {
                                1
                            }
                            "Create Next Shortcut ($next/$total)"
                            }
                        } else {
                            "Create Shortcut"
                        }
                        Text(buttonLabel)
                    }
                }
            }

            ShortcutPreviewCard(
                shortcutName = shortcutName,
                romPath = romPath,
                selectedRoms = selectedRoms,
                pendingBulkRoms = pendingBulkRoms,
                pendingBulkIndex = pendingBulkIndex,
                shortcutTarget = shortcutTarget,
                corePath = corePath,
                retroArchPackage = retroArchPackage,
                edenPackageName = edenPackageName,
                edenActivityName = edenActivityName,
                netherSx2PackageName = netherSx2PackageName,
                netherSx2ActivityName = netherSx2ActivityName,
                selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride,
                dolphinAutoStartFile = FilePickerHelper.buildDolphinAutoStartFile(romUri, romPath)
            )

            if (showCoreSelector && shortcutTarget == ShortcutTarget.RETROARCH) {
                CorePresetsDialog(
                    onDismiss = { showCoreSelector = false },
                    onCoreSelected = { preset ->
                        selectedCorePreset = preset
                        corePath = preset.resolveCorePath(retroArchPackage)
                        message = "Core selected: ${preset.name}"
                        showCoreSelector = false
                    }
                )
            }

            if (showThumbnailDatabaseSelector && shortcutTarget == ShortcutTarget.RETROARCH) {
                ThumbnailDatabaseDialog(
                    currentSelection = selectedThumbnailDatabaseOverride,
                    onDismiss = { showThumbnailDatabaseSelector = false },
                    onAutoSelected = {
                        selectedThumbnailDatabaseOverride = null
                        message = "Using auto database selection."
                        showThumbnailDatabaseSelector = false
                    },
                    onDatabaseSelected = { database ->
                        selectedThumbnailDatabaseOverride = database
                        message = "Thumbnail database selected: $database"
                        showThumbnailDatabaseSelector = false
                    }
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1220).copy(alpha = 0.85f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = message, color = Color(0xFFF9FAFB))
                }
            }
        }
    }
}

@Composable
private fun ShortcutPreviewCard(
    shortcutName: String,
    romPath: String,
    selectedRoms: List<RomSelection>,
    pendingBulkRoms: List<RomSelection>,
    pendingBulkIndex: Int,
    shortcutTarget: ShortcutTarget,
    corePath: String,
    retroArchPackage: String,
    edenPackageName: String,
    edenActivityName: String,
    netherSx2PackageName: String,
    netherSx2ActivityName: String,
    selectedThumbnailDatabaseOverride: String?,
    dolphinAutoStartFile: String?
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.9f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val isBulkMode = selectedRoms.size > 1
            Text(text = "Shortcut preview", color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (isBulkMode) {
                val activeQueue = if (pendingBulkRoms == selectedRoms && pendingBulkRoms.isNotEmpty()) {
                    pendingBulkRoms
                } else {
                    selectedRoms
                }
                val nextIndex = if (activeQueue.isEmpty()) 0 else pendingBulkIndex.coerceAtMost(activeQueue.lastIndex)
                Text(text = "Bulk shortcuts: ${selectedRoms.size}", color = Color(0xFFE5E7EB))
                Text(
                    text = "Next to create: ${nextIndex + 1}/${activeQueue.size}",
                    color = Color(0xFFCBD5E1),
                    style = MaterialTheme.typography.bodySmall
                )

                selectedRoms.forEachIndexed { index, selection ->
                    val displayName = selection.shortcutName.ifBlank { buildShortcutNameFromRomPath(selection.path) }
                    Text(
                        text = "${index + 1}. $displayName",
                        color = Color(0xFFE5E7EB),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "ROM: ${selection.path.takeLast(50)}",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(text = "Title: $shortcutName", color = Color(0xFFE5E7EB))
                Text(text = "ROM: ${romPath.takeLast(50)}", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            }
            Text(text = "Target: ${shortcutTarget.name}", color = Color(0xFFCBD5E1))
            if (shortcutTarget == ShortcutTarget.RETROARCH) {
                val autoFolderDatabase = inferThumbnailDatabaseFromRomFolder(romPath)
                val effectiveDatabase = selectedThumbnailDatabaseOverride ?: autoFolderDatabase ?: "Auto (core-based fallback)"
                Text(text = "Core: ${corePath.takeLast(50)}", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Text(text = "Package: $retroArchPackage", color = Color(0xFFCBD5E1))
                Text(text = "Thumbnail DB: $effectiveDatabase", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            } else if (shortcutTarget == ShortcutTarget.DOLPHIN) {
                Text(text = "Package: org.dolphinemu.dolphinemu", color = Color(0xFFCBD5E1))
                Text(text = "Activity: org.dolphinemu.dolphinemu.ui.main.MainActivity", color = Color(0xFFCBD5E1))
                Text(text = "AutoStartFile: ${dolphinAutoStartFile ?: "Unable to build"}", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            } else if (shortcutTarget == ShortcutTarget.MELONDS) {
                Text(text = "Package: me.magnum.melonds", color = Color(0xFFCBD5E1))
                Text(text = "Activity: me.magnum.melonds.ui.emulator.EmulatorActivity", color = Color(0xFFCBD5E1))
                Text(text = "Action: me.magnum.melonds.LAUNCH_ROM", color = Color(0xFFCBD5E1))
                Text(text = "Data: [ROM uri]", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Text(text = "Extra: uri = [ROM uri]", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            } else if (shortcutTarget == ShortcutTarget.PPSSPP_GOLD) {
                Text(text = "Package: org.ppsspp.ppssppgold", color = Color(0xFFCBD5E1))
                Text(text = "Action: android.intent.action.VIEW", color = Color(0xFFCBD5E1))
                Text(text = "Class: org.ppsspp.ppsspp.PpssppActivity", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Text(text = "Data: [external storage content URI]", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Text(text = "Extra: org.ppsspp.ppsspp.Shortcuts = [same URI]", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            } else if (shortcutTarget == ShortcutTarget.EDEN) {
                Text(text = "Package: $edenPackageName", color = Color(0xFFCBD5E1))
                Text(text = "Activity: $edenActivityName", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Text(text = "Action: android.intent.action.VIEW", color = Color(0xFFCBD5E1))
                Text(text = "Data: [ROM uri]", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            } else if (shortcutTarget == ShortcutTarget.NETHERSX2) {
                Text(text = "Package: $netherSx2PackageName", color = Color(0xFFCBD5E1))
                Text(text = "Activity: $netherSx2ActivityName", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Text(text = "Action: android.intent.action.VIEW", color = Color(0xFFCBD5E1))
                Text(text = "Extra: bootPath = ${buildNetherSx2BootUri(null, romPath).takeLast(60)}", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            } else if (shortcutTarget == ShortcutTarget.HAKUX) {
                Text(text = "Package: $HAKUX_PACKAGE_NAME", color = Color(0xFFCBD5E1))
                Text(text = "Activity: $HAKUX_ACTIVITY_NAME", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Text(text = "Action: android.intent.action.VIEW", color = Color(0xFFCBD5E1))
                Text(text = "Data: [ROM uri]", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ThumbnailDatabaseDialog(
    currentSelection: String?,
    onDismiss: () -> Unit,
    onAutoSelected: () -> Unit,
    onDatabaseSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = "Thumbnail Database",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 420.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.8f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAutoSelected() }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Auto (ROM folder/core)",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (currentSelection == null) "Currently selected" else "Tap to use automatic detection",
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    items(MANUAL_THUMBNAIL_DATABASE_OPTIONS) { database ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.8f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDatabaseSelected(database) }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = database,
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (currentSelection == database) "Currently selected" else "Tap to select",
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun CorePresetsDialog(
    onDismiss: () -> Unit,
    onCoreSelected: (com.romshortcutmaker.data.CorePreset) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = "Available Cores",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(COMMON_CORES) { preset ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.8f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCoreSelected(preset) }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "For: ${preset.systems.joinToString(", ")}",
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun buildLaunchIntent(
    context: Context,
    shortcutTarget: ShortcutTarget,
    romPath: String,
    romUri: Uri?,
    corePath: String,
    retroArchPackage: String,
    edenPackageName: String,
    edenActivityName: String,
    netherSx2PackageName: String,
    netherSx2ActivityName: String,
    netherSx2BootExtraKey: String = "bootPath"
): Intent? {
    return when (shortcutTarget) {
        ShortcutTarget.RETROARCH -> {
            val possiblePackages = listOf(
                retroArchPackage,
                "com.retroarch.aarch64",
                "com.retroarch",
                "com.retroarch.ra32"
            ).distinct()

            var resolvedPackage: String? = null
            for (pkg in possiblePackages) {
                try {
                    context.packageManager.getApplicationInfo(pkg, 0)
                    resolvedPackage = pkg
                    break
                } catch (_: Exception) {
                }
            }
            if (resolvedPackage == null) return null

            val appInfo = context.packageManager.getApplicationInfo(resolvedPackage, 0)
            val externalBase = Environment.getExternalStorageDirectory().absolutePath
            val externalFiles = "$externalBase/Android/data/$resolvedPackage/files"

            Intent().apply {
                action = Intent.ACTION_MAIN
                component = ComponentName(
                    resolvedPackage,
                    "com.retroarch.browser.retroactivity.RetroActivityFuture"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("ROM", romPath)
                putExtra("LIBRETRO", corePath)
                putExtra("CONFIGFILE", "$externalFiles/retroarch.cfg")
                putExtra(
                    "IME",
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                )
                putExtra("DATADIR", appInfo.dataDir)
                putExtra("APK", appInfo.sourceDir)
                putExtra("SDCARD", externalBase)
                putExtra("EXTERNAL", externalFiles)
            }
        }
        ShortcutTarget.DOLPHIN -> {
            Intent().apply {
                action = Intent.ACTION_MAIN
                component = ComponentName(
                    "org.dolphinemu.dolphinemu",
                    "org.dolphinemu.dolphinemu.ui.main.MainActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("AutoStartFile", FilePickerHelper.buildDolphinAutoStartFile(romUri, romPath))
            }
        }
        ShortcutTarget.MELONDS -> {
            val launchUri = when {
                romUri != null -> romUri
                romPath.startsWith("content://", ignoreCase = true) -> Uri.parse(romPath)
                romPath.startsWith("file://", ignoreCase = true) -> Uri.parse(romPath)
                else -> Uri.fromFile(File(romPath))
            }
            Intent().apply {
                action = "me.magnum.melonds.LAUNCH_ROM"
                component = ComponentName(
                    "me.magnum.melonds",
                    "me.magnum.melonds.ui.emulator.EmulatorActivity"
                )
                data = launchUri
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                putExtra("uri", launchUri.toString())
            }
        }
        ShortcutTarget.PPSSPP_GOLD -> {
            val launchUri = buildPpssppLaunchUri(romUri = romUri, romPath = romPath)
            val uriString = launchUri.toString()
            Intent().apply {
                action = Intent.ACTION_VIEW
                component = ComponentName(
                    "org.ppsspp.ppssppgold",
                    "org.ppsspp.ppsspp.PpssppActivity"
                )
                data = launchUri
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                putExtra("org.ppsspp.ppsspp.Shortcuts", uriString)
            }
        }
        ShortcutTarget.EDEN -> {
            val launchUri = buildPpssppLaunchUri(romUri = romUri, romPath = romPath)
            Intent().apply {
                action = Intent.ACTION_VIEW
                component = ComponentName(edenPackageName, edenActivityName)
                // Eden's EmulationActivity intent-filter requires content scheme
                // with application/octet-stream MIME type.
                setDataAndType(launchUri, "application/octet-stream")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        ShortcutTarget.NETHERSX2 -> {
            // NetherSX2 expects bootPath as a tree-style SAF document URI string:
            // content://.../tree/<volume>%3A<folder>/document/<volume>%3A<folder>%2F<file>
            val bootUri = buildNetherSx2BootUri(romUri, romPath)
            debugLog("NetherSX2 launch: $netherSx2BootExtraKey='$bootUri'")
            Intent().apply {
                action = Intent.ACTION_VIEW
                component = ComponentName(netherSx2PackageName, netherSx2ActivityName)
                putExtra(netherSx2BootExtraKey, bootUri)
                data = Uri.parse(bootUri)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        ShortcutTarget.HAKUX -> {
            val launchUri = buildPpssppLaunchUri(romUri = romUri, romPath = romPath)
            Intent().apply {
                action = Intent.ACTION_VIEW
                component = ComponentName(HAKUX_PACKAGE_NAME, HAKUX_ACTIVITY_NAME)
                data = launchUri
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}

private suspend fun createPinnedShortcut(
    context: Context,
    shortcutName: String,
    romPath: String,
    romUri: Uri?,
    shortcutTarget: ShortcutTarget,
    corePath: String,
    retroArchPackage: String,
    edenPackageName: String,
    edenActivityName: String,
    netherSx2PackageName: String,
    netherSx2ActivityName: String,
    netherSx2BootExtraKey: String = "bootPath",
    selectedCorePreset: CorePreset?,
    selectedThumbnailDatabaseOverride: String?,
    shortcutIconMode: ShortcutIconMode,
    pinConfirmationIntentSender: IntentSender? = null
): String {
    if (shortcutName.isBlank()) {
        return "Enter a shortcut name first."
    }

    if (romPath.isBlank()) {
        return "Enter a ROM path first."
    }

    if (shortcutTarget == ShortcutTarget.RETROARCH && corePath.isBlank()) {
        return "Enter a core path first."
    }

    if (shortcutTarget == ShortcutTarget.RETROARCH && retroArchPackage.isBlank()) {
        return "Enter the RetroArch package name first."
    }

    if (shortcutTarget == ShortcutTarget.EDEN && edenPackageName.isBlank()) {
        return "Enter the Eden package name first."
    }

    if (shortcutTarget == ShortcutTarget.EDEN && edenActivityName.isBlank()) {
        return "Enter the Eden activity class first."
    }

    if (shortcutTarget == ShortcutTarget.NETHERSX2 && netherSx2PackageName.isBlank()) {
        return "Enter the NetherSX2 package name first."
    }

    if (shortcutTarget == ShortcutTarget.NETHERSX2 && netherSx2ActivityName.isBlank()) {
        return "Enter the NetherSX2 activity class first."
    }

    try {
        val intent = buildLaunchIntent(
            context, shortcutTarget, romPath, romUri, corePath,
            retroArchPackage, edenPackageName, edenActivityName,
            netherSx2PackageName, netherSx2ActivityName, netherSx2BootExtraKey
        ) ?: return "RetroArch is not installed or package name is incorrect."

        val thumbnailDatabases = when (shortcutTarget) {
            ShortcutTarget.RETROARCH -> {
                resolveRetroArchThumbnailDatabases(
                    selectedCorePreset = selectedCorePreset,
                    corePath = corePath,
                    romPath = romPath,
                    selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride
                )
            }
            ShortcutTarget.DOLPHIN -> {
                listOf("Nintendo - GameCube", "Nintendo - Wii")
            }
            ShortcutTarget.MELONDS -> {
                listOf("Nintendo - Nintendo DS")
            }
            ShortcutTarget.PPSSPP_GOLD -> {
                listOf("Sony - PlayStation Portable")
            }
            ShortcutTarget.EDEN -> {
                listOf("Nintendo - Nintendo Switch")
            }
            ShortcutTarget.NETHERSX2 -> {
                listOf("Sony - PlayStation 2")
            }
            ShortcutTarget.HAKUX -> {
                listOf("Microsoft - Xbox")
            }
        }
        val iconBitmap = if (thumbnailDatabases.isEmpty()) {
            null
        } else {
            downloadBoxArtBitmap(context, romPath, shortcutName, thumbnailDatabases)
        }
        val shortcutIcon = iconBitmap?.let {
            // Use a plain bitmap icon (not adaptive) so the transparent background is preserved;
            // adaptive icons get a launcher-drawn background plate that renders as white.
            IconCompat.createWithBitmap(buildFilledShortcutIconBitmap(it, shortcutIconMode))
        }
            ?: IconCompat.createWithResource(context, android.R.drawable.ic_media_play)

        val romHash = "$romPath|${intent.component?.className}|${intent.extras?.keySet()?.sorted()}".hashCode().toUInt().toString(16)
        val shortcutId = "${shortcutTarget.name.lowercase()}_${shortcutName.trim().replace(" ", "_")}_$romHash"
        val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(shortcutName)
            .setLongLabel("Launch $shortcutName in ${shortcutTarget.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() }}")
            .setIcon(shortcutIcon)
            .setIntent(intent)
            .build()

        // Update icon/intent if a shortcut with this ID already exists.
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcutInfo))

        return if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, pinConfirmationIntentSender)) {
            when {
                shortcutTarget == ShortcutTarget.DOLPHIN && iconBitmap != null -> {
                    "Dolphin shortcut '$shortcutName' created with downloaded box art."
                }
                shortcutTarget == ShortcutTarget.DOLPHIN -> {
                    "Dolphin shortcut '$shortcutName' created. Box art not found, using fallback icon."
                }
                shortcutTarget == ShortcutTarget.MELONDS && iconBitmap != null -> {
                    "melonDS shortcut '$shortcutName' created with downloaded box art."
                }
                shortcutTarget == ShortcutTarget.MELONDS -> {
                    "melonDS shortcut '$shortcutName' created. Box art not found, using fallback icon."
                }
                shortcutTarget == ShortcutTarget.PPSSPP_GOLD && iconBitmap != null -> {
                    "PPSSPP Gold shortcut '$shortcutName' created with downloaded box art."
                }
                shortcutTarget == ShortcutTarget.PPSSPP_GOLD -> {
                    "PPSSPP Gold shortcut '$shortcutName' created. Box art not found, using fallback icon."
                }
                shortcutTarget == ShortcutTarget.EDEN && iconBitmap != null -> {
                    "Eden shortcut '$shortcutName' created with downloaded box art."
                }
                shortcutTarget == ShortcutTarget.EDEN -> {
                    "Eden shortcut '$shortcutName' created. Box art not found, using fallback icon."
                }
                shortcutTarget == ShortcutTarget.NETHERSX2 && iconBitmap != null -> {
                    "NetherSX2 shortcut '$shortcutName' created with downloaded box art."
                }
                shortcutTarget == ShortcutTarget.NETHERSX2 -> {
                    "NetherSX2 shortcut '$shortcutName' created. Box art not found, using fallback icon."
                }
                shortcutTarget == ShortcutTarget.HAKUX && iconBitmap != null -> {
                    "hakuX shortcut '$shortcutName' created with downloaded box art."
                }
                shortcutTarget == ShortcutTarget.HAKUX -> {
                    "hakuX shortcut '$shortcutName' created. Box art not found, using fallback icon."
                }
                iconBitmap != null -> {
                    "Shortcut '$shortcutName' created with downloaded box art."
                }
                else -> {
                    "Shortcut '$shortcutName' created. Box art not found, using fallback icon."
                }
            }
        } else {
            "Shortcut '$shortcutName' created (manual pinning may be required)"
        }
    } catch (e: Exception) {
        return "Error creating shortcut: ${e.message}"
    }
}

private suspend fun downloadBoxArtBitmap(
    context: Context,
    romPath: String,
    titleFallback: String?,
    thumbnailDatabases: List<String>
): Bitmap? {
    return withContext(Dispatchers.IO) {
        val cacheKey = buildThumbnailCacheKey(romPath, titleFallback, thumbnailDatabases)
        loadCachedThumbnailBitmap(context, cacheKey)?.let { cached ->
            return@withContext cached
        }

        val romNameCandidates = buildRomNameCandidates(romPath)
        val titleCandidates = buildTitleCandidates(titleFallback)
        val normalizedCandidates = (romNameCandidates + titleCandidates)
            .map(::normalizeNameForComparison)
            .filter { it.isNotBlank() }
            .distinct()
        val comparisonKeys = normalizedCandidates
            .map(::buildComparisonKey)
            .filter { it.isNotBlank() }
            .distinct()

        fun findMatchingThumbnail(): Bitmap? {
            debugLog("Box art lookup: databases=$thumbnailDatabases candidates=$normalizedCandidates keys=$comparisonKeys")
            for (database in thumbnailDatabases) {
                val listingEntries = loadThumbnailListingEntries(database)
                if (listingEntries.isEmpty()) {
                    continue
                }

                val exactMatches = listingEntries.filter { entry ->
                    comparisonKeys.any { candidateKey ->
                        entry.comparisonKey == candidateKey
                    } || normalizedCandidates.any { candidate ->
                        entry.normalizedName.equals(candidate, ignoreCase = true)
                    }
                }
                val looseMatches = if (exactMatches.isEmpty()) {
                    listingEntries.filter { entry ->
                        comparisonKeys.any { candidateKey ->
                            entry.comparisonKey.contains(candidateKey)
                        }
                    }
                } else {
                    emptyList()
                }

                val matchedEntries = (exactMatches + looseMatches).distinctBy { it.imageName }
                debugLog("[$database] ${listingEntries.size} entries, ${exactMatches.size} exact, ${looseMatches.size} loose matches")
                for (entry in matchedEntries) {
                    val url = buildThumbnailUrl(database, "Named_Boxarts", entry.imageName)
                    downloadBitmap(url)?.let { return it }
                        ?: debugLog("[$database] Download FAILED: $url")
                }
            }
            return null

        }

        val bitmap = findMatchingThumbnail()
        if (bitmap != null) {
            saveCachedThumbnailBitmap(context, cacheKey, bitmap)
        } else {
            // Don't cache misses: a failed lookup (offline, listing fetch
            // timeout, or a database added later) would otherwise be
            // permanently cached for this ROM.
            buildThumbnailCacheFile(context, cacheKey).delete()
        }

        bitmap
    }
}

private suspend fun prefetchBulkThumbnails(
    context: Context,
    selections: List<RomSelection>,
    shortcutTarget: ShortcutTarget,
    corePath: String,
    selectedCorePreset: CorePreset?,
    selectedThumbnailDatabaseOverride: String?,
    onProgress: (completed: Int, total: Int, found: Int) -> Unit = { _, _, _ -> }
): Pair<Int, Int> {
    if (selections.isEmpty()) {
        return 0 to 0
    }

    var availableCount = 0
    val total = selections.size
    onProgress(0, total, 0)
    for ((index, selection) in selections.withIndex()) {
        val thumbnailDatabases = when (shortcutTarget) {
            ShortcutTarget.RETROARCH -> {
                resolveRetroArchThumbnailDatabases(
                    selectedCorePreset = selectedCorePreset,
                    corePath = corePath,
                    romPath = selection.path,
                    selectedThumbnailDatabaseOverride = selectedThumbnailDatabaseOverride
                )
            }
            ShortcutTarget.DOLPHIN -> {
                listOf("Nintendo - GameCube", "Nintendo - Wii")
            }
            ShortcutTarget.MELONDS -> {
                listOf("Nintendo - Nintendo DS")
            }
            ShortcutTarget.PPSSPP_GOLD -> {
                listOf("Sony - PlayStation Portable")
            }
            ShortcutTarget.EDEN -> {
                listOf("Nintendo - Nintendo Switch")
            }
            ShortcutTarget.NETHERSX2 -> {
                listOf("Sony - PlayStation 2")
            }
            ShortcutTarget.HAKUX -> {
                listOf("Microsoft - Xbox")
            }
        }

        if (thumbnailDatabases.isEmpty()) {
            onProgress(index + 1, total, availableCount)
            continue
        }

        val bitmap = downloadBoxArtBitmap(
            context = context,
            romPath = selection.path,
            titleFallback = selection.shortcutName,
            thumbnailDatabases = thumbnailDatabases
        )
        if (bitmap != null) {
            availableCount += 1
        }
        onProgress(index + 1, total, availableCount)
    }

    return availableCount to total
}

private fun loadThumbnailListingEntries(database: String): List<ThumbnailListingEntry> {
    val startTime = System.currentTimeMillis()
    
    cachedThumbnailListings[database]?.let {
        val cacheTime = System.currentTimeMillis() - startTime
        debugLog("[$database] Cache HIT in ${cacheTime}ms, returned ${it.size} entries")
        return it
    }

    debugLog("[$database] Cache MISS, fetching from $THUMBNAIL_BASE_URL")

    val databaseFolder = Uri.encode(database)
    val namedBoxartsUrl = "$THUMBNAIL_BASE_URL$databaseFolder/Named_Boxarts/"
    debugLog("[$database] Constructed URL: $namedBoxartsUrl")

    val fetchStart = System.currentTimeMillis()
    val html = fetchUrlText(namedBoxartsUrl)
    val fetchDuration = System.currentTimeMillis() - fetchStart
    
    if (html == null) {
        debugLog("[$database] Network fetch FAILED after ${fetchDuration}ms (null response)")
        return emptyList()
    }

    debugLog("[$database] Network fetch OK in ${fetchDuration}ms, HTML size: ${html.length} bytes")

    val parseStart = System.currentTimeMillis()
    val allHrefs = parseAnchorHrefs(html)
    val parseDuration = System.currentTimeMillis() - parseStart
    debugLog("[$database] Parsed ${allHrefs.size} hrefs in ${parseDuration}ms")

    val filterStart = System.currentTimeMillis()
    val imageHrefs = allHrefs.filter { href -> 
        href.endsWith(".png", ignoreCase = true) || href.endsWith(".jpg", ignoreCase = true) 
    }
    val filterDuration = System.currentTimeMillis() - filterStart
    debugLog("[$database] Filtered to ${imageHrefs.size} image hrefs in ${filterDuration}ms")

    val decodeStart = System.currentTimeMillis()
    val decodedNames = imageHrefs
        .map { href -> Uri.decode(href.substringAfterLast('/')) }
        .distinct()
    val decodeDuration = System.currentTimeMillis() - decodeStart
    debugLog("[$database] Decoded/deduplicated to ${decodedNames.size} unique names in ${decodeDuration}ms")

    val normStart = System.currentTimeMillis()
    val entries = decodedNames
        .map { imageName ->
            ThumbnailListingEntry(
                imageName = imageName,
                normalizedName = normalizeNameForComparison(imageName.substringBeforeLast('.')),
                comparisonKey = buildComparisonKey(imageName.substringBeforeLast('.'))
            )
        }
        .filter { entry -> entry.normalizedName.isNotBlank() }
    val normDuration = System.currentTimeMillis() - normStart
    debugLog("[$database] Normalized ${entries.size} entries in ${normDuration}ms")

    cachedThumbnailListings[database] = entries
    
    val totalTime = System.currentTimeMillis() - startTime
    debugLog("[$database] COMPLETE: ${entries.size} entries loaded in ${totalTime}ms total (fetch=${fetchDuration}ms, parse=${parseDuration}ms, filter=${filterDuration}ms, decode=${decodeDuration}ms, norm=${normDuration}ms)")
    
    return entries
}

private fun fetchUrlText(url: String): String? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 15000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "RomShortcutMaker")
    }

    return try {
        if (connection.responseCode !in 200..299) {
            null
        } else {
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun parseAnchorHrefs(html: String): List<String> {
    val hrefRegex = Regex("<a\\s+href=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    return hrefRegex.findAll(html)
        .map { it.groupValues[1] }
        .filter { href ->
            href.isNotBlank() &&
                !href.startsWith("../") &&
                !href.startsWith("/") &&
                !href.contains("?") &&
                !href.contains("#")
        }
        .toList()
}

private fun buildThumbnailCacheKey(
    romPath: String,
    titleFallback: String?,
    thumbnailDatabases: List<String>
): String {
    val normalizedTitle = titleFallback?.trim().orEmpty()
    return "$THUMBNAIL_CACHE_VERSION|${thumbnailDatabases.joinToString("|")}|$romPath|$normalizedTitle"
}

private fun buildThumbnailCacheFile(context: Context, cacheKey: String): File {
    val cacheDir = File(context.cacheDir, THUMBNAIL_CACHE_DIR)
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }
    val fileName = cacheKey.hashCode().toUInt().toString(16) + ".png"
    return File(cacheDir, fileName)
}

private fun loadCachedThumbnailBitmap(context: Context, cacheKey: String): Bitmap? {
    val cacheFile = buildThumbnailCacheFile(context, cacheKey)
    if (!cacheFile.exists()) {
        return null
    }
    return try {
        BitmapFactory.decodeFile(cacheFile.absolutePath)
    } catch (_: Exception) {
        null
    }
}

private fun saveCachedThumbnailBitmap(context: Context, cacheKey: String, bitmap: Bitmap) {
    val cacheFile = buildThumbnailCacheFile(context, cacheKey)
    try {
        FileOutputStream(cacheFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    } catch (_: Exception) {
    }
}

private fun buildTitleCandidates(titleFallback: String?): List<String> {
    if (titleFallback == null) return emptyList()

    val baseTitle = normalizeNameForComparison(titleFallback)
    val candidates = mutableListOf<String>()

    // Add the normalized title
    candidates.add(baseTitle)

    // Add variations with underscores replacing spaces
    candidates.add(baseTitle.replace(" ", "_"))

    return candidates
}

private fun buildRomNameCandidates(romPath: String): List<String> {
    val baseName = normalizeNameForComparison(File(romPath).nameWithoutExtension)
    val candidates = mutableListOf<String>()

    // Add the normalized base name
    candidates.add(baseName)

    // Add variations with underscores replacing spaces
    candidates.add(baseName.replace(" ", "_"))

    return candidates
}

private fun extractRomFileName(romPath: String): String {
    val uri = Uri.parse(romPath)
    val uriLastSegment = uri.lastPathSegment
    val candidate = when {
        romPath.startsWith("content://", ignoreCase = true) && !uriLastSegment.isNullOrBlank() -> {
            uriLastSegment.substringAfterLast(':')
        }

        romPath.startsWith("file://", ignoreCase = true) && !uriLastSegment.isNullOrBlank() -> {
            uriLastSegment
        }

        else -> romPath.substringAfterLast('\\').substringAfterLast('/')
    }

    return candidate.substringBeforeLast('.')
}

private fun buildShortcutNameFromRomPath(romPath: String): String {
    val baseName = extractRomFileName(romPath)
    return baseName
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun inferThumbnailDatabase(corePath: String): String? {
    return COMMON_CORES.firstOrNull { preset ->
        corePath.endsWith(preset.coreFileName, ignoreCase = true)
    }?.thumbnailDatabase
}

private fun resolveRetroArchThumbnailDatabases(
    selectedCorePreset: CorePreset?,
    corePath: String,
    romPath: String,
    selectedThumbnailDatabaseOverride: String?
): List<String> {
    if (!selectedThumbnailDatabaseOverride.isNullOrBlank()) {
        val databases = listOf(selectedThumbnailDatabaseOverride)
        debugLog("Resolved thumbnail databases from manual override for '$romPath': $databases")
        return databases
    }

    val folderDatabase = inferThumbnailDatabaseFromRomFolder(romPath)
    if (folderDatabase != null) {
        val databases = listOf(folderDatabase)
        debugLog("Resolved thumbnail databases from ROM folder for '$romPath': $databases")
        return databases
    }

    val primaryDatabase = selectedCorePreset?.thumbnailDatabase ?: inferThumbnailDatabase(corePath)
        ?: return emptyList()

    val isMgbaCore = selectedCorePreset?.coreFileName.equals("mgba_libretro_android.so", ignoreCase = true) ||
        corePath.endsWith("mgba_libretro_android.so", ignoreCase = true)

    val databases = if (!isMgbaCore) {
        listOf(primaryDatabase)
    } else {
        when (extractRomExtension(romPath)) {
            "gb" -> listOf(
                "Nintendo - Game Boy",
                "Nintendo - Game Boy Color",
                primaryDatabase
            )
            "gbc", "cgb" -> listOf(
                "Nintendo - Game Boy Color",
                "Nintendo - Game Boy",
                primaryDatabase
            )
            else -> listOf(
                primaryDatabase,
                "Nintendo - Game Boy",
                "Nintendo - Game Boy Color",
                "Nintendo - Game Boy Advance"
            )
        }.distinct()
    }

    debugLog("Resolved thumbnail databases for '$romPath': $databases")
    return databases
}

private fun inferThumbnailDatabaseFromRomFolder(romPath: String): String? {
    val parentFolderName = extractRomParentFolderName(romPath)

    return when {
        parentFolderName.equals("gbc", ignoreCase = true) -> "Nintendo - Game Boy Color"
        parentFolderName.equals("nes", ignoreCase = true) -> "Nintendo - Nintendo Entertainment System"
        parentFolderName.equals("snes", ignoreCase = true) -> "Nintendo - Super Nintendo Entertainment System"
        parentFolderName.equals("gba", ignoreCase = true) -> "Nintendo - Game Boy Advance"
        parentFolderName.equals("gb", ignoreCase = true) -> "Nintendo - Game Boy"
        else -> null
    }
}

private fun inferCorePresetFromRomFolder(romPath: String): CorePreset? {
    val parentFolderName = extractRomParentFolderName(romPath)
    return when {
        parentFolderName.equals("gb", ignoreCase = true) ||
            parentFolderName.equals("gbc", ignoreCase = true) ||
            parentFolderName.equals("gba", ignoreCase = true) -> {
            COMMON_CORES.firstOrNull { it.coreFileName.equals("mgba_libretro_android.so", ignoreCase = true) }
        }
        parentFolderName.equals("nes", ignoreCase = true) -> {
            COMMON_CORES.firstOrNull { it.coreFileName.equals("nestopia_libretro_android.so", ignoreCase = true) }
        }
        parentFolderName.equals("snes", ignoreCase = true) -> {
            COMMON_CORES.firstOrNull { it.coreFileName.equals("snes9x_libretro_android.so", ignoreCase = true) }
        }
        parentFolderName.equals("n64", ignoreCase = true) -> {
            COMMON_CORES.firstOrNull { it.coreFileName.equals("mupen64plus_next_gles3_libretro_android.so", ignoreCase = true) }
        }
        parentFolderName.equals("gc", ignoreCase = true) ||
            parentFolderName.equals("gamecube", ignoreCase = true) ||
            parentFolderName.equals("wii", ignoreCase = true) -> {
            COMMON_CORES.firstOrNull { it.coreFileName.equals("dolphin_libretro.so", ignoreCase = true) }
        }
        parentFolderName.equals("psp", ignoreCase = true) -> {
            COMMON_CORES.firstOrNull { it.coreFileName.equals("ppsspp_libretro.so", ignoreCase = true) }
        }
        else -> null
    }
}

private fun extractRomParentFolderName(romPath: String): String {
    return run {
        val fileParent = File(romPath).parentFile?.name
        if (!fileParent.isNullOrBlank()) {
            fileParent
        } else {
            val uri = Uri.parse(romPath)
            val lastSegment = uri.lastPathSegment.orEmpty()
            val parts = lastSegment.split('/')
            if (parts.size >= 2) parts[parts.size - 2] else ""
        }
    }.trim()
}

private fun extractRomExtension(romPath: String): String {
    val uri = Uri.parse(romPath)
    val uriLastSegment = uri.lastPathSegment
    val candidate = when {
        romPath.startsWith("content://", ignoreCase = true) && !uriLastSegment.isNullOrBlank() -> {
            uriLastSegment.substringAfterLast(':')
        }
        romPath.startsWith("file://", ignoreCase = true) && !uriLastSegment.isNullOrBlank() -> {
            uriLastSegment
        }
        else -> romPath.substringAfterLast('\\').substringAfterLast('/')
    }

    return candidate.substringAfterLast('.', "").lowercase()
}

private fun buildNetherSx2BootUri(romUri: Uri?, romPath: String): String {
    // Prefer the real SAF document URI from the picker; NetherSX2 resolves
    // these via its own SAF permissions for the containing folder.
    if (romUri != null && romUri.scheme.equals("content", ignoreCase = true)) {
        return romUri.toString()
    }
    if (romPath.startsWith("content://", ignoreCase = true)) {
        return romPath
    }

    // Build the tree-style document URI NetherSX2's to_document_uri() produces:
    // content://com.android.externalstorage.documents/tree/<volume>%3A<folder>/document/<volume>%3A<folder>%2F<file>
    val normalizedPath = romPath.replace('\\', '/').trim()
    if (normalizedPath.startsWith("/storage/", ignoreCase = true)) {
        val storageRemainder = normalizedPath.substringAfter("/storage/")
        val driveId = storageRemainder.substringBefore('/')
        val relativePath = storageRemainder.substringAfter('/', "")
        if (driveId.isNotBlank() && relativePath.isNotBlank()) {
            val volumeId = if (driveId.equals("emulated", ignoreCase = true)) "primary" else driveId
            val folder = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val treeDocId = if (folder.isBlank()) "$volumeId:" else "$volumeId:$folder"
            val docId = "$volumeId:$relativePath"
            return "content://com.android.externalstorage.documents/tree/${Uri.encode(treeDocId)}/document/${Uri.encode(docId)}"
        }
    }

    return romPath
}

private fun buildPpssppLaunchUri(romUri: Uri?, romPath: String): Uri {
    if (romUri != null && romUri.scheme.equals("content", ignoreCase = true)) {
        return romUri
    }

    if (romPath.startsWith("content://", ignoreCase = true)) {
        return Uri.parse(romPath)
    }

    if (romPath.startsWith("file://", ignoreCase = true)) {
        return Uri.parse(romPath)
    }

    val normalizedPath = romPath.replace('\\', '/').trim()
    if (normalizedPath.startsWith("/storage/", ignoreCase = true)) {
        val storageRemainder = normalizedPath.substringAfter("/storage/")
        val driveId = storageRemainder.substringBefore('/')
        val relativePath = storageRemainder.substringAfter('/', "")

        if (driveId.isNotBlank()) {
            val volumeId = if (driveId.equals("emulated", ignoreCase = true)) {
                "primary"
            } else {
                driveId
            }
            val documentId = if (relativePath.isBlank()) {
                "$volumeId:"
            } else {
                "$volumeId:$relativePath"
            }
            val encodedDocumentId = Uri.encode(documentId)
            return Uri.parse("content://com.android.externalstorage.documents/document/$encodedDocumentId")
        }
    }

    return Uri.fromFile(File(romPath))
}

private fun buildThumbnailUrl(
    database: String,
    folder: String,
    imageName: String
): String {
    val encodedDatabase = Uri.encode(database)
    val encodedFolder = Uri.encode(folder)
    val encodedImageName = Uri.encode(imageName)
    return "$THUMBNAIL_BASE_URL$encodedDatabase/$encodedFolder/$encodedImageName"
}

private fun buildFilledShortcutIconBitmap(source: Bitmap, shortcutIconMode: ShortcutIconMode): Bitmap {
    val iconSize = 220
    val output = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawARGB(0, 0, 0, 0)

    val destination = when (shortcutIconMode) {
        ShortcutIconMode.STRETCH -> RectF(0f, 0f, iconSize.toFloat(), iconSize.toFloat())
        ShortcutIconMode.SCALE -> {
            val scale = minOf(iconSize / source.width.toFloat(), iconSize / source.height.toFloat())
            val scaledWidth = source.width * scale
            val scaledHeight = source.height * scale
            val left = (iconSize - scaledWidth) / 2f
            val top = (iconSize - scaledHeight) / 2f
            RectF(left, top, left + scaledWidth, top + scaledHeight)
        }
    }

    canvas.drawBitmap(source, null, destination, paint)
    return output
}

private fun downloadBitmap(url: String): Bitmap? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 10000
        instanceFollowRedirects = true
    }

    return try {
        connection.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun debugLog(message: String) {
    println("[DEBUG] $message")
}

private fun normalizeNameForComparison(name: String): String {
    return name
        .replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), " ")
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun buildComparisonKey(name: String): String {
    return normalizeNameForComparison(name)
        .split(" - ")
        .map(::normalizeArticleOrder)
        .map(::removeLeadingArticle)
        .joinToString(" ")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")
}

private fun normalizeArticleOrder(segment: String): String {
    val trimmed = segment.trim()
    val trailingArticleMatch = Regex("^(.*),\\s*(the|a|an)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
    return if (trailingArticleMatch != null) {
        val article = trailingArticleMatch.groupValues[2]
        val body = trailingArticleMatch.groupValues[1].trim()
        "$article $body"
    } else {
        trimmed
    }
}

private fun removeLeadingArticle(segment: String): String {
    return segment.replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "").trim()
}
