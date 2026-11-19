package app.planner.features

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.planner.core.designsystem.*
import app.planner.core.model.Record
import app.planner.domain.isScheduledOnDay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private enum class CalendarMode { DAY, WEEK, MONTH }

@Composable
internal fun CalendarScreen(vm: PlannerViewModel, nav: NavHostController, colors: TodayColors, edit: (String) -> Unit) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val records by vm.calendar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var mode by rememberSaveable { mutableStateOf(CalendarMode.WEEK) }
    val firstDay = if (settings["weekStart"] == "monday") DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val week = date.with(TemporalAdjusters.previousOrSame(firstDay))
    val zone = ZoneId.systemDefault()
    val select: (LocalDate) -> Unit = { vm.selectedDate.value = it }
    val days = List(7) { week.plusDays(it.toLong()) }
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val move: (Long) -> Unit = { delta ->
        select(when (mode) {
            CalendarMode.DAY -> date.plusDays(delta)
            CalendarMode.WEEK -> date.plusWeeks(delta)
            CalendarMode.MONTH -> date.plusMonths(delta)
        })
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 15.dp).testTag("calendar-list"),
        contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WarmChoice(date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), colors) {
                    DatePickerDialog(context, { _, y, m, d -> select(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }
                Spacer(Modifier.weight(1f))
                TextButton({ select(LocalDate.now()) }) { Text(organizerLabel("Today", "Сегодня"), color = colors.ink) }
            }
            WarmHeader(organizerLabel("Calendar", "Календарь"), nav, colors)
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(if (mode == CalendarMode.WEEK) .65f else 1f).padding(top = 6.dp)
                        .background(colors.peach, CircleShape).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    CalendarMode.entries.forEach { value ->
                        val label = when (value) {
                            CalendarMode.DAY -> organizerLabel("Day", "День")
                            CalendarMode.WEEK -> organizerLabel("Week", "Неделя")
                            CalendarMode.MONTH -> organizerLabel("Month", "Месяц")
                        }
                        WarmChoice(label, colors, Modifier.weight(1f), selected = value == mode) { mode = value }
                    }
                }
                if (mode == CalendarMode.WEEK) WarmCat(Modifier.align(Alignment.BottomEnd).padding(top = 11.dp, end = 5.dp).width(118.dp))
            }
        }
        item {
            WarmPanel(colors) {
                val heading = when (mode) {
                    CalendarMode.DAY -> date.format(formatter)
                    CalendarMode.WEEK -> "${week.format(formatter)} – ${days.last().format(formatter)} ${date.year}"
                    CalendarMode.MONTH -> date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ move(-1) }, Modifier.semantics { contentDescription = "Previous period" }) { PlannerIcon(Glyph.Left, Modifier.size(18.dp), colors.ink) }
                    Text(heading, Modifier.weight(1f), color = colors.ink, style = TodayType.body)
                    IconButton({ move(1) }, Modifier.semantics { contentDescription = "Next period" }) { PlannerIcon(Glyph.Right, Modifier.size(18.dp), colors.ink) }
                }
                when (mode) {
                    CalendarMode.WEEK -> CalendarTimeline(days, date, zone, colors, vm, select, edit)
                    CalendarMode.MONTH -> MonthGrid(date, firstDay, colors, select)
                    CalendarMode.DAY -> Row(Modifier.fillMaxWidth().padding(5.dp)) {
                        days.forEach { day -> CalendarDay(day, date, Modifier.weight(1f), colors, select) }
                    }
                }
            }
        }
        val scheduled = records.filter { isScheduledOnDay(it, date, zone) }.sortedBy { it.startAt }
        val tasks = records.filter { it.baseKind == "task" && !isScheduledOnDay(it, date, zone) }
        item {
            Text(organizerLabel("Scheduled events", "События") + " · " + date.format(formatter), Modifier.padding(horizontal = 8.dp), color = colors.ink, style = TodayType.section)
            if (scheduled.isEmpty()) LocalQueryHint(vm, "calendar", organizerLabel("No timed commitments on this day.", "На этот день нет событий со временем."))
        }
        items(scheduled, key = { "scheduled:${it.id}" }) { record ->
            WarmPanel(colors) { TodayRecordRow(record, date, colors, { edit(record.id) }, { vm.finish(record) }, divider = false) }
        }
        item {
            Text(organizerLabel("Planned / due", "Запланировано / срок"), Modifier.padding(horizontal = 8.dp), color = colors.ink, style = TodayType.section)
            if (tasks.isEmpty()) LocalQueryHint(vm, "calendar", organizerLabel("Nothing planned or due on this day.", "На этот день нет задач или крайних сроков."))
        }
        items(tasks, key = { "planned:${it.id}" }) { record ->
            WarmPanel(colors) { TodayRecordRow(record, date, colors, { edit(record.id) }, { vm.finish(record) }, divider = false, showTaskDate = true) }
        }
    }
}

@Composable
private fun CalendarDay(day: LocalDate, selected: LocalDate, modifier: Modifier, colors: TodayColors, select: (LocalDate) -> Unit, weekLabel: Boolean = true, dim: Boolean = false, hasEntries: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.padding(2.dp).clip(CircleShape).background(if (day == selected) colors.peachDeep else Color.Transparent)
            .clickable(role = Role.Button) { select(day) }.heightIn(min = 48.dp).padding(vertical = 3.dp)
            .clearAndSetSemantics {
                contentDescription = day.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale))
                this.selected = day == selected
                role = Role.Button
                onClick { select(day); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (weekLabel) Text(day.format(DateTimeFormatter.ofPattern("EE", locale)), color = colors.muted, style = TodayType.meta)
        Text(day.dayOfMonth.toString(), color = if (dim) colors.muted else colors.ink, style = TodayType.body)
        if (weekLabel) Box(Modifier.size(4.dp).background(if (day == selected || hasEntries) colors.amberInk else Color.Transparent, CircleShape))
    }
}

