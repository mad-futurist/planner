package app.planner.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.planner.core.designsystem.*
import app.planner.core.model.Record

@Composable
internal fun TasksScreen(vm: PlannerViewModel, nav: NavHostController, colors: TodayColors, edit: (String) -> Unit, addTask: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val filter by vm.taskFilter.collectAsStateWithLifecycle()
    val page by vm.taskPage.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var views by remember { mutableStateOf(false) }
    var pinnedOpen by rememberSaveable { mutableStateOf(true) }
    var groupOpen by rememberSaveable { mutableStateOf(true) }
    val todayLabel = organizerLabel("Today", "Сегодня")
    val someday = organizerLabel("No date", "Без даты")
    val deadline = organizerLabel("Deadlines", "Дедлайны")
    val completed = organizerLabel("Completed", "Завершённые")
    val other = listOf("all" to organizerLabel("All unfinished", "Все активные"), "upcoming" to organizerLabel("Upcoming", "Позже"),
        "archived" to organizerLabel("Archived", "Архив"))
    val filters = listOf("today" to todayLabel, "someday" to someday, "deadlines" to deadline, "completed" to completed)
    val pinned = tasks.filter(Record::pinned)
    val ordinary = tasks.filterNot(Record::pinned)
    val done: (Record) -> Unit = { record ->
        if (record.state == "completed") vm.action { vm.repository.complete(record, true) }
        else if (record.state == "pending") vm.finish(record)
        else edit(record.id)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 15.dp).testTag("tasks-list"),
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Box {
                WarmChoice(other.firstOrNull { it.first == filter }?.second ?: organizerLabel("More views", "Другие виды"), colors,
                    Modifier.testTag("task-views")) { views = true }
                DropdownMenu(views, { views = false }) {
                    other.forEach { (key, label) -> DropdownMenuItem({ Text(label) }, { vm.filterTasks(key); views = false }) }
                }
            }
            WarmHeader(organizerLabel("Tasks", "Задачи"), nav, colors) {
                IconButton(addTask, Modifier.semantics { contentDescription = "Add task" }) { PlannerIcon(Glyph.Plus, Modifier.size(22.dp), colors.ink) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                filters.forEach { (key, label) ->
                    WarmChoice(label, colors, Modifier.weight(if (key == "completed") 1.35f else 1f).testTag("filter-$key"), filter == key) { vm.filterTasks(key) }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(70.dp)) { WarmCat(Modifier.align(Alignment.TopEnd).padding(end = 9.dp)) }
            if (pinned.isNotEmpty()) {
                WarmPanel(colors) {
                    TaskGroup(organizerLabel("Pinned", "Закреплено"), pinned.size, pinnedOpen, colors) { pinnedOpen = !pinnedOpen }
                    if (pinnedOpen) pinned.forEachIndexed { i, record ->
                        TodayRecordRow(record, today, colors, { edit(record.id) }, { done(record) }, divider = i < pinned.lastIndex, showTaskDate = true)
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
            WarmPanel(colors) {
                TaskGroup(when (filter) {
                    "today" -> organizerLabel("In focus", "В фокусе")
                    "someday" -> someday
                    "deadlines" -> deadline
                    "completed" -> completed
                    else -> other.firstOrNull { it.first == filter }?.second.orEmpty()
                }, ordinary.size, groupOpen, colors) { groupOpen = !groupOpen }
                if (groupOpen) ordinary.forEachIndexed { i, record ->
                    TodayRecordRow(record, today, colors, { edit(record.id) }, { done(record) }, divider = i < ordinary.lastIndex, showTaskDate = true)
                }
                if (tasks.isEmpty()) LocalQueryHint(vm, "tasks", organizerLabel("No tasks in this view.", "В этом разделе нет задач."))
            }
        }
        item {
            WarmPanel(colors, lavender = true) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(addTask, Modifier.weight(1f)) { Text(organizerLabel("+ Add task", "+ Добавить задачу"), color = colors.ink) }
                    if (filter != "upcoming") TextButton({ vm.filterTasks("upcoming") }) { Text(organizerLabel("Later", "Позже"), color = colors.ink) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ vm.taskPage.value = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text(organizerLabel("Previous", "Назад")) }
                TextButton({ vm.taskPage.value = page + 1 }, enabled = tasks.size == 50) { Text(organizerLabel("More tasks", "Ещё задачи")) }
            }
        }
    }
}

@Composable
private fun TaskGroup(label: String, count: Int, expanded: Boolean, colors: TodayColors, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = click).heightIn(min = 48.dp)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PlannerIcon(if (expanded) Glyph.Down else Glyph.Right, Modifier.size(15.dp), colors.ink)
        Text(label, color = colors.ink, style = TodayType.section)
        Text(count.toString(), Modifier.background(colors.peach, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
            style = TodayType.meta, color = colors.muted)
    }
}
