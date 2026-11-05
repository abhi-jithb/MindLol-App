package com.abhijith.mindlol.ui.screens.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhijith.mindlol.domain.InstalledApp
import com.abhijith.mindlol.domain.RoastIntensity
import com.abhijith.mindlol.domain.Settings
import com.abhijith.mindlol.domain.SleepTime
import com.abhijith.mindlol.domain.TonightOverride
import java.time.Instant
import java.time.ZoneId
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.abhijith.mindlol.data.repository.FakeData
import com.abhijith.mindlol.domain.AppRule
import com.abhijith.mindlol.domain.PermissionInfo
import com.abhijith.mindlol.ui.components.formatMinutes
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
private val expiryFormat = DateTimeFormatter.ofPattern("EEE h:mm a")
private const val LIMIT_STEP = 15

// Which time the open picker is editing. Null means no picker is open.
private enum class EditingTime { WAKE, BED, TONIGHT_WAKE, TONIGHT_BED }

/**
 * Sleep window, tonight's override, locked apps, limits and roast intensity are real and saved
 * with DataStore (through [SettingsViewModel]). Theme is still preview only.
 */
@Composable
fun SettingsScreen(
    usageAccessGranted: Boolean,
    onOpenUsageSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        message = message,
        onMessageShown = viewModel::messageShown,
        actions = SettingsActions(
            setWake = viewModel::setWake,
            setBedtime = viewModel::setBedtime,
            setTonightEnabled = viewModel::setTonightEnabled,
            setTonightWake = viewModel::setTonightWake,
            setTonightBedtime = viewModel::setTonightBedtime,
            addApp = viewModel::addApp,
            setLocked = viewModel::setLocked,
            setLimit = viewModel::setLimit,
            setRoastIntensity = viewModel::setRoastIntensity
        ),
        usageAccessGranted = usageAccessGranted,
        onOpenUsageSettings = onOpenUsageSettings,
        onOpenAppSettings = onOpenAppSettings
    )
}

/** Everything the screen can change, bundled so [SettingsContent] stays free of the ViewModel (and previewable). */
data class SettingsActions(
    val setWake: (LocalTime) -> Unit = {},
    val setBedtime: (LocalTime) -> Unit = {},
    val setTonightEnabled: (Boolean) -> Unit = {},
    val setTonightWake: (LocalTime) -> Unit = {},
    val setTonightBedtime: (LocalTime) -> Unit = {},
    val addApp: (String) -> Unit = {},
    val setLocked: (String, Boolean) -> Unit = { _, _ -> },
    val setLimit: (String, Int) -> Unit = { _, _ -> },
    val setRoastIntensity: (RoastIntensity) -> Unit = {}
)