@Composable
private fun MonthGrid(date: LocalDate, firstDay: DayOfWeek, colors: TodayColors, select: (LocalDate) -> Unit) {
    val monthStart = date.withDayOfMonth(1)
    val grid = monthStart.with(TemporalAdjusters.previousOrSame(firstDay))
    val weeks = ((monthStart.dayOfWeek.value - firstDay.value + 7) % 7 + date.lengthOfMonth() + 6) / 7
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.padding(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            repeat(7) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(grid.plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("EE", locale)), color = colors.muted, style = TodayType.meta)
                }
            }
        }
        repeat(weeks) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = grid.plusDays((row * 7 + column).toLong())
                    CalendarDay(day, date, Modifier.weight(1f), colors, select, weekLabel = false, dim = day.month != date.month)
                }
            }
        }
    }
}

@Composable
private fun CalendarTimeline(
    days: List<LocalDate>, selected: LocalDate, zone: ZoneId, colors: TodayColors, vm: PlannerViewModel,
    select: (LocalDate) -> Unit, edit: (String) -> Unit,
) {
    val recordsByDay = days.map { day ->
        key(day) {
            val records by remember(vm, day, zone) { vm.calendarDayRecords(day, zone) }.collectAsStateWithLifecycle(emptyList())
            records.filter { isScheduledOnDay(it, day, zone) }
        }
    }
    val starts = days.zip(recordsByDay).flatMap { (day, records) -> records.map { minuteOnDay(it.startAt!!, day, zone) } }
    val ends = days.zip(recordsByDay).flatMap { (day, records) -> records.map { minuteOnDay(it.endAt ?: it.startAt!!, day, zone) } }
    val firstHour = minOf(8, (starts.minOrNull() ?: 480) / 60)
    val endHour = maxOf(21, (ends.maxOrNull()?.plus(59) ?: 0) / 60, (starts.maxOrNull() ?: 0) / 60 + 1).coerceAtMost(24)
    val hourHeight = 24.dp
    val height = hourHeight * (endHour - firstHour)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(selected, scroll.maxValue > 0) {
        if (scroll.maxValue > 0) scroll.scrollTo(with(density) {
            (70.dp * (days.indexOf(selected) - 2).coerceAtLeast(0)).roundToPx().coerceAtMost(scroll.maxValue)
        })
    }
    Row(Modifier.fillMaxWidth().padding(start = 5.dp, end = 5.dp, bottom = 8.dp)) {
        Column(Modifier.width(43.dp).padding(top = 57.dp)) {
            for (hour in firstHour until endHour) {
                Box(Modifier.height(hourHeight)) {
                    if ((hour - firstHour) % 2 == 0) Text("%02d:00".format(hour), color = colors.muted, style = TodayType.nav)
                }
            }
        }
        Row(Modifier.horizontalScroll(scroll)) {
            days.zip(recordsByDay).forEach { (day, records) ->
                val lanes = mutableListOf<Int>()
                val placements = records.sortedBy { it.startAt }.map { record ->
                    val start = minuteOnDay(record.startAt!!, day, zone).coerceIn(firstHour * 60, endHour * 60 - 1)
                    val end = record.endAt?.let { minuteOnDay(it, day, zone) }
                    val visualEnd = end?.coerceAtLeast(start + 1) ?: (start + 1)
                    val lane = lanes.indexOfFirst { it <= start }.takeIf { it >= 0 } ?: lanes.size.also { lanes.add(0) }
                    lanes[lane] = maxOf(visualEnd, start + 90)
                    Triple(record, lane, start to visualEnd)
                }
                val laneWidth = 70.dp
                Column(Modifier.width(laneWidth * lanes.size.coerceAtLeast(1))) {
                    CalendarDay(day, selected, Modifier.fillMaxWidth().heightIn(min = 56.dp), colors, select, hasEntries = records.isNotEmpty())
                    Box(Modifier.fillMaxWidth().height(height).border(.6.dp, colors.line).clip(RoundedCornerShape(3.dp))) {
                        Column {
                            repeat(endHour - firstHour) { Box(Modifier.fillMaxWidth().height(hourHeight)) { HorizontalDivider(color = colors.line, thickness = .5.dp) } }
                        }
                        placements.forEach { (record, lane, range) ->
                            val span = range.second.coerceAtMost(endHour * 60) - range.first
                            Column(
                                Modifier.offset(x = laneWidth * lane, y = hourHeight * ((range.first - firstHour * 60) / 60f))
                                    .width(laneWidth).heightIn(min = maxOf(34.dp, hourHeight * (span / 60f))).padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(7.dp)).background(if (record.baseKind == "event") colors.lavender else colors.peachDeep)
                                    .clickable(role = Role.Button) { edit(record.id) }.padding(4.dp),
                            ) {
                                Text(recordClock(record.startAt), style = TodayType.nav, color = colors.muted)
                                Text(record.title, style = TodayType.meta, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun minuteOnDay(value: String, day: LocalDate, zone: ZoneId): Int {
    val local = Instant.parse(value).atZone(zone)
    return when {
        local.toLocalDate() < day -> 0
        local.toLocalDate() > day -> 1440
        else -> local.hour * 60 + local.minute
    }
}
