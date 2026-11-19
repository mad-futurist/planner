package app.planner.features

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.core.designsystem.*
import app.planner.domain.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal fun CollapsedComposer(vm: PlannerViewModel, colors: TodayColors, open: () -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val hint = stringResource(R.string.today_capture_hint)
    Row(
        Modifier.fillMaxWidth().padding(start = 21.dp, end = 21.dp, top = 6.dp, bottom = 13.dp)
            .clip(RoundedCornerShape(29.dp)).background(colors.composer)
            .clickable(role = Role.Button, onClick = open).testTag("capture-bar")
            .heightIn(min = 58.dp).padding(horizontal = 21.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlannerIcon(Glyph.Plus, Modifier.size(23.dp), colors.composerText)
        Text(draft.ifBlank { hint }, Modifier.weight(1f), style = TodayType.body,
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (draft.isEmpty()) colors.composerMuted else colors.composerText)
        PlannerIcon(Glyph.Right, Modifier.size(19.dp), colors.composerText)
    }
}

@Composable
internal fun SemanticComposer(vm: PlannerViewModel, colors: TodayColors, close: () -> Unit) {
    val raw by vm.draft.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val initialKind by vm.captureKind.collectAsStateWithLifecycle()
    val private by vm.privateCapture.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val ru = LocalConfiguration.current.locales[0].language == "ru"
    val privacyLabel = stringResource(if (private) R.string.today_private_mode else R.string.today_cloud_mode)
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val reading = remember(raw, today) { readDraft(raw, today) }
    var manual by rememberSaveable { mutableStateOf(initialKind != null) }
    var kindChoice by rememberSaveable { mutableStateOf(initialKind?.let { if (it.startsWith("task")) "TASK" else it.uppercase() }) }
    var dayChoice by rememberSaveable(raw) { mutableStateOf<String?>(if (initialKind == "task") today.toString() else null) }
    var timeChoice by rememberSaveable(raw) { mutableStateOf<String?>(null) }
    var numberAsDate by rememberSaveable(raw) { mutableStateOf(false) }
    var skipDay by rememberSaveable(raw) { mutableStateOf(false) }
    var skipTime by rememberSaveable(raw) { mutableStateOf(false) }
    var selectedToken by remember(raw) { mutableStateOf<DraftToken?>(null) }
    val kind = kindChoice?.let(CaptureKind::valueOf) ?: if (manual) CaptureKind.NOTE else reading.kind
    val temporal = kind in setOf(CaptureKind.TASK, CaptureKind.EVENT, CaptureKind.DEADLINE)
    val hour = reading.ambiguousHour.takeIf { temporal && !manual }
    val time = if (temporal && !skipTime) timeChoice?.let(LocalTime::parse) ?: reading.time.takeUnless { numberAsDate || manual } else null
    val day = if (temporal && !skipDay) dayChoice?.let(LocalDate::parse) ?: reading.day.takeUnless { manual || numberAsDate } else null
    val monthDay = hour.takeIf { numberAsDate }
    val interpretation = CaptureInterpretation(kind, day, time)
    val dismiss = { keyboard?.hide(); vm.captureKind.value = null; close() }
    val openDate: () -> Unit = {
        val seed = day ?: monthDay?.let { today.withDayOfMonth(it) } ?: today
        DatePickerDialog(context, { _, year, month, date ->
            dayChoice = LocalDate.of(year, month + 1, monthDay ?: date).toString()
            skipDay = false
        }, seed.year, seed.monthValue - 1, seed.dayOfMonth).show()
    }
    val openTime: () -> Unit = {
        TimePickerDialog(context, { _, h, m -> timeChoice = LocalTime.of(h, m).toString(); skipTime = false },
            time?.hour ?: 12, time?.minute ?: 0, true).show()
    }
    val setMeaning: (Boolean) -> Unit = { asDate ->
        if (numberAsDate != asDate) {
            numberAsDate = asDate
            timeChoice = null
            skipTime = false
            dayChoice = null
            skipDay = false
        }
        selectedToken = null
    }
    val chooseType: (CaptureKind?) -> Unit = { choice ->
        vm.captureKind.value = null
        manual = choice != null
        kindChoice = choice?.name
        dayChoice = null
        timeChoice = null
        skipDay = false
        skipTime = false
        numberAsDate = false
        selectedToken = null
    }
    BackHandler { dismiss() }
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }

    Scaffold(
        Modifier.fillMaxSize().background(colors.canvas).todayBackdrop(colors),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            Box(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 9.dp)) {
                val enabled = raw.isNotBlank() && !saving
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(colors.composer)
                        .clickable(enabled = enabled, role = Role.Button) {
                            vm.capture(interpretation = interpretation.takeUnless { kind == CaptureKind.NOTE && !manual }, onSaved = dismiss)
                        }.testTag("send").heightIn(min = 56.dp).padding(horizontal = 23.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (ru) "Сохранить" else "Save", Modifier.weight(1f), color = if (enabled) colors.composerText else colors.composerMuted, style = TodayType.section)
                    PlannerIcon(Glyph.Right, Modifier.size(25.dp), colors.composerText)
                }
            }
        },
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(dismiss, Modifier.semantics { contentDescription = if (ru) "Закрыть" else "Close composer" }) {
                    PlannerIcon(Glyph.Close, Modifier.size(24.dp), colors.ink)
                }
                Spacer(Modifier.weight(1f))
                ComposerTypeMenu("${if (manual) if (ru) "Вручную" else "Manual" else if (ru) "Авто" else "Auto"} · ${kind.label(ru)}", colors, ru, chooseType)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Text(if (ru) "Запись мысли" else "Capture a thought", Modifier.padding(start = 7.dp),
                    style = TodayType.title.copy(fontSize = 32.sp), color = colors.ink)
                Image(painterResource(R.drawable.today_cat_peek), null,
                    Modifier.align(Alignment.BottomEnd).padding(top = 42.dp, end = 8.dp).width(minOf(138.dp, maxWidth * .41f)).aspectRatio(768f / 591f))
            }
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(30.dp)).padding(19.dp)) {
                val tokens = if (manual) emptyList() else reading.tokens
                SemanticEditor(raw, vm::setDraft, tokens, selectedToken, { selectedToken = it }, colors, focus) { token ->
                    when (token.meaning) {
                        TokenMeaning.TIME -> if (hour != null) {
                            ComposerOption(if (ru) "Время" else "Time", colors, !numberAsDate) { setMeaning(false) }
                            ComposerOption(if (ru) "Дата" else "Date", colors, numberAsDate) { setMeaning(true) }
                        } else ComposerOption(if (ru) "Изменить время" else "Edit time", colors) { selectedToken = null; openTime() }
                        TokenMeaning.DATE, TokenMeaning.DEADLINE ->
                            ComposerOption(if (ru) "Изменить дату" else "Edit date", colors) { selectedToken = null; openDate() }
                        TokenMeaning.ENTITY, TokenMeaning.IDEA, TokenMeaning.LINK ->
                            ComposerTypeMenu(kind.label(ru), colors, ru, chooseType)
                    }
                }
                if (tokens.isNotEmpty()) {
                    var tokensMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton({ tokensMenu = true }) { Text(if (ru) "Распознанные фрагменты" else "Recognized fragments", color = colors.ink) }
                        DropdownMenu(tokensMenu, { tokensMenu = false }) {
                            tokens.distinctBy { it.range }.forEach { token ->
                                DropdownMenuItem({ Text(raw.substring(token.range)) }, { tokensMenu = false; selectedToken = token })
                            }
                        }
                    }
                }
                TextButton({ vm.setPrivacy(!private) }, Modifier.testTag("privacy").semantics {
                    contentDescription = privacyLabel
                }) { Text(privacyLabel, color = colors.muted) }
            }
            if (hour != null && !numberAsDate && time == null && !skipTime) {
                ComposerPanel(if (ru) "Утром или вечером?" else "Morning or evening?", colors) {
                    listOf(hour % 12, hour % 12 + 12).forEach { h ->
                        val option = LocalTime.of(h, 0)
                        ComposerOption(option.format(DateTimeFormatter.ofPattern("HH:mm")), colors) { timeChoice = option.toString() }
                    }
                }
            } else if (temporal && day == null && !skipDay && (time != null || monthDay != null)) {
                val prompt = if (monthDay != null) {
                    if (ru) "В каком месяце $monthDay-е число?" else "Which month contains day $monthDay?"
                } else if (ru) "На какой день?" else "Which day?"
                ComposerPanel(prompt, colors) {
                    if (monthDay == null) {
                        ComposerOption(if (ru) "Сегодня" else "Today", colors) { dayChoice = today.toString() }
                        ComposerOption(if (ru) "Завтра" else "Tomorrow", colors) { dayChoice = today.plusDays(1).toString() }
                    }
                    ComposerOption(if (ru) "Другая дата" else "Choose date", colors, click = openDate)
                    ComposerOption(if (ru) "Пропустить" else "Skip", colors) { skipDay = true }
                }
            }
            if (raw.isNotBlank()) {
                ComposerPanel(if (ru) "Интерпретация" else "Interpretation", colors, lavender = true) {
                    ComposerTypeMenu(kind.label(ru), colors, ru, chooseType)
                    if (temporal) {
                        ComposerField(time?.toString(), if (ru) "Без времени" else "No time", colors, ru, openTime) {
                            skipTime = true; timeChoice = null
                        }
                        ComposerField(day?.toString(), if (ru) "Дата не указана" else "No date", colors, ru, openDate) {
                            skipDay = true; dayChoice = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SemanticEditor(
    raw: String, onChange: (String) -> Unit, tokens: List<DraftToken>, selected: DraftToken?,
    select: (DraftToken?) -> Unit, colors: TodayColors, focus: FocusRequester,
    tokenMenu: @Composable (DraftToken) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val boxes = layout?.takeIf { it.layoutInput.text.text == raw }?.let { measured ->
        tokens.flatMap { token ->
            val first = measured.getLineForOffset(token.range.first)
            val last = measured.getLineForOffset(token.range.last)
            (first..last).mapNotNull { line ->
                val start = maxOf(token.range.first, measured.getLineStart(line))
                val end = minOf(token.range.last + 1, measured.getLineEnd(line, visibleEnd = true))
                if (start >= end) null else token to measured.getPathForRange(start, end).getBounds()
            }
        }
    }.orEmpty()
    BasicTextField(
        raw, onChange, Modifier.fillMaxWidth().focusRequester(focus).testTag("capture"),
        textStyle = TodayType.body.copy(color = colors.ink, fontSize = 20.sp, lineHeight = 30.sp),
        cursorBrush = SolidColor(colors.amberInk), onTextLayout = { layout = it },
        decorationBox = { field ->
            Box(Modifier.fillMaxWidth().heightIn(min = 75.dp).drawBehind {
                boxes.forEach { (token, bounds) ->
                    val tint = token.tint()
                    val margin = 2.dp.toPx()
                    val round = CornerRadius(9.dp.toPx())
                    val origin = bounds.topLeft.copy(x = bounds.left - margin)
                    val dimensions = bounds.size.copy(width = bounds.width + margin * 2)
                    drawRoundRect(tint.copy(alpha = .15f), origin, dimensions, round)
                    drawRoundRect(tint, origin, dimensions, round, style = Stroke(1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
            }) {
                if (raw.isEmpty()) Text(stringResource(R.string.today_capture_hint), color = colors.muted, style = TodayType.body)
                field()
                boxes.forEach { (token, bounds) ->
                    Box(
                        Modifier.offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                            .width(with(density) { bounds.width.toDp() }).height(with(density) { bounds.height.toDp() })
                            .clickable(role = Role.Button) { select(token) }.semantics { contentDescription = raw.substring(token.range) },
                    ) {
                        if (selected == token && boxes.firstOrNull { it.first == token }?.second == bounds) {
                            Popup(AboveToken, onDismissRequest = { select(null) }, properties = PopupProperties(focusable = true)) {
                                Row(
                                    Modifier.background(colors.surface, CircleShape).border(1.dp, colors.line, CircleShape).padding(5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    tokenMenu(token)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ComposerTypeMenu(label: String, colors: TodayColors, ru: Boolean, choose: (CaptureKind?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ComposerOption(label, colors, true) { open = true }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(if (ru) "Автоматически" else "Automatic interpretation") }, { open = false; choose(null) })
            CaptureKind.entries.forEach { kind ->
                DropdownMenuItem({ Text(kind.label(ru)) }, { open = false; choose(kind) })
            }
        }
    }
}

@Composable
private fun ComposerField(value: String?, empty: String, colors: TodayColors, ru: Boolean, edit: () -> Unit, clear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ComposerOption(value ?: empty, colors) { if (value == null) edit() else open = true }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(if (ru) "Изменить" else "Change") }, { open = false; edit() })
            DropdownMenuItem({ Text(if (ru) "Очистить" else "Clear") }, { open = false; clear() })
        }
    }
}

private object AboveToken : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset((anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(0))
}

private fun DraftToken.tint(): Color = when (meaning) {
    TokenMeaning.ENTITY, TokenMeaning.LINK -> Color(0xFF5794CB)
    TokenMeaning.IDEA -> Color(0xFFA78AD0)
    else -> Color(0xFFD9A235)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerPanel(title: String, colors: TodayColors, lavender: Boolean = false, body: @Composable FlowRowScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface)
        .then(if (lavender) Modifier.todayCollectionArt(colors) else Modifier).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.ink, style = TodayType.section)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = body)
    }
}

@Composable
private fun ComposerOption(label: String, colors: TodayColors, selected: Boolean = false, click: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) colors.peachDeep else colors.surface)
            .border(1.dp, colors.line, CircleShape).clickable(role = Role.Button, onClick = click)
            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = colors.ink, style = TodayType.meta) }
}

private fun CaptureKind.label(ru: Boolean): String = when (this) {
    CaptureKind.NOTE -> if (ru) "Заметка" else "Note"
    CaptureKind.TASK -> if (ru) "Задача" else "Task"
    CaptureKind.EVENT -> if (ru) "Событие" else "Event"
    CaptureKind.DEADLINE -> if (ru) "Дедлайн" else "Deadline"
    CaptureKind.IDEA -> if (ru) "Идея" else "Idea"
    CaptureKind.INFORMATION -> if (ru) "Информация" else "Information"
}
