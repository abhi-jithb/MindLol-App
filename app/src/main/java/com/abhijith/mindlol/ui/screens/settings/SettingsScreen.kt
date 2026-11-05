package com.abhijith.mindlol.ui.screens.settings

import android.content.Context
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
import androidx.compose.runtime.mutableStateListOf
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
private const val LIMIT_STEP = 15

// Which time the open picker is editing. Null means no picker is open.
private enum class EditingTime { WAKE, BED }

/**
 * Mostly UI only for now: changes live in memory and reset when the app closes.
 * The usage access row is real and opens system Settings when tapped.
 */
@Composable
fun SettingsScreen(
    usageAccessGranted: Boolean,
    onOpenUsageSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val initial = FakeData.settings
    var wake by rememberSaveable { mutableStateOf(initial.wakeTime) }
    var bed by rememberSaveable { mutableStateOf(initial.bedTime) }
    var editing by rememberSaveable { mutableStateOf<EditingTime?>(null) }
    // mutableStateListOf: a list Compose watches, so replacing an item redraws that row.
    val rules = remember { mutableStateListOf(*initial.appRules.toTypedArray()) }
    var theme by rememberSaveable { mutableStateOf(initial.selectedTheme) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        NightLength(bed = bed, wake = wake)

        SectionLabel("SCHEDULE", Modifier.revealOnEnter(1))
        SettingRow("Wake up", wake.format(timeFormat), Modifier.revealOnEnter(2)) { editing = EditingTime.WAKE }
        SettingRow("Bedtime", bed.format(timeFormat), Modifier.revealOnEnter(3)) { editing = EditingTime.BED }

        SectionLabel("APP LIMITS AND LOCKS", Modifier.revealOnEnter(4))
        rules.forEachIndexed { i, rule ->
            AppRuleRow(
                rule = rule,
                onChange = { rules[i] = it },
                modifier = Modifier.revealOnEnter(5 + i)
            )
        }

        SectionLabel("PERMISSIONS", Modifier.revealOnEnter(8))
        PermissionRow(
            permission = PermissionInfo(
                name = "Usage access",
                reason = "To count screen time per app",
                status = if (usageAccessGranted) "Allowed" else "Not granted"
            ),
            onClick = onOpenUsageSettings
        )
        initial.permissions.forEach { PermissionRow(it) }

        SectionLabel("KEEP TRACKING IN THE BACKGROUND", Modifier.revealOnEnter(9))
        BackgroundLimitsSection(onOpenAppSettings)

        SectionLabel("THEME", Modifier.revealOnEnter(10))
        initial.themeOptions.forEachIndexed { i, name ->
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
            text = "Preview only. Changes reset when the app closes.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
    }

    editing?.let { which ->
        TimePickerDialog(
            initial = if (which == EditingTime.WAKE) wake else bed,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                if (which == EditingTime.WAKE) wake = picked else bed = picked
                editing = null
            }
        )
    }
}

/** The screen's one big number: how long the phone stays off between bedtime and waking. */
@Composable
private fun NightLength(bed: LocalTime, wake: LocalTime) {
    val minutesOff = Math.floorMod(wake.toSecondOfDay() / 60 - bed.toSecondOfDay() / 60, 24 * 60)
    val shown = remember { Animatable(minutesOff.toFloat()) }
    LaunchedEffect(minutesOff) { shown.animateTo(minutesOff.toFloat(), Motion.standard(Motion.LONG)) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatMinutes(shown.value.toInt()),
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "PHONE-FREE EACH NIGHT",
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

/** App name, a − limit + stepper (15-minute steps, 0 means no limit) and a bedtime-lock switch. */
@Composable
private fun AppRuleRow(rule: AppRule, onChange: (AppRule) -> Unit, modifier: Modifier = Modifier) {
    val limit = rule.dailyLimitMinutes ?: 0
    // copy() makes a new AppRule with one field changed, like {...rule, x} in JS.
    fun setLimit(minutes: Int) = onChange(rule.copy(dailyLimitMinutes = minutes.takeIf { it > 0 }))

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
                "Lock at bedtime",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = rule.lockedAtBedtime, onCheckedChange = { onChange(rule.copy(lockedAtBedtime = it)) })
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

@Preview(heightDp = 1400)
@Composable
private fun SettingsScreenPreview() {
    MindLolTheme { SettingsScreen(usageAccessGranted = false, onOpenUsageSettings = {}, onOpenAppSettings = {}) }
}
