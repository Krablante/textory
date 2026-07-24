package mom.cosmism.textory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import mom.cosmism.textory.EditorUiState
import mom.cosmism.textory.data.MIN_EDITOR_FONT_SIZE_SP
import mom.cosmism.textory.diff.TextChange
import mom.cosmism.textory.ui.theme.TextoryPalette
import kotlin.math.roundToInt

private enum class EditorMode { READ, EDIT }

@Composable
fun EditorScreen(
    state: EditorUiState,
    startInEditMode: Boolean = false,
    editorFontSizeSp: Float,
    onBack: () -> Unit,
    onTextChanged: (String) -> Unit,
    onEditorFontSizeChanged: (Float) -> Unit,
    onSave: () -> Unit,
    onExportDocument: () -> Unit,
    onNoticeConsumed: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedChange by remember { mutableStateOf<TextChange?>(null) }
    var toolbarExpanded by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf(if (startInEditMode) EditorMode.EDIT else EditorMode.READ) }
    var editorValue by remember { mutableStateOf(TextFieldValue(state.currentText)) }
    var navigationToken by remember(state.projectId) { mutableLongStateOf(0L) }
    var navigationRequest by remember(state.projectId) { mutableStateOf<ChangeNavigationRequest?>(null) }
    var comparisonDockHeightPx by remember(state.projectId) { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(state.documentGeneration) {
        editorValue = TextFieldValue(state.currentText, TextRange(state.currentText.length))
        selectedChange = null
    }
    LaunchedEffect(state.notice?.id) {
        state.notice?.let { notice ->
            snackbarHostState.showSnackbar(notice.text)
            onNoticeConsumed(notice.id)
        }
    }
    LaunchedEffect(state.diffSourceText) {
        selectedChange?.let { selected ->
            if (state.changes.none { it.id == selected.id }) selectedChange = null
        }
    }

    fun updateEditor(updated: TextFieldValue) {
        val textChanged = updated.text != editorValue.text
        editorValue = updated
        if (textChanged) {
            selectedChange = null
            onTextChanged(updated.text)
        }
    }

    val visibleChanges = if (state.diffSourceText == editorValue.text) state.changes else emptyList()
    val comparisonDockClearance = if (selectedChange != null && comparisonDockHeightPx > 0) {
        with(density) { comparisonDockHeightPx.toDp() } + 8.dp
    } else {
        0.dp
    }
    val changeCandidates = visibleChanges
        .filterNot(TextChange::isDeletion)
        .map { change ->
            ChangeHitCandidate(
                change = change,
                visibleSpanLength = change.currentEnd - change.currentStart,
            )
        }
    val selectionStart = minOf(editorValue.selection.start, editorValue.selection.end)
    val selectionEnd = maxOf(editorValue.selection.start, editorValue.selection.end)
    val activeEditorChange = if (editorValue.selection.collapsed) {
        ChangeHitResolver.atOffset(changeCandidates, editorValue.selection.start)
            ?: editorValue.selection.start.takeIf { it > 0 }
                ?.let { cursor -> ChangeHitResolver.atOffset(changeCandidates, cursor - 1) }
    } else {
        ChangeHitResolver.intersectingSelection(visibleChanges, selectionStart, selectionEnd)
    }

    fun showChange(change: TextChange, navigate: Boolean) {
        selectedChange = change
        if (!navigate) return
        val offset = (if (change.isDeletion) change.anchorOffset else change.currentStart)
            .coerceIn(0, editorValue.text.length)
        navigationToken += 1
        navigationRequest = ChangeNavigationRequest(offset = offset, token = navigationToken)
        if (mode == EditorMode.EDIT) {
            editorValue = editorValue.copy(selection = TextRange(offset))
        }
    }

    BackHandler(enabled = selectedChange != null) {
        selectedChange = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = TextoryPalette.Canvas,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(
                            if (mode == EditorMode.READ) {
                                READ_TOOLBAR_HEIGHT
                            } else if (toolbarExpanded) {
                                EDITOR_TOOLBAR_EXPANDED_HEIGHT
                            } else {
                                EDITOR_TOOLBAR_COLLAPSED_HEIGHT
                            },
                        ),
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                EditorTopBar(
                    fileName = state.fileName,
                hasUnsavedChanges = state.hasUnsavedChanges,
                changeCount = visibleChanges.size,
                isSaving = state.isSaving,
                onBack = onBack,
                onSave = onSave,
                onExportDocument = onExportDocument,
                onChangesClick = {
                    val target = selectedChange?.takeIf { selected ->
                        visibleChanges.any { it.id == selected.id }
                    } ?: visibleChanges.firstOrNull()
                    target?.let { showChange(it, navigate = true) }
                },
                )
                EditorModeSelector(mode = mode, onModeChanged = { mode = it })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TextoryPalette.Border),
                )
                if (mode == EditorMode.READ) {
                    MarkdownPreview(
                        markdown = editorValue.text,
                        changes = visibleChanges,
                        fontSizeSp = editorFontSizeSp,
                        onChangeTapped = { showChange(it, navigate = false) },
                        navigationRequest = navigationRequest,
                        bottomOverlayPadding = comparisonDockClearance,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    EditorBody(
                        value = editorValue,
                        changes = visibleChanges,
                        editorFontSizeSp = editorFontSizeSp,
                        bottomOverlayPadding = comparisonDockClearance,
                        onValueChange = ::updateEditor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (mode == EditorMode.EDIT) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding(),
            ) {
                MarkdownToolbar(
                    expanded = toolbarExpanded,
                    editorFontSizeSp = editorFontSizeSp,
                    onExpandedChange = { toolbarExpanded = it },
                    onEditorFontSizeChanged = onEditorFontSizeChanged,
                    onAction = { action -> updateEditor(MarkdownFormatter.apply(editorValue, action)) },
                )
            }
        } else {
            ReadAppearanceToolbar(
                fontSizeSp = editorFontSizeSp,
                onFontSizeChanged = onEditorFontSizeChanged,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
        if (mode == EditorMode.EDIT && activeEditorChange != null && selectedChange == null) {
            EditorHistoryButton(
                onClick = { showChange(activeEditorChange, navigate = false) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(end = 8.dp)
                    .padding(
                        bottom = (if (toolbarExpanded) {
                            EDITOR_TOOLBAR_EXPANDED_HEIGHT
                        } else {
                            EDITOR_TOOLBAR_COLLAPSED_HEIGHT
                        }) + 8.dp,
                    ),
            )
        }
        selectedChange?.let { change ->
            val selectedIndex = visibleChanges.indexOfFirst { it.id == change.id }
            ComparisonDock(
                change = change,
                editorFontSizeSp = editorFontSizeSp,
                position = selectedIndex.coerceAtLeast(0),
                total = visibleChanges.size.coerceAtLeast(1),
                onPrevious = {
                    if (selectedIndex > 0) showChange(visibleChanges[selectedIndex - 1], navigate = true)
                },
                onNext = {
                    if (selectedIndex in 0 until visibleChanges.lastIndex) {
                        showChange(visibleChanges[selectedIndex + 1], navigate = true)
                    }
                },
                onDismiss = { selectedChange = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp)
                    .padding(
                        bottom = if (mode == EditorMode.EDIT) {
                            (if (toolbarExpanded) {
                                EDITOR_TOOLBAR_EXPANDED_HEIGHT
                            } else {
                                EDITOR_TOOLBAR_COLLAPSED_HEIGHT
                            }) + 8.dp
                        } else {
                            READ_TOOLBAR_HEIGHT + 8.dp
                        },
                    )
                    .onSizeChanged { comparisonDockHeightPx = it.height },
            )
        }
    }

}

@Composable
private fun EditorTopBar(
    fileName: String,
    hasUnsavedChanges: Boolean,
    changeCount: Int,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onExportDocument: () -> Unit,
    onChangesClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CenteredAction(
            text = "←",
            description = "К документам",
            onClick = onBack,
            fontSize = 24.sp,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = fileName,
                color = TextoryPalette.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = changeCount > 0, onClick = onChangesClick)
                    .semantics {
                        if (changeCount > 0) {
                            contentDescription = "Показать изменения: $changeCount"
                        }
                    }
                    .padding(vertical = 1.dp, horizontal = 2.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (hasUnsavedChanges) TextoryPalette.Green else TextoryPalette.Border),
                )
                Text(
                    text = when {
                        !hasUnsavedChanges -> "Сохранено"
                        changeCount > 0 -> "Есть изменения · $changeCount"
                        else -> "Есть изменения"
                    },
                    color = TextoryPalette.InkMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        TopBarTextAction(
            label = if (isSaving) "Сохранение…" else "Сохранить",
            enabled = hasUnsavedChanges && !isSaving,
            color = TextoryPalette.Green,
            fontWeight = FontWeight.SemiBold,
            onClick = onSave,
        )
        TopBarTextAction(
            label = "Экспорт",
            color = TextoryPalette.InkMuted,
            fontWeight = FontWeight.Medium,
            onClick = onExportDocument,
        )
    }
}

@Composable
private fun CenteredAction(
    text: String,
    description: String,
    onClick: () -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = TextoryPalette.Ink, fontSize = fontSize)
    }
}

