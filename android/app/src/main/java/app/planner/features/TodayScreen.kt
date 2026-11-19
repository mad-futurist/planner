package app.planner.features

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.planner.R
import app.planner.core.designsystem.*
import app.planner.core.model.Record
import app.planner.domain.isScheduledOnDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val PanelTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val PanelBottom = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
private val CoffeeTitle = Regex("""(?<!\p{L})(coffee|кофе)(?!\p{L})""", RegexOption.IGNORE_CASE)

@Composable
internal fun TodayScreen(
    vm: PlannerViewModel,
    nav: NavHostController,
    status: String,
    colors: TodayColors,
    edit: (String) -> Unit,
    develop: (String) -> Unit,
    addTask: () -> Unit,
    openProfile: () -> Unit,
    openCollection: () -> Unit,
) {
    val records by vm.todayItems.collectAsStateWithLifecycle()
    val tomorrow by vm.tomorrowItems.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val idea by vm.idea.collectAsStateWithLifecycle()
    val review by vm.taskReview.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reads by vm.localReads.collectAsStateWithLifecycle()
    val date by vm.today.collectAsStateWithLifecycle()
    var expanded by rememberSaveable(date) { mutableStateOf(false) }
    var showDone by rememberSaveable(date) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val (scheduled, anytime) = remember(records, date, zone) {
        val (timed, untimed) = records.partition { isScheduledOnDay(it, date, zone) }
        timed.sortedBy { it.startAt } to untimed.filter { it.baseKind == "task" }
            .sortedWith(compareByDescending<Record> { it.pinned }.thenBy { it.createdAt })
    }
    val visibleAnytime = if (expanded) anytime else anytime.take(2)
    val (deadlines, otherTasks) = visibleAnytime.partition { it.dueDate != null || it.dueAt != null }
    val pendingRows = deadlines + scheduled + otherTasks
    val visibleDone = if (showDone) completed else completed.take(1)
    val lastPending = pendingRows.lastOrNull().takeIf { visibleDone.isEmpty() }
    val lastDone = visibleDone.lastOrNull()
    val next = tomorrow.filter { isScheduledOnDay(it, date.plusDays(1), zone) }.minByOrNull { it.startAt.orEmpty() }
    val openDay: (LocalDate) -> Unit = { day ->
        vm.selectedDate.value = day
        nav.navigate("calendar")
    }
    val toggleTask: (Record) -> Unit = { record ->
        if (record.state == "completed") vm.action { vm.repository.complete(record, true) }
        else vm.finish(record)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 15.dp).testTag("today-list"),
        contentPadding = PaddingValues(top = 29.dp, bottom = 20.dp),
    ) {
        item {
            TodayHeading(date, colors, openDay, openProfile) { nav.navigate("settings") }
            TodayPanelHeading(date, records.count { it.baseKind == "task" }, completed.size, colors, openDay)
        }
        items(if (lastPending == null) pendingRows else pendingRows.dropLast(1), key = { "pending:${it.id}" }) { record ->
            TodayRecordRow(record, date, colors, { edit(record.id) }, { toggleTask(record) })
        }
        if (pendingRows.isEmpty()) item {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 20.dp)) {
                when (reads["today"]) {
                    "ready" -> Text(stringResource(R.string.today_empty), style = TodayType.body, color = colors.muted)
                    "error" -> {
                        Text("Couldn't read saved data. Your input is still here.", style = TodayType.body, color = colors.muted)
                        TextButton(vm::reloadLocalQueries) { Text("Try again", color = colors.ink) }
                    }
                    else -> Text("Reading saved data…", style = TodayType.body, color = colors.muted)
                }
                TextButton(addTask) { Text(stringResource(R.string.today_add_task), color = colors.ink) }
            }
        }
        items(visibleDone.dropLast(1), key = { "done:${it.id}" }) { record ->
            TodayRecordRow(record, date, colors, { edit(record.id) }, { toggleTask(record) })
        }
        item {
            Column(Modifier.fillMaxWidth().clip(PanelBottom).background(colors.surface).padding(bottom = 8.dp)) {
                val last = lastDone ?: lastPending
                val showActions = anytime.size > 2 || completed.size > 1
                if (last != null) {
                    TodayRecordRow(last, date, colors, { edit(last.id) }, { toggleTask(last) }, divider = showActions)
                }
                if (showActions) Column(Modifier.padding(horizontal = 20.dp)) {
                    if (anytime.size > 2) {
                        TodayPanelAction(
                            if (expanded) stringResource(R.string.today_show_less)
                            else pluralStringResource(R.plurals.today_more_tasks, anytime.size - 2, anytime.size - 2),
                            colors, expanded, Modifier.testTag("anytime-more"),
                        ) { expanded = !expanded }
                    }
                    if (completed.size > 1) {
                        TodayPanelAction(stringResource(R.string.today_done, completed.size), colors, showDone) { showDone = !showDone }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TodayCollection(profile.ideas.takeIf { reads["profile"] == "ready" }, colors, openCollection)
            Spacer(Modifier.height(10.dp))
        }
        if (reads["profile"] == "ready" && profile.notes == 0) item {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("A place for every thought", style = TodayType.section, color = colors.ink)
                Text("New notes stay private in Journal. Use + to create a task yourself, or allow cloud AI to interpret future notes.", style = TodayType.body, color = colors.muted)
                TextButton({ vm.cloudExplanation.value = true }) { Text("How cloud AI works", color = colors.ink) }
            }
        }
        if (next != null) item {
            Spacer(Modifier.height(12.dp))
            val more = if (tomorrow.size > 1) " · +${tomorrow.size - 1} more" else ""
            TodayLink("Tomorrow · ${next.title}", recordClock(next.startAt) + more, Glyph.Calendar, colors) { openDay(date.plusDays(1)) }
        }
        val savedIdea = idea
        if (savedIdea != null) item {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.today_daily_idea), Modifier.padding(start = 12.dp, bottom = 8.dp), style = TodayType.section, color = colors.ink)
            IdeaCard(savedIdea, { edit(savedIdea.id) }, { develop(savedIdea.id) }, { vm.action { vm.repository.later(savedIdea, date) } })
        } else if (reads["idea"] != "ready") item {
            Spacer(Modifier.height(12.dp))
            if (reads["idea"] == "error") {
                TodayLink("Couldn't read saved ideas", "Try again", Glyph.Bulb, colors, vm::reloadLocalQueries)
            } else {
                Text("Reading saved ideas…", Modifier.padding(start = 12.dp), style = TodayType.meta, color = colors.muted)
            }
        }
        if (review.isNotEmpty()) item {
            Spacer(Modifier.height(12.dp))
            TodayLink("Worth revisiting", review.first().title, Glyph.Leaf, colors) { nav.navigate("triage") }
        }
        item {
            Spacer(Modifier.height(8.dp))
            TextButton({ nav.navigate("settings") }) { Text(status, style = TodayType.meta, color = colors.muted) }
        }
    }
}

