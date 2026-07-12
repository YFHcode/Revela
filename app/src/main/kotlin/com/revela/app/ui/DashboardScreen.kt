package com.revela.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revela.app.AppContainer
import com.revela.core.db.DaySummaryEntity
import com.revela.core.db.UsageDailyEntity
import com.revela.core.db.UsageHourlyEntity
import com.revela.core.model.DayKeys
import com.revela.pipeline.RollupScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dashboard v0 (§12.1): exploratory, no scoring. A single sequential hue
 * carries magnitude everywhere; identity/labels stay in ink tokens.
 */
@Composable
fun DashboardScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { RollupScheduler.rollupNow(context) }

    val zone = remember { ZoneId.systemDefault() }
    val todayKey = remember { DayKeys.dayKey(System.currentTimeMillis(), zone) }
    val heatmapMinDate = remember { LocalDate.now(zone).minusDays(27).toString() }

    val recentDays by container.database.daySummaryDao().recent(8)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val topApps by container.database.usageDailyDao().topForDate(todayKey, 8)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val hourly by container.database.usageHourlyDao().since(heatmapMinDate)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val today = recentDays.firstOrNull { it.date == todayKey }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Dashboard", style = MaterialTheme.typography.titleLarge)
        }

        if (recentDays.isEmpty()) {
            Card {
                Text(
                    "No rollups yet — data appears after the first processing pass. " +
                        "Pull up this screen again in a minute.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        StatTiles(today)
        WeekBars(recentDays.take(7).sortedBy { it.date }, todayKey)
        Heatmap(hourly)
        TopApps(topApps)
    }
}

@Composable
private fun StatTiles(today: DaySummaryEntity?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Screen time", today?.let { formatDuration(it.totalScreenTimeS) } ?: "—", Modifier.weight(1f))
        StatTile("Pickups", today?.pickupCount?.toString() ?: "—", Modifier.weight(1f))
        StatTile("Reflex checks", today?.reflexCheckCount?.toString() ?: "—", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("First unlock", formatClockTime(today?.firstUnlock), Modifier.weight(1f))
        StatTile("Last use", formatClockTime(today?.lastUse), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Last-7-days screen time. Single series → one hue, no legend; value on tap. */
@Composable
private fun WeekBars(days: List<DaySummaryEntity>, todayKey: String) {
    if (days.isEmpty()) return
    var selected by remember { mutableStateOf<DaySummaryEntity?>(null) }
    val max = days.maxOf { it.totalScreenTimeS }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Screen time — last 7 days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                days.forEach { day ->
                    val fraction = day.totalScreenTimeS.toFloat() / max
                    val isToday = day.date == todayKey
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selected = if (selected == day) null else day },
                    ) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height((104 * fraction).coerceAtLeast(2f).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isToday) barColor else barColor.copy(alpha = 0.45f)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            weekdayLetter(day.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            selected?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${it.date}: ${formatDuration(it.totalScreenTimeS)}, " +
                        "${it.pickupCount} pickups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Hour × weekday heatmap over the last 4 weeks. Sequential: one hue, light→dark. */
@Composable
private fun Heatmap(hourly: List<UsageHourlyEntity>) {
    if (hourly.isEmpty()) return
    var selectedCell by remember { mutableStateOf<Pair<DayOfWeek, Int>?>(null) }

    // avg seconds per (weekday, hour) = total / #dates seen for that weekday
    val datesPerDow = hourly.map { it.date }.distinct()
        .groupBy { LocalDate.parse(it).dayOfWeek }
        .mapValues { it.value.size }
    val totals = HashMap<Pair<DayOfWeek, Int>, Long>()
    for (row in hourly) {
        val key = LocalDate.parse(row.date).dayOfWeek to row.hour
        totals[key] = (totals[key] ?: 0L) + row.totalSeconds
    }
    val avg = totals.mapValues { (key, total) -> total / (datesPerDow[key.first] ?: 1) }
    val maxAvg = (avg.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val cellHue = MaterialTheme.colorScheme.primary
    val emptyCell = MaterialTheme.colorScheme.surfaceVariant

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("When your phone is in use", style = MaterialTheme.typography.titleMedium)
            Text(
                "average, last 4 weeks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            DayOfWeek.values().forEach { dow ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp),
                ) {
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(16.dp),
                    )
                    (0..23).forEach { hour ->
                        val value = avg[dow to hour] ?: 0L
                        val t = value.toFloat() / maxAvg
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 1.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (t == 0f) emptyCell else cellHue.copy(alpha = 0.15f + 0.85f * t))
                                .clickable {
                                    selectedCell =
                                        if (selectedCell == dow to hour) null else dow to hour
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(0.15f, 0.36f, 0.57f, 0.78f, 1f).forEach { a ->
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cellHue.copy(alpha = a)),
                        )
                    }
                }
                Text(
                    "more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            selectedCell?.let { (dow, hour) ->
                val value = avg[dow to hour] ?: 0L
                Spacer(Modifier.height(8.dp))
                Text(
                    "${dow.getDisplayName(TextStyle.FULL, Locale.getDefault())} " +
                        "${hour.toString().padStart(2, '0')}:00 — " +
                        "avg ${formatDuration(value.toInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopApps(topApps: List<UsageDailyEntity>) {
    if (topApps.isEmpty()) return
    val context = LocalContext.current
    val labels = remember(topApps) {
        topApps.associate { row ->
            row.appPkg to runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(row.appPkg, 0)).toString()
            }.getOrDefault(row.appPkg.substringAfterLast('.'))
        }
    }
    val max = topApps.maxOf { it.totalSeconds }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today's apps", style = MaterialTheme.typography.titleMedium)
            topApps.forEach { row ->
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            labels[row.appPkg] ?: row.appPkg,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatDuration(row.totalSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.totalSeconds.toFloat() / max)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }
}
