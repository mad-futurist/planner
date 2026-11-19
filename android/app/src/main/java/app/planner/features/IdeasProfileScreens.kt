package app.planner.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.planner.core.designsystem.*
import app.planner.core.model.Record
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun IdeasScreen(
    vm: PlannerViewModel, nav: NavHostController, colors: TodayColors, edit: (String) -> Unit,
    develop: (String) -> Unit, addIdea: () -> Unit,
) {
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val query by vm.ideaSearch.collectAsStateWithLifecycle()
    val page by vm.ideaPage.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale).withZone(ZoneId.systemDefault()) }
    var themes by rememberSaveable { mutableStateOf(false) }
    val displayed = if (themes) ideas.sortedBy { recordDomain(it).ifBlank { "\uffff" } } else ideas
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 15.dp).testTag("ideas-list"),
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            WarmChoice(organizerLabel("Collection", "Коллекция"), colors) { nav.navigate("profile") }
            WarmHeader(organizerLabel("Ideas", "Идеи"), nav, colors) {
                IconButton(addIdea, Modifier.semantics { contentDescription = "Add idea" }) { PlannerIcon(Glyph.Plus, Modifier.size(23.dp), colors.ink) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WarmChoice(organizerLabel("All", "Все"), colors, selected = !themes) { themes = false }
                WarmChoice(organizerLabel("By theme", "По темам"), colors, selected = themes) { themes = true }
            }
            OutlinedTextField(
                query, { vm.ideaPage.value = 0; vm.ideaSearch.value = it },
                Modifier.fillMaxWidth().padding(top = 6.dp).testTag("idea-search"),
                placeholder = { Text(organizerLabel("Find a saved idea…", "Найти идею…"), style = TodayType.body) },
                singleLine = true, shape = CircleShape, textStyle = TodayType.body,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.amberInk, unfocusedBorderColor = colors.line, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink),
            )
        }
        if (displayed.isEmpty()) item { LocalQueryHint(vm, "ideas", organizerLabel("No saved ideas in this view.", "В этом разделе нет сохранённых идей.")) }
        itemsIndexed(displayed, key = { _, record -> record.id }) { i, record ->
            if (themes && (i == 0 || recordDomain(displayed[i - 1]) != recordDomain(record))) {
                Text(recordDomain(record).ifBlank { organizerLabel("No theme", "Без темы") }, Modifier.padding(start = 10.dp, bottom = 5.dp), color = colors.muted, style = TodayType.meta)
            }
            if (i == 0 && !themes && query.isBlank()) Box(Modifier.fillMaxWidth().height(67.dp)) {
                WarmCat(Modifier.align(Alignment.TopEnd).padding(end = 8.dp))
            }
            WarmIdea(record, colors, i == 0 && !themes, formatter, { edit(record.id) }, { develop(record.id) }) {
                vm.action {
                    vm.repository.linkedTask(record, private = vm.privateCapture.value)
                    vm.message.value = "Task created · your idea is still saved"
                }
            }
        }
        item {
            WarmPanel(colors, lavender = true) {
                WarmProfileLink(organizerLabel("Collections", "Коллекции"), Glyph.Sparkles, colors,
                    organizerLabel("${profile.ideas} saved ideas", "Сохранённых идей: ${profile.ideas}")) { nav.navigate("profile") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ vm.ideaPage.value = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text(organizerLabel("Previous", "Назад")) }
                TextButton({ vm.ideaPage.value = page + 1 }, enabled = ideas.size == 50) { Text(organizerLabel("More ideas", "Ещё идеи")) }
            }
        }
    }
}

@Composable
private fun WarmIdea(
    record: Record, colors: TodayColors, featured: Boolean, formatter: DateTimeFormatter, open: () -> Unit,
    develop: () -> Unit, convert: () -> Unit,
) {
    WarmPanel(colors, lavender = featured, container = if (featured) colors.lavender else colors.surface) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = open).padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).background(colors.peach, CircleShape), contentAlignment = Alignment.Center) {
                PlannerIcon(if (featured) Glyph.Sparkles else Glyph.Bulb, Modifier.size(20.dp), colors.amberInk)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.title, style = TodayType.section, color = colors.ink)
                recordDetails(record).takeIf(String::isNotBlank)?.let { Text(it, style = TodayType.meta, color = colors.muted, maxLines = 3) }
                Text(formatter.format(Instant.parse(record.createdAt)), style = TodayType.meta, color = colors.muted)
            }
            PlannerIcon(Glyph.Right, Modifier.size(17.dp), colors.muted)
        }
        Row(Modifier.padding(horizontal = 15.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(develop) { Text(organizerLabel("Develop", "Развить"), color = colors.ink) }
            TextButton(convert, Modifier.testTag("convert-${record.id}")) { Text(organizerLabel("Make a task", "Создать задачу"), color = colors.ink) }
        }
    }
}