@Composable
private fun TodayHeading(
    date: LocalDate,
    colors: TodayColors,
    openDay: (LocalDate) -> Unit,
    openProfile: () -> Unit,
    openSettings: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val dateFormat = remember(locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMMEEEE")
        DateTimeFormatter.ofPattern(pattern, locale)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.clip(CircleShape).background(colors.peach).clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_open_date_picker)) {
                    DatePickerDialog(context, { _, y, m, d -> openDay(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                PlannerIcon(Glyph.Calendar, Modifier.size(17.dp), colors.ink)
                Text(date.format(dateFormat), Modifier.weight(1f, fill = false), style = TodayType.meta, color = colors.ink)
                PlannerIcon(Glyph.Down, Modifier.size(13.dp), colors.muted)
            }
        }
        TodayHeaderAction(Glyph.User, stringResource(R.string.today_open_profile), colors.ink, colors.surface, openProfile)
        TodayHeaderAction(Glyph.Settings, stringResource(R.string.today_open_settings), colors.ink, Color.Transparent, openSettings)
    }
    val title: @Composable () -> Unit = {
        Text(stringResource(R.string.today_title), Modifier.padding(start = 7.dp, top = 8.dp).semantics { heading() },
            color = colors.ink, style = TodayType.title)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val catWidth = minOf(maxWidth * .49f, 176.dp)
        val cat: @Composable (Modifier) -> Unit = { modifier ->
            Image(painterResource(R.drawable.today_cat_peek), contentDescription = null,
                modifier = modifier.width(catWidth).aspectRatio(768f / 591f))
        }
        if (maxWidth < 330.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(Modifier.fillMaxWidth()) {
                title()
                cat(Modifier.align(Alignment.End))
            }
        } else {
            title()
            cat(Modifier.align(Alignment.BottomEnd).offset(y = 10.dp))
        }
    }
}

