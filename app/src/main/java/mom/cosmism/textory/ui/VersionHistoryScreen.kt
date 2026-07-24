package mom.cosmism.textory.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mom.cosmism.textory.VersionHistoryUiState
import mom.cosmism.textory.VersionItem
import mom.cosmism.textory.ui.theme.TextoryPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VersionHistoryScreen(
    state: VersionHistoryUiState,
    fontSizeSp: Float,
    onBack: () -> Unit,
    onSelectVersion: (Int) -> Unit,
    onUseSelectedVersion: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = state.selectedIndex) { state.items.size }
    val selectedItem = state.items.getOrNull(pagerState.currentPage) ?: state.selectedItem
    val selectedText = selectedItem?.let { state.texts[it.id] }

    LaunchedEffect(pagerState.settledPage, state.items.size) {
        if (state.items.isNotEmpty()) onSelectVersion(pagerState.settledPage)
    }

    Scaffold(
        containerColor = TextoryPalette.Canvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VersionTopBar(
                item = selectedItem,
                textAvailable = selectedText != null,
                onBack = onBack,
                onCopy = {
                    selectedText?.let { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Textory", text))
                        scope.launch { snackbarHostState.showSnackbar("Версия скопирована") }
                    }
                },
            )
        },
        bottomBar = {
            VersionSwitcher(
                items = state.items,
                selectedIndex = pagerState.currentPage,
                canUse = selectedItem?.isCurrent == false && selectedText != null,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                onUse = {
                    onSelectVersion(pagerState.currentPage)
                    onUseSelectedVersion()
                },
            )
        },
    ) { contentPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            key = { page -> state.items[page].id },
        ) { page ->
            val item = state.items[page]
            val text = state.texts[item.id]
            if (text == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = TextoryPalette.Green,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Только просмотр",
                        color = TextoryPalette.InkMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 18.dp, top = 10.dp),
                    )
                    MarkdownPreview(
                        markdown = text,
                        changes = emptyList(),
                        fontSizeSp = fontSizeSp,
                        onChangeTapped = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionTopBar(
    item: VersionItem?,
    textAvailable: Boolean,
    onBack: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(66.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Назад" },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "←", color = TextoryPalette.Ink, fontSize = 24.sp)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = if (item?.isCurrent == true) "Текущая версия" else "Просмотр версии",
                color = TextoryPalette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item?.let(::formatVersionSubtitle).orEmpty(),
                color = TextoryPalette.InkMuted,
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onCopy, enabled = textAvailable) {
            Text(
                text = "Копировать",
                color = if (textAvailable) TextoryPalette.Green else TextoryPalette.InkMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun VersionSwitcher(
    items: List<VersionItem>,
    selectedIndex: Int,
    canUse: Boolean,
    onSelect: (Int) -> Unit,
    onUse: () -> Unit,
) {
    val listState = rememberLazyListState()
    val chipLabels = remember(items) { buildVersionChipLabels(items) }
    LaunchedEffect(selectedIndex) {
        if (items.isNotEmpty()) listState.animateScrollToItem(selectedIndex.coerceIn(items.indices))
    }

    Surface(
        color = TextoryPalette.Surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        border = BorderStroke(1.dp, TextoryPalette.Border),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(34.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TextoryPalette.Border),
            )
            Text(
                text = "Все версии",
                color = TextoryPalette.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
            Text(
                text = "Листайте влево или вправо",
                color = TextoryPalette.InkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 12.dp),
            )
            LazyRow(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    val selected = index == selectedIndex
                    Surface(
                        color = if (selected) TextoryPalette.GreenBlock else TextoryPalette.Surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) TextoryPalette.Green else TextoryPalette.Border,
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .clickable { onSelect(index) },
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 17.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = chipLabels[index],
                                color = if (selected) TextoryPalette.Green else TextoryPalette.Ink,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selectedIndex + 1} из ${items.size}",
                    color = TextoryPalette.InkMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onUse, enabled = canUse) {
                    Text(
                        text = "Использовать эту версию",
                        color = if (canUse) TextoryPalette.Green else TextoryPalette.InkMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun formatVersionChip(item: VersionItem): String {
    if (item.isCurrent) return "Сейчас"
    val savedAt = item.savedAt ?: return "Версия"
    val dateTime = Instant.ofEpochMilli(savedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = LocalDate.now()
    return when (dateTime.toLocalDate()) {
        today -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "Вчера"
        else -> "${dateTime.dayOfMonth} ${MONTHS[dateTime.monthValue - 1]}"
    }
}

private fun buildVersionChipLabels(items: List<VersionItem>): List<String> {
    val baseLabels = items.map(::formatVersionChip)
    val duplicateLabels = baseLabels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return items.mapIndexed { index, item ->
        val base = baseLabels[index]
        if (item.isCurrent || base !in duplicateLabels || item.savedAt == null) return@mapIndexed base
        Instant.ofEpochMilli(item.savedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }
}

private fun formatVersionSubtitle(item: VersionItem): String {
    if (item.isCurrent) return "Сейчас"
    val savedAt = item.savedAt ?: return "Сохранённая версия"
    val dateTime = Instant.ofEpochMilli(savedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val day = when (dateTime.toLocalDate()) {
        LocalDate.now() -> "Сегодня"
        LocalDate.now().minusDays(1) -> "Вчера"
        else -> "${dateTime.dayOfMonth} ${MONTHS[dateTime.monthValue - 1]}"
    }
    return "$day, ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private val MONTHS = listOf(
    "янв.", "февр.", "марта", "апр.", "мая", "июня",
    "июля", "авг.", "сент.", "окт.", "нояб.", "дек.",
)