@Composable
internal fun ProfileScreen(vm: PlannerViewModel, nav: NavHostController, colors: TodayColors) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    val reads by vm.localReads.collectAsStateWithLifecycle()
    val owner = metadata["owner"]
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 15.dp).testTag("profile-list"),
        contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            WarmHeader(organizerLabel("Profile", "Профиль"), nav, colors, showProfile = false)
            Box(Modifier.fillMaxWidth().height(83.dp)) { WarmCat(Modifier.align(Alignment.TopEnd).padding(end = 13.dp)) }
            WarmPanel(colors) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(52.dp).background(colors.peach, CircleShape), contentAlignment = Alignment.Center) {
                        PlannerIcon(Glyph.User, Modifier.size(28.dp), colors.ink)
                    }
                    Column {
                        Text(if (owner == null) organizerLabel("Local device profile", "Локальный профиль") else organizerLabel("Connected workspace", "Подключённое пространство"),
                            style = TodayType.section, color = colors.ink)
                        Text(owner?.let { organizerLabel("Workspace", "Пространство") + " ${it.take(8)}" }
                            ?: organizerLabel("On this device", "На этом устройстве"), style = TodayType.meta, color = colors.muted)
                    }
                }
                if (reads["profile"] == "ready") {
                    Row(Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileMetric(organizerLabel("Completed", "Выполнено"), profile.completed, Modifier.weight(1f), colors, colors.lavender)
                        ProfileMetric(organizerLabel("Ideas", "Идеи"), profile.ideas, Modifier.weight(1f), colors, colors.peach)
                        ProfileMetric(organizerLabel("Notes", "Заметки"), profile.notes, Modifier.weight(1f), colors, colors.peachDeep)
                    }
                } else LocalQueryHint(vm, "profile", "")
            }
        }
        item {
            WarmPanel(colors) {
                WarmProfileLink(organizerLabel("Journal", "Журнал"), Glyph.Book, colors) { nav.navigate("journal") }
                HorizontalDivider(Modifier.padding(start = 45.dp, end = 14.dp), color = colors.line)
                WarmProfileLink(organizerLabel("Review", "На проверку"), Glyph.Sparkles, colors) { nav.navigate("triage") }
                HorizontalDivider(Modifier.padding(start = 45.dp, end = 14.dp), color = colors.line)
                WarmProfileLink(organizerLabel("Settings", "Настройки"), Glyph.Settings, colors) { nav.navigate("settings") }
            }
        }
        item {
            WarmPanel(colors, lavender = true) {
                WarmProfileLink(organizerLabel("Collections", "Коллекции"), Glyph.Tag, colors) {
                    nav.navigate("ideas") { launchSingleTop = true }
                }
                collections.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(item.name, Modifier.weight(1f), color = colors.ink, style = TodayType.meta)
                        Text(item.count.toString(), color = colors.muted, style = TodayType.meta)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: Int, modifier: Modifier, colors: TodayColors, tint: Color) {
    Column(modifier.background(tint, RoundedCornerShape(20.dp)).padding(horizontal = 7.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = colors.ink, style = TodayType.section)
        Text(label, color = colors.muted, style = TodayType.meta, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun WarmProfileLink(label: String, glyph: Glyph, colors: TodayColors, subtitle: String? = null, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = click).heightIn(min = 50.dp)
        .padding(horizontal = 15.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        PlannerIcon(glyph, Modifier.size(20.dp), colors.ink)
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.ink, style = TodayType.body)
            subtitle?.let { Text(it, color = colors.muted, style = TodayType.meta) }
        }
        PlannerIcon(Glyph.Right, Modifier.size(15.dp), colors.muted)
    }
}