@Composable
private fun TodayHeaderAction(glyph: Glyph, label: String, tint: Color, plate: Color, click: () -> Unit) {
    IconButton(click, Modifier.size(48.dp).semantics { contentDescription = label }) {
        Box(Modifier.size(37.dp).background(plate, CircleShape), contentAlignment = Alignment.Center) {
            PlannerIcon(glyph, Modifier.size(22.dp), tint)
        }
    }
}

@Composable
private fun TodayPanelHeading(date: LocalDate, pending: Int, completed: Int, colors: TodayColors, openDay: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EE", locale) }
    val fullFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val total = pending + completed
    Column(Modifier.fillMaxWidth().background(colors.surface, PanelTop).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { offset ->
                val day = date.plusDays(offset.toLong())
                val current = offset == 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(21.dp))
                        .background(if (current) colors.peachDeep else Color.Transparent)
                        .clickable(role = Role.Button) { openDay(day) }
                        .heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 8.dp)
                        .clearAndSetSemantics {
                            contentDescription = day.format(fullFormat)
                            role = Role.Button
                            selected = current
                            onClick { openDay(day); true }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(day.format(dayFormat).replaceFirstChar { it.titlecase(locale) }, style = TodayType.meta, color = if (current) colors.ink else colors.muted, textAlign = TextAlign.Center)
                    Text(day.dayOfMonth.toString(), style = TodayType.section.copy(fontSize = 21.sp), color = colors.ink)
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.size(5.dp).background(if (current) colors.amberInk else Color.Transparent, CircleShape))
                }
                if (offset < 4) Box(Modifier.width(1.dp).height(36.dp).background(colors.line))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.today_tasks), Modifier.weight(1f).semantics { heading() }, style = TodayType.section, color = colors.ink)
            if (total > 0) {
                Text(stringResource(R.string.today_progress, completed, total), style = TodayType.meta, color = colors.muted)
                LinearProgressIndicator(
                    progress = { completed.toFloat() / total },
                    modifier = Modifier.width(44.dp).height(6.dp).clearAndSetSemantics {},
                    color = colors.ink, trackColor = colors.line, gapSize = 0.dp, drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
internal fun TodayRecordRow(
    record: Record, date: LocalDate, colors: TodayColors, open: () -> Unit, toggle: () -> Unit,
    divider: Boolean = true, showTaskDate: Boolean = false,
) {
    val done = record.state == "completed"
    val hasCheckbox = record.baseKind == "task"
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    }
    val deadline = record.dueDate?.let { LocalDate.parse(it).format(formatter) }
        ?: record.dueAt?.let { Instant.parse(it).atZone(zone).format(formatter) + " · " + recordClock(it) }
    val clock = record.startAt?.let {
        if (Instant.parse(it).atZone(zone).toLocalDate() < date) stringResource(R.string.today_ongoing)
        else recordClock(it)
    }
    val details = recordDetails(record)
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 15.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasCheckbox) {
                val checkLabel = stringResource(if (done) R.string.today_restore else R.string.today_complete, record.title)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).toggleable(done, role = Role.Checkbox, onValueChange = { toggle() })
                        .semantics { contentDescription = checkLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    val mark = Modifier.size(28.dp)
                    if (done) Box(mark.background(colors.completed, CircleShape), contentAlignment = Alignment.Center) {
                        PlannerIcon(Glyph.Check, Modifier.size(19.dp), colors.canvas)
                    } else Box(mark.border(1.5.dp, colors.checkOutline, CircleShape))
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(
                    role = Role.Button, onClickLabel = stringResource(R.string.today_details),
                    onClick = open,
                ).semantics { contentDescription = "Details ${record.title}" }
                    .heightIn(min = 48.dp).padding(start = 6.dp, end = 3.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        record.title, style = TodayType.body,
                        color = if (done) colors.completed else colors.ink,
                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    if (!done && deadline != null) {
                        Row(
                            Modifier.background(colors.peachDeep, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlannerIcon(Glyph.Calendar, Modifier.size(14.dp), colors.amberInk)
                            Text(stringResource(R.string.today_due, deadline), style = TodayType.meta, color = colors.amberInk)
                        }
                    }
                    if (!done && clock != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlannerIcon(Glyph.Clock, Modifier.size(16.dp), colors.muted)
                            Text(clock, style = TodayType.meta, color = colors.muted)
                        }
                        if (details.isNotBlank()) Text(details, style = TodayType.meta, color = colors.muted, maxLines = 2)
                    }
                    if (showTaskDate && !done && deadline == null && clock == null && record.plannedFor != null) {
                        Text(taskDateLabel(record, date), style = TodayType.meta, color = colors.muted)
                    }
                }
                when {
                    done && CoffeeTitle.containsMatchIn(record.title) -> Image(
                        painterResource(R.drawable.today_coffee), null, Modifier.size(27.dp),
                    )
                    !done && details.contains("https://") -> PlannerIcon(Glyph.Attachment, Modifier.size(18.dp), colors.muted)
                    !done && record.pinned -> PlannerIcon(Glyph.Pin, Modifier.size(16.dp), colors.muted)
                    !hasCheckbox -> PlannerIcon(Glyph.People, Modifier.size(20.dp), colors.muted)
                }
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(horizontal = 6.dp), color = colors.line)
    }
}