@Composable
fun SettingsContent(
    state: SettingsUiState,
    message: String?,
    onMessageShown: () -> Unit,
    actions: SettingsActions,
    usageAccessGranted: Boolean,
    onOpenUsageSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val settings = state.settings
    val override = state.activeOverride
    val wake = SleepTime.toLocalTime(settings.wakeMinutes)
    val bed = SleepTime.toLocalTime(settings.bedtimeMinutes)

    var editing by rememberSaveable { mutableStateOf<EditingTime?>(null) }
    var pickingApp by rememberSaveable { mutableStateOf(false) }
    val fake = FakeData.settings
    var theme by rememberSaveable { mutableStateOf(fake.selectedTheme) }

    // A snackbar is a short message that slides up from the bottom and goes away by itself.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(32.dp))
            // Tonight's window wins while it applies, so the big number shows what really counts tonight.
            NightLength(
                bedMinutes = override?.bedtimeMinutes ?: settings.bedtimeMinutes,
                wakeMinutes = override?.wakeMinutes ?: settings.wakeMinutes,
                label = if (override != null) "PHONE-FREE TONIGHT" else "PHONE-FREE EACH NIGHT"
            )

            SectionLabel("SCHEDULE", Modifier.revealOnEnter(1))
            SettingRow("Wake up", wake.format(timeFormat), Modifier.revealOnEnter(2)) { editing = EditingTime.WAKE }
            SettingRow("Bedtime", bed.format(timeFormat), Modifier.revealOnEnter(3)) { editing = EditingTime.BED }

            SectionLabel("TONIGHT IS DIFFERENT", Modifier.revealOnEnter(4))
            TonightSection(
                override = override,
                onToggle = actions.setTonightEnabled,
                onEditBed = { editing = EditingTime.TONIGHT_BED },
                onEditWake = { editing = EditingTime.TONIGHT_WAKE },
                modifier = Modifier.revealOnEnter(5)
            )

            SectionLabel("LOCKED APPS AND LIMITS", Modifier.revealOnEnter(6))
            if (state.rules.isEmpty()) {
                Text(
                    "No apps locked. Add the ones that keep you up.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            // key(): each row is tied to its package, so rows keep their identity when the list re-sorts.
            state.rules.forEach { rule ->
                key(rule.packageName) {
                    AppRuleRow(
                        rule = rule,
                        onLimitChange = { actions.setLimit(rule.packageName, it) },
                        onLockChange = { actions.setLocked(rule.packageName, it) }
                    )
                }
            }
            OutlinedButton(
                onClick = { pickingApp = true },
                enabled = state.addableApps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Add app")
            }

            SectionLabel("ROAST INTENSITY", Modifier.revealOnEnter(7))
            RoastIntensitySelector(selected = settings.roastIntensity, onSelect = actions.setRoastIntensity)

            SectionLabel("PERMISSIONS", Modifier.revealOnEnter(8))
            PermissionRow(
                permission = PermissionInfo(
                    name = "Usage access",
                    reason = "To count screen time per app",
                    status = if (usageAccessGranted) "Allowed" else "Not granted"
                ),
                onClick = onOpenUsageSettings
            )
            fake.permissions.forEach { PermissionRow(it) }

            SectionLabel("KEEP TRACKING IN THE BACKGROUND", Modifier.revealOnEnter(9))
            BackgroundLimitsSection(onOpenAppSettings)

            SectionLabel("THEME", Modifier.revealOnEnter(10))
            fake.themeOptions.forEachIndexed { i, name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = theme == i, role = Role.RadioButton) { theme = i }
                        .padding(vertical = 4.dp)
                ) {
                    // onClick = null: the whole row handles the tap, so the touch target is bigger.
                    RadioButton(selected = theme == i, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Spacer(Modifier.height(40.dp))
            Text(
                text = "Theme is preview only. Everything else is saved.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(32.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            )
        }
    }

    editing?.let { which ->
        val initial = when (which) {
            EditingTime.WAKE -> wake
            EditingTime.BED -> bed
            EditingTime.TONIGHT_WAKE -> SleepTime.toLocalTime(override?.wakeMinutes ?: settings.wakeMinutes)
            EditingTime.TONIGHT_BED -> SleepTime.toLocalTime(override?.bedtimeMinutes ?: settings.bedtimeMinutes)
        }
        TimePickerDialog(
            initial = initial,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                when (which) {
                    EditingTime.WAKE -> actions.setWake(picked)
                    EditingTime.BED -> actions.setBedtime(picked)
                    EditingTime.TONIGHT_WAKE -> actions.setTonightWake(picked)
                    EditingTime.TONIGHT_BED -> actions.setTonightBedtime(picked)
                }
                editing = null
            }
        )
    }

    if (pickingApp) {
        AppPickerDialog(
            apps = state.addableApps,
            onDismiss = { pickingApp = false },
            onPick = {
                actions.addApp(it.packageName)
                pickingApp = false
            }
        )
    }
}

/** The on/off switch, and when on, tonight's two times and when the override ends. */
@Composable
private fun TonightSection(
    override: TonightOverride?,
    onToggle: (Boolean) -> Unit,
    onEditBed: () -> Unit,
    onEditWake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = override != null, role = Role.Switch, onValueChange = onToggle)
                .padding(vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Custom window tonight", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "For the rare late night. Ends by itself.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = override != null, onCheckedChange = null)
        }
        // AnimatedVisibility: the rows slide open and closed instead of popping in.
        AnimatedVisibility(visible = override != null) {
            Column {
                // Keep showing the last values while the rows animate out.
                val shown = override ?: return@Column
                SettingRow("Tonight's bedtime", SleepTime.toLocalTime(shown.bedtimeMinutes).format(timeFormat), onClick = onEditBed)
                SettingRow("Tonight's wake up", SleepTime.toLocalTime(shown.wakeMinutes).format(timeFormat), onClick = onEditWake)
                val ends = Instant.ofEpochMilli(shown.expiresAtMillis).atZone(ZoneId.systemDefault())
                Text(
                    "Back to normal after ${ends.format(expiryFormat)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

/** Three equal boxes; the chosen one turns white with black text. */
@Composable
private fun RoastIntensitySelector(selected: RoastIntensity, onSelect: (RoastIntensity) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup()
        ) {
            RoastIntensity.entries.forEach { option ->
                val isSelected = option == selected
                val fill by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                    Motion.standard(), label = "intensityFill"
                )
                val text by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    Motion.standard(), label = "intensityText"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                        .background(fill, RoundedCornerShape(8.dp))
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                        .padding(vertical = 12.dp)
                ) {
                    Text(option.name, style = MaterialTheme.typography.labelMedium, color = text)
                }
            }
        }
        Text(
            text = when (selected) {
                RoastIntensity.MILD -> "Disappointed, not cruel."
                RoastIntensity.SAVAGE -> "Direct. About your choices, never about you."
                RoastIntensity.PERSONAL -> "Savage, plus personal questions at unlock."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Search box over the launcher apps; tapping one adds a row for it. */
@Composable
private fun AppPickerDialog(apps: List<InstalledApp>, onDismiss: () -> Unit, onPick: (InstalledApp) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                // LazyColumn only builds the rows on screen, like FlatList / ListView.builder.
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    )
}

/** The screen's one big number: how long the phone stays off between bedtime and waking. */
@Composable
private fun NightLength(bedMinutes: Int, wakeMinutes: Int, label: String) {
    val minutesOff = SleepTime.windowLength(bedMinutes, wakeMinutes)
    val shown = remember { Animatable(minutesOff.toFloat()) }
    LaunchedEffect(minutesOff) { shown.animateTo(minutesOff.toFloat(), Motion.standard(Motion.LONG)) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatMinutes(shown.value.toInt()),
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 40.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** App name, a − limit + stepper (15-minute steps, 0 means no limit) and a sleep-window lock switch. */
@Composable
private fun AppRuleRow(
    rule: AppRule,
    onLimitChange: (Int) -> Unit,
    onLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val limit = rule.dailyLimitMinutes ?: 0
    fun setLimit(minutes: Int) = onLimitChange(minutes) // 0 removes the limit

    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                rule.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { setLimit((limit - LIMIT_STEP).coerceAtLeast(0)) }, enabled = limit > 0) { Text("−") }
            Text(
                text = if (limit > 0) "${formatMinutes(limit)} / day" else "No limit",
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 96.dp)
            )
            TextButton(onClick = { setLimit(limit + LIMIT_STEP) }) { Text("+") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Lock during sleep window",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = rule.lockedAtBedtime, onCheckedChange = onLockChange)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Composable
private fun PermissionRow(permission: PermissionInfo, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(permission.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(permission.reason, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(permission.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Vivo (OriginOS) and some other brands stop apps in the background to save battery, which
 * would pause the 6-hour sync. This explains how to allow it; nothing is changed automatically.
 */
@Composable
private fun BackgroundLimitsSection(onOpenAppSettings: () -> Unit) {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    // Re-read when you come back from system Settings.
    LifecycleResumeEffect(Unit) {
        unrestricted = isBatteryUnrestricted(context)
        onPauseOrDispose { }
    }
    val isVivo = Build.MANUFACTURER.equals("vivo", ignoreCase = true)

    PermissionRow(
        PermissionInfo(
            name = "Battery",
            reason = "Unrestricted keeps the background sync running",
            status = if (unrestricted) "Unrestricted" else "Optimized"
        )
    )
    Text(
        text = if (isVivo) {
            "On Vivo, allow both:\n" +
                "1. Auto-start: Settings → Apps → Autostart → MindLol on.\n" +
                "2. Battery: App info → Battery → Allow background activity / high background power use.\n" +
                "Menu names vary a little between OriginOS versions."
        } else {
            "If your phone limits background apps, set MindLol's battery use to Unrestricted in App info."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    OutlinedButton(
        onClick = onOpenAppSettings,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Text("Open MindLol's app settings")
    }
}

/** Read-only check: is MindLol exempt from battery optimization? */
private fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

// TimePicker is still marked experimental in Material 3, so we opt in explicitly.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            // Selected parts go white-on-black so they stand out against the gray dialog.
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.background,
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onBackground,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.background,
                    periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    )
}

@Preview(heightDp = 1600)
@Composable
private fun SettingsContentPreview() {
    MindLolTheme {
        SettingsContent(
            message = null,
            onMessageShown = {},
            state = SettingsUiState(
                settings = Settings(roastIntensity = RoastIntensity.SAVAGE),
                rules = listOf(
                    AppRule("com.instagram.android", "Instagram", dailyLimitMinutes = 45, lockedAtBedtime = true),
                    AppRule("com.google.android.youtube", "YouTube", dailyLimitMinutes = null, lockedAtBedtime = true)
                )
            ),
            actions = SettingsActions(),
            usageAccessGranted = false,
            onOpenUsageSettings = {},
            onOpenAppSettings = {}
        )
    }
}