@Composable
private fun TopBarTextAction(
    label: String,
    color: Color,
    fontWeight: FontWeight,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = label,
            color = if (enabled) color else TextoryPalette.InkMuted,
            fontWeight = fontWeight,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun EditorModeSelector(mode: EditorMode, onModeChanged: (EditorMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 12.dp),
    ) {
        EditorMode.entries.forEach { item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onModeChanged(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = if (item == EditorMode.READ) "Читать" else "Править",
                    color = if (mode == item) TextoryPalette.Green else TextoryPalette.InkMuted,
                    fontSize = 13.sp,
                    fontWeight = if (mode == item) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (mode == item) TextoryPalette.Green else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun EditorBody(
    value: TextFieldValue,
    changes: List<TextChange>,
    editorFontSizeSp: Float,
    bottomOverlayPadding: androidx.compose.ui.unit.Dp,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(
        initialText = value.text,
        initialSelection = value.selection,
    )
    val stateEchoTracker = remember { EditorStateEchoTracker() }
    val editorScrollState = rememberScrollState()
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val editorLineHeightSp = editorFontSizeSp * EDITOR_LINE_HEIGHT_RATIO
    val visibleChanges = remember(value.text, changes) {
        changes.filter { it.currentStart >= 0 && it.currentEnd <= value.text.length }
    }
    val highlightRanges = remember(visibleChanges) {
        visibleChanges.filterNot(TextChange::isDeletion).map {
            DisplayHighlight(it.currentStart, it.currentEnd)
        }
    }
    val currentHighlightRanges = rememberUpdatedState(highlightRanges)
    val currentEditorFontSizeSp = rememberUpdatedState(editorFontSizeSp)
    val outputTransformation = remember {
        markdownEditorOutputTransformation(
            highlights = { currentHighlightRanges.value },
            fontSizeSp = { currentEditorFontSizeSp.value },
        )
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(value.text, value.selection) {
        if (stateEchoTracker.consumeEcho(EditorStateKey(value.text, value.selection))) {
            return@LaunchedEffect
        }
        val currentText = textFieldState.text.toString()
        val targetSelection = TextRange(
            value.selection.start.coerceIn(0, value.text.length),
            value.selection.end.coerceIn(0, value.text.length),
        )
        if (currentText != value.text) {
            textFieldState.edit {
                replace(0, length, value.text)
                selection = targetSelection
            }
        } else if (textFieldState.selection != targetSelection) {
            textFieldState.edit { selection = targetSelection }
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow {
            TextFieldValue(
                text = textFieldState.text.toString(),
                selection = textFieldState.selection,
                composition = textFieldState.composition,
            )
        }.distinctUntilChanged().collect { updatedValue ->
            stateEchoTracker.recordOutbound(EditorStateKey(updatedValue.text, updatedValue.selection))
            latestOnValueChange(updatedValue)
        }
    }

    LaunchedEffect(viewportHeightPx, layoutResult, textFieldState.selection, hasFocus) {
        val layout = layoutResult ?: return@LaunchedEffect
        if (!hasFocus || viewportHeightPx <= 0 || textFieldState.text.isEmpty()) return@LaunchedEffect
        delay(CURSOR_SCROLL_SETTLE_MS)
        val cursorOffset = textFieldState.selection.end.coerceIn(0, textFieldState.text.length)
        val cursor = layout.getCursorRect(cursorOffset)
        val targetScroll = cursorScrollTarget(
            cursorTop = cursor.top,
            cursorBottom = cursor.bottom,
            currentScroll = editorScrollState.value,
            viewportHeight = viewportHeightPx,
            margin = 0f,
            maxScroll = editorScrollState.maxValue,
        )
        if (kotlin.math.abs(targetScroll - editorScrollState.value) > 1) {
            editorScrollState.animateScrollTo(
                value = targetScroll,
                animationSpec = tween(durationMillis = CURSOR_SCROLL_DURATION_MS),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .padding(bottom = bottomOverlayPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        val editorWidth = maxWidth.coerceAtMost(720.dp)
        Box(
            modifier = Modifier
                .width(editorWidth)
                .fillMaxHeight()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            BasicTextField(
                state = textFieldState,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeightPx = it.height }
                    .onFocusChanged {
                        hasFocus = it.isFocused
                    },
                textStyle = TextStyle(
                    color = TextoryPalette.Ink,
                    fontSize = editorFontSizeSp.sp,
                    lineHeight = editorLineHeightSp.sp,
                    letterSpacing = 0.sp,
                ),
                cursorBrush = SolidColor(TextoryPalette.Green),
                lineLimits = TextFieldLineLimits.MultiLine(),
                outputTransformation = outputTransformation,
                onTextLayout = { getResult -> layoutResult = getResult() },
                scrollState = editorScrollState,
                decorator = TextFieldDecorator { innerTextField ->
                    Box {
                        if (textFieldState.text.isEmpty() && !hasFocus) {
                            Text(
                                "Начните писать…",
                                color = TextoryPalette.InkMuted,
                                fontSize = editorFontSizeSp.sp,
                                lineHeight = editorLineHeightSp.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

internal fun cursorScrollTarget(
    cursorTop: Float,
    cursorBottom: Float,
    currentScroll: Int,
    viewportHeight: Int,
    margin: Float,
    maxScroll: Int,
): Int {
    val current = currentScroll.toFloat()
    val target = when {
        cursorTop < current + margin -> cursorTop - margin
        cursorBottom > current + viewportHeight - margin -> cursorBottom - viewportHeight + margin
        else -> current
    }
    return target.roundToInt().coerceIn(0, maxScroll.coerceAtLeast(0))
}

internal data class EditorStateKey(
    val text: String,
    val selection: TextRange,
)

internal class EditorStateEchoTracker(
    private val maxPendingValues: Int = 64,
) {
    private val pendingValues = ArrayDeque<EditorStateKey>()

    fun recordOutbound(value: EditorStateKey) {
        pendingValues.addLast(value)
        while (pendingValues.size > maxPendingValues) pendingValues.removeFirst()
    }

    fun consumeEcho(value: EditorStateKey): Boolean {
        val index = pendingValues.indexOf(value)
        if (index < 0) return false
        repeat(index + 1) { pendingValues.removeFirst() }
        return true
    }
}

private const val CURSOR_SCROLL_SETTLE_MS = 48L
private const val CURSOR_SCROLL_DURATION_MS = 140
private const val EDITOR_LINE_HEIGHT_RATIO = 27f / 17f
private val EDITOR_TOOLBAR_EXPANDED_HEIGHT = 50.dp
private val EDITOR_TOOLBAR_COLLAPSED_HEIGHT = 34.dp
private val READ_TOOLBAR_HEIGHT = 48.dp

@Composable
private fun ReadAppearanceToolbar(
    fontSizeSp: Float,
    onFontSizeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = TextoryPalette.Toolbar,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(READ_TOOLBAR_HEIGHT),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(
                label = "A−",
                description = "Уменьшить размер текста, сейчас ${fontSizeSp.roundToInt()} sp",
                enabled = fontSizeSp > MIN_EDITOR_FONT_SIZE_SP,
                onClick = { onFontSizeChanged(fontSizeSp - 1f) },
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = "${fontSizeSp.roundToInt()} sp",
                color = TextoryPalette.InkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp),
            )
            ToolbarButton(
                label = "A+",
                description = "Увеличить размер текста, сейчас ${fontSizeSp.roundToInt()} sp",
                onClick = { onFontSizeChanged(fontSizeSp + 1f) },
                modifier = Modifier.width(72.dp),
            )
        }
    }
}

@Composable
private fun MarkdownToolbar(
    expanded: Boolean,
    editorFontSizeSp: Float,
    onExpandedChange: (Boolean) -> Unit,
    onEditorFontSizeChanged: (Float) -> Unit,
    onAction: (MarkdownAction) -> Unit,
) {
    Surface(
        color = TextoryPalette.Toolbar,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarActionGroup(
                    actions = listOf(MarkdownAction.HEADING, MarkdownAction.BOLD, MarkdownAction.ITALIC),
                    onAction = onAction,
                    modifier = Modifier.weight(3f),
                )
                Spacer(Modifier.width(2.dp))
                ToolbarActionGroup(
                    actions = listOf(
                        MarkdownAction.BULLET,
                        MarkdownAction.NUMBERED,
                        MarkdownAction.CHECKBOX,
                    ),
                    onAction = onAction,
                    modifier = Modifier.weight(3f),
                )
                Spacer(Modifier.width(2.dp))
                ToolbarActionGroup(
                    actions = listOf(MarkdownAction.CODE, MarkdownAction.LINK),
                    onAction = onAction,
                    modifier = Modifier.weight(2f),
                )
                Spacer(Modifier.width(2.dp))
                Surface(
                    color = TextoryPalette.Surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(42.dp),
                ) {
                    Row(Modifier.fillMaxSize()) {
                        ToolbarButton(
                            label = "A−",
                            description = "Уменьшить размер текста, сейчас ${editorFontSizeSp.roundToInt()} sp",
                            enabled = editorFontSizeSp > MIN_EDITOR_FONT_SIZE_SP,
                            onClick = { onEditorFontSizeChanged(editorFontSizeSp - 1f) },
                            modifier = Modifier.weight(1f),
                        )
                        ToolbarButton(
                            label = "A+",
                            description = "Увеличить размер текста, сейчас ${editorFontSizeSp.roundToInt()} sp",
                            onClick = { onEditorFontSizeChanged(editorFontSizeSp + 1f) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.width(2.dp))
                ToolbarButton(
                    label = "⌄",
                    description = "Скрыть панель форматирования",
                    onClick = { onExpandedChange(false) },
                    modifier = Modifier.width(32.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clickable { onExpandedChange(true) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Форматирование", color = TextoryPalette.InkMuted, fontSize = 12.sp)
                Text("⌃", color = TextoryPalette.InkMuted, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ToolbarActionGroup(
    actions: List<MarkdownAction>,
    onAction: (MarkdownAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = TextoryPalette.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(42.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            actions.forEach { action ->
                ToolbarButton(
                    label = action.label,
                    description = action.accessibilityLabel,
                    onClick = { onAction(action) },
                    modifier = Modifier.weight(1f),
                    bold = action == MarkdownAction.BOLD,
                    italic = action == MarkdownAction.ITALIC,
                )
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false,
    italic: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) TextoryPalette.Ink else TextoryPalette.InkMuted,
            fontSize = if (label == "<>") 13.sp else 15.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (italic) FontFamily.Serif else FontFamily.SansSerif,
        )
    }
}

@Composable
private fun EditorHistoryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = TextoryPalette.GreenBlock,
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .semantics {
                contentDescription = "Показать историю изменения под курсором"
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp)) {
                val centerY = size.height / 2f
                val inset = 4.dp.toPx()
                val wing = 4.dp.toPx()
                val strokeWidth = 2.5.dp.toPx()
                val right = size.width - inset
                drawLine(
                    color = TextoryPalette.Green,
                    start = Offset(inset, centerY),
                    end = Offset(right, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = TextoryPalette.Green,
                    start = Offset(inset, centerY),
                    end = Offset(inset + wing, centerY - wing),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = TextoryPalette.Green,
                    start = Offset(inset, centerY),
                    end = Offset(inset + wing, centerY + wing),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = TextoryPalette.Green,
                    start = Offset(right, centerY),
                    end = Offset(right - wing, centerY - wing),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = TextoryPalette.Green,
                    start = Offset(right, centerY),
                    end = Offset(right - wing, centerY + wing),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun ComparisonDock(
    change: TextChange,
    editorFontSizeSp: Float,
    position: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentLabel: String = "Сейчас",
    previousLabel: String = "Было",
    additionDescription: String = "добавлено",
    deletionDescription: String = "удалено",
) {
    var dragOffsetY by remember(change.id) { mutableFloatStateOf(0f) }
    val dismissThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val layout = comparisonDockLayout(change)
    val replacementMode = replacementPresentation(change)
    Surface(
        color = TextoryPalette.Surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .semantics { contentDescription = "Панель сравнения" },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(change.id, dismissThreshold) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (dragOffsetY >= dismissThreshold) onDismiss() else dragOffsetY = 0f
                            },
                            onDragCancel = { dragOffsetY = 0f },
                        )
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp)
                        .width(34.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextoryPalette.Border),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сравнение",
                            color = TextoryPalette.Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = buildString {
                                append("${position + 1} из $total")
                                when (layout) {
                                    ComparisonDockLayout.ADDITION -> append(" · $additionDescription")
                                    ComparisonDockLayout.DELETION -> append(" · $deletionDescription")
                                    ComparisonDockLayout.REPLACEMENT -> Unit
                                }
                            },
                            color = TextoryPalette.InkMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    DockHeaderButton(
                        label = "‹",
                        description = "Предыдущее изменение",
                        enabled = position > 0,
                        onClick = onPrevious,
                    )
                    DockHeaderButton(
                        label = "›",
                        description = "Следующее изменение",
                        enabled = position < total - 1,
                        onClick = onNext,
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(TextoryPalette.Border),
                    )
                    Spacer(Modifier.width(4.dp))
                    DockHeaderButton(
                        label = "×",
                        description = "Закрыть сравнение",
                        onClick = onDismiss,
                    )
                }
            }
            when (layout) {
                ComparisonDockLayout.ADDITION -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        CompactComparisonBlock(
                            label = currentLabel,
                            text = change.currentText,
                            counterpart = change.previousText,
                            containerColor = TextoryPalette.GreenBlock,
                            detailColor = TextoryPalette.GreenDetail,
                            fontSizeSp = editorFontSizeSp,
                            maxChars = 300,
                            maxLines = 8,
                        )
                    }
                }
                ComparisonDockLayout.DELETION -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        CompactComparisonBlock(
                            label = previousLabel,
                            text = change.previousText,
                            counterpart = change.currentText,
                            containerColor = TextoryPalette.RedHighlight,
                            detailColor = TextoryPalette.RedDetail,
                            fontSizeSp = editorFontSizeSp,
                            maxChars = 300,
                            maxLines = 8,
                        )
                    }
                }
                ComparisonDockLayout.REPLACEMENT -> {
                    val contentModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                    if (replacementMode == ReplacementPresentation.SIDE_BY_SIDE) {
                        Row(
                            modifier = contentModifier,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            CompactComparisonBlock(
                                label = currentLabel,
                                text = change.currentText,
                                counterpart = change.previousText,
                                containerColor = TextoryPalette.GreenBlock,
                                detailColor = TextoryPalette.GreenDetail,
                                fontSizeSp = editorFontSizeSp,
                                maxChars = 140,
                                maxLines = 7,
                                emptyText = "Фрагмент удалён",
                                modifier = Modifier.weight(1f),
                            )
                            CompactComparisonBlock(
                                label = previousLabel,
                                text = change.previousText,
                                counterpart = change.currentText,
                                containerColor = TextoryPalette.RedHighlight,
                                detailColor = TextoryPalette.RedDetail,
                                fontSizeSp = editorFontSizeSp,
                                maxChars = 140,
                                maxLines = 7,
                                emptyText = "Фрагмента не было",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(
                            modifier = contentModifier,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            CompactComparisonBlock(
                                label = currentLabel,
                                text = change.currentText,
                                counterpart = change.previousText,
                                containerColor = TextoryPalette.GreenBlock,
                                detailColor = TextoryPalette.GreenDetail,
                                fontSizeSp = editorFontSizeSp,
                                maxChars = 220,
                                maxLines = 5,
                                emptyText = "Фрагмент удалён",
                            )
                            CompactComparisonBlock(
                                label = previousLabel,
                                text = change.previousText,
                                counterpart = change.currentText,
                                containerColor = TextoryPalette.RedHighlight,
                                detailColor = TextoryPalette.RedDetail,
                                fontSizeSp = editorFontSizeSp,
                                maxChars = 220,
                                maxLines = 5,
                                emptyText = "Фрагмента не было",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockHeaderButton(
    label: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) TextoryPalette.Ink else TextoryPalette.Border,
            fontSize = 24.sp,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun CompactComparisonBlock(
    label: String,
    text: String,
    counterpart: String,
    containerColor: Color,
    detailColor: Color,
    fontSizeSp: Float,
    maxChars: Int,
    maxLines: Int,
    emptyText: String = "",
    modifier: Modifier = Modifier,
) {
    val excerpt = remember(text, counterpart, maxChars) {
        comparisonExcerpt(text, counterpart, maxChars)
    }
    val displayFontSizeSp = comparisonDockFontSize(fontSizeSp, text.length, maxChars)
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = TextoryPalette.InkMuted,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (text.isEmpty()) AnnotatedString(emptyText) else detailedDifference(excerpt, detailColor),
                color = if (text.isEmpty()) TextoryPalette.InkMuted else TextoryPalette.Ink,
                fontStyle = if (text.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                fontSize = displayFontSizeSp.sp,
                lineHeight = (displayFontSizeSp * 1.24f).sp,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun detailedDifference(excerpt: ComparisonExcerpt, detailColor: Color): AnnotatedString =
    buildAnnotatedString {
        append(excerpt.text)
        excerpt.highlights.forEach { range ->
            addStyle(SpanStyle(background = detailColor), range.start, range.endExclusive)
        }
    }

internal fun comparisonDockFontSize(preferredSp: Float, sourceLength: Int, maxChars: Int): Float {
    val base = preferredSp.coerceIn(12f, 15f)
    return when {
        sourceLength > maxChars * 2 -> (base - 2f).coerceAtLeast(11f)
        sourceLength > maxChars -> (base - 1f).coerceAtLeast(11.5f)
        else -> base
    }
}