@Composable
private fun TodayPanelAction(text: String, colors: TodayColors, expanded: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(CircleShape).clickable(role = Role.Button, onClick = click).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = TodayType.meta, color = colors.muted)
        PlannerIcon(if (expanded) Glyph.Up else Glyph.Down, Modifier.size(14.dp), colors.muted)
    }
}

@Composable
private fun TodayCollection(ideas: Int?, colors: TodayColors, onClick: () -> Unit) {
    val shape = RoundedCornerShape(27.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.surface).todayCollectionArt(colors)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 74.dp)
            .padding(horizontal = 19.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Image(painterResource(R.drawable.today_sparkles), null, Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.today_collection), style = TodayType.section, color = colors.ink)
            if (ideas != null) {
                Text(pluralStringResource(R.plurals.today_saved_ideas, ideas, ideas), style = TodayType.meta, color = colors.muted)
            }
        }
        PlannerIcon(Glyph.Right, Modifier.size(18.dp), colors.ink)
    }
}

@Composable
private fun TodayLink(title: String, subtitle: String, glyph: Glyph, colors: TodayColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlannerIcon(glyph, Modifier.size(20.dp), colors.muted)
        Column(Modifier.weight(1f)) {
            Text(title, style = TodayType.body, color = colors.ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = TodayType.meta, color = colors.muted)
        }
        PlannerIcon(Glyph.Right, Modifier.size(16.dp), colors.muted)
    }
}
