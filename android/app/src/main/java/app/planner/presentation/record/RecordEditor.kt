package app.planner.presentation.record

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.planner.domain.model.Record
import app.planner.presentation.common.editor.DateControl
import app.planner.presentation.common.editor.KindPicker
import app.planner.presentation.common.editor.TimeControl
import com.google.gson.*
import java.time.LocalDate

@Composable
internal fun RecordEditor(state: RecordUiState, save: (Record) -> Unit, complete: (Record) -> Unit, openRelated: ((String) -> Unit)? = null, close: () -> Unit) {
    val record = state.record ?: return
    val expectedVersion by rememberSaveable(record.id) { mutableStateOf(record.version) }
    var title by rememberSaveable(record.id) { mutableStateOf(record.title) }
    var kind by rememberSaveable(record.id) { mutableStateOf(record.baseKind) }
    var planned by rememberSaveable(record.id) { mutableStateOf(record.plannedFor) }
    var dueDate by rememberSaveable(record.id) { mutableStateOf(record.dueDate) }
    var dueAt by rememberSaveable(record.id) { mutableStateOf(record.dueAt) }
    var start by rememberSaveable(record.id) { mutableStateOf(record.startAt) }
    var end by rememberSaveable(record.id) { mutableStateOf(record.endAt) }
    var reminder by rememberSaveable(record.id) { mutableStateOf(record.remindAt) }
    var customTypeId by rememberSaveable(record.id) { mutableStateOf(record.customTypeId) }
    var typeVersion by rememberSaveable(record.id) { mutableStateOf(record.typeSchemaVersion) }
    val types = state.types
    val definition = types.find { it.id == customTypeId && it.schemaVersion == typeVersion }
    val originalFields = remember(record.id) { JsonParser.parseString(record.fieldsJson).asJsonObject }
    val fields = rememberSaveable(record.id) { mutableStateOf(originalFields.entrySet().associate { it.key to if (it.value.isJsonPrimitive) it.value.asString else it.value.toString() }) }
    var typeMenu by remember { mutableStateOf(false) }
    val saving = state.saving
    var error by remember { mutableStateOf<String?>(null) }
    val note = state.note
    val specs = definition?.let { JsonParser.parseString(it.fieldDefinitions).asJsonObject } ?: JsonObject()
    val provenanceKeys = setOf("developedFrom", "splitFrom", "suggestionJobId")
    val keys = (specs.keySet() + fields.value.keys).distinct().filterNot { it in provenanceKeys }
    val originId = (originalFields.get("developedFrom") ?: originalFields.get("splitFrom"))?.takeIf { it.isJsonPrimitive }?.asString
    val origin = state.origin
    AlertDialog(onDismissRequest = close, title = { Text("Details") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            note?.let { Text("Original note", style = MaterialTheme.typography.labelSmall); Text(it.originalContent, style = MaterialTheme.typography.bodySmall) }
            if (origin != null && openRelated != null) TextButton({ openRelated(origin!!.id) }) { Text("From ${origin!!.baseKind}: ${origin!!.title}", maxLines = 2) }
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            KindPicker(kind) { kind = it; customTypeId = null; typeVersion = null }
            Box {
                TextButton({ typeMenu = true }) { Text("Type: ${definition?.name ?: "General"}") }
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    DropdownMenuItem({ Text("General") }, { customTypeId = null; typeVersion = null; typeMenu = false })
                    for (type in types.groupBy { it.id }.values.map { rows -> rows.maxBy { it.schemaVersion } }.filter { it.enabled && it.baseKind == kind }) DropdownMenuItem({ Text(type.name) }, { customTypeId = type.id; typeVersion = type.schemaVersion; typeMenu = false })
                }
            }
            if (kind == "task") {
                DateControl("Plan for", planned, { planned = it })
                TextButton({ planned = LocalDate.now().toString() }) { Text("Plan for today") }
                DateControl("Deadline day", dueDate, { dueDate = it; if (it != null) dueAt = null })
                TimeControl("Deadline time", dueAt, record.timezone, { dueAt = it; if (it != null) dueDate = null })
            }
            if (kind in setOf("task", "event")) {
                TimeControl("Scheduled start", start, record.timezone, { start = it })
                TimeControl("Scheduled end", end, record.timezone, { end = it })
            }
            if (record.remindAt != null) {
                TimeControl("Reminder time", reminder, record.timezone, { reminder = it })
                Text("Reminder time is stored; scheduled reminder notifications are not enabled in this MVP.", style = MaterialTheme.typography.bodySmall)
            }
            for (key in keys) {
                val spec = specs.get(key)?.asJsonObject
                val label = spec?.get("label")?.asString?.takeIf { it.isNotBlank() } ?: key.replaceFirstChar { it.uppercase() }
                val value = fields.value[key].orEmpty()
                val type = spec?.get("type")?.asString ?: "text"
                when (type) {
                    "boolean", "enum" -> {
                        var menu by remember(key) { mutableStateOf(false) }
                        val choices = if (type == "boolean") listOf("true", "false") else spec?.getAsJsonArray("values")?.map { it.asString }.orEmpty()
                        Box { TextButton({ menu = true }) { Text("$label: ${if (value.isEmpty()) "Not set" else if (type == "boolean") (if (value == "true") "Yes" else "No") else value}") }; DropdownMenu(menu, { menu = false }) { for (choice in listOf("") + choices) DropdownMenuItem({ Text(if (choice.isEmpty()) "Not set" else if (type == "boolean") (if (choice == "true") "Yes" else "No") else choice) }, { fields.value = fields.value + (key to choice); menu = false }) } }
                    }
                    "date" -> DateControl(label, value.takeIf { it.isNotEmpty() }, { fields.value = fields.value + (key to it.orEmpty()) })
                    "datetime" -> TimeControl(label, value.takeIf { it.isNotEmpty() }, record.timezone, { fields.value = fields.value + (key to it.orEmpty()) })
                    "quantity" -> {
                        val quantity = runCatching { JsonParser.parseString(value).asJsonObject }.getOrDefault(JsonObject())
                        val amount = quantity.get("value")?.asString.orEmpty()
                        val unit = quantity.get("unit")?.asString ?: spec?.get("unit")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                        OutlinedTextField(amount, { changed -> val updated = quantity.deepCopy().apply { addProperty("value", changed); addProperty("unit", unit) }; fields.value = fields.value + (key to updated.toString()) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(unit, { changed -> fields.value = fields.value + (key to quantity.deepCopy().apply { addProperty("unit", changed) }.toString()) }, label = { Text("Unit") }, enabled = spec?.get("unit")?.isJsonNull != false, modifier = Modifier.fillMaxWidth())
                    }
                    else -> OutlinedTextField(value, { fields.value = fields.value + (key to it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = if (type in setOf("number", "duration")) KeyboardType.Decimal else if (type == "URL") KeyboardType.Uri else KeyboardType.Text))
                }
            }
            if (customTypeId == null && "details" !in keys) TextButton({ fields.value = fields.value + ("details" to "") }) { Text("Add a detail") }
            Row {
                TextButton({ save(record.copy(version = expectedVersion, pinned = !record.pinned)) }) { Text(if (record.pinned) "Unpin" else "Pin") }
                TextButton({ save(record.copy(version = expectedVersion, state = if (record.state == "archived") "pending" else "archived", completedAt = null)) }) { Text(if (record.state == "archived") "Restore item" else "Archive") }
            }
            if (record.baseKind == "task") TextButton({ complete(record) }) { Text(if (record.state == "completed") "Restore task" else "Complete task") }
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }, confirmButton = { TextButton({
        run {
            try {
                require(title.isNotBlank() && title.length <= 1000) { "Enter a title of up to 1,000 characters." }
                val json = JsonObject()
                for ((key, value) in fields.value) {
                    val spec = specs.get(key)?.asJsonObject
                    val original = originalFields.get(key)
                    // Unknown provider fields retain their JSON type when unrelated edits are saved.
                    if (spec == null && original != null && value == if (original.isJsonPrimitive) original.asString else original.toString()) { json.add(key, original.deepCopy()); continue }
                    if (value.isEmpty()) { require(spec?.get("required")?.asBoolean != true) { "Fill in $key." }; continue }
                    when (spec?.get("type")?.asString ?: "text") {
                        "number", "duration" -> { val number = value.toDoubleOrNull(); require(number != null && number.isFinite()) { "Enter a number for $key." }; json.addProperty(key, number) }
                        "boolean" -> json.addProperty(key, value == "true")
                        "quantity" -> { val quantity = JsonParser.parseString(value).asJsonObject; val number = quantity.get("value")?.asString?.toDoubleOrNull(); require(number != null && number.isFinite() && quantity.get("unit")?.asString?.isNotBlank() == true) { "Enter a number and unit for $key." }; quantity.addProperty("value", number); json.add(key, quantity) }
                        else -> json.addProperty(key, value)
                    }
                }
                save(record.copy(version = expectedVersion, title = title.trim(), baseKind = kind, customTypeId = customTypeId, typeSchemaVersion = typeVersion, fieldsJson = json.toString(), plannedFor = if (kind == "task") planned else null, dueDate = if (kind == "task") dueDate else null, dueAt = if (kind == "task") dueAt else null, startAt = if (kind in setOf("task", "event")) start else null, endAt = if (kind in setOf("task", "event")) end else null, remindAt = reminder, state = if (kind != "task" && record.state == "completed") "pending" else record.state, completedAt = if (kind == "task") record.completedAt else null))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Couldn't save. Your changes are still here." }
        }
    }, enabled = !saving) { Text("Save") } }, dismissButton = { TextButton(close) { Text("Cancel") } })
}
