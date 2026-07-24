package mom.cosmism.textory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mom.cosmism.textory.ProjectCatalogUiState
import mom.cosmism.textory.UpdateUiState
import mom.cosmism.textory.data.AppTheme
import mom.cosmism.textory.data.ProjectSummary
import mom.cosmism.textory.ui.theme.TextoryPalette
import mom.cosmism.textory.ui.theme.textoryColors

@Composable
internal fun HomeScreen(
    catalog: ProjectCatalogUiState,
    updateState: UpdateUiState,
    appTheme: AppTheme,
    onAppThemeChanged: (AppTheme) -> Unit,
    onOpenProject: (String) -> Unit,
    onNewProject: (String) -> Unit,
    onImportDocument: () -> Unit,
    onOpenVersions: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDiscardChanges: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    var appMenuExpanded by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var newProjectDialogOpen by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    val newProjectFocusRequester = remember { FocusRequester() }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var discardTarget by remember { mutableStateOf<ProjectSummary?>(null) }

    Surface(color = TextoryPalette.Canvas, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Textory",
                    color = TextoryPalette.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        catalog.isLoading -> "Загрузка…"
                        else -> documentCountLabel(catalog.projects.size)
                    },
                    color = TextoryPalette.InkMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { appMenuExpanded = true }
                            .semantics { contentDescription = "Меню Textory" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⋮",
                            color = TextoryPalette.Ink,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    DropdownMenu(
                        expanded = appMenuExpanded,
                        onDismissRequest = { appMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Настройки") },
                            onClick = {
                                appMenuExpanded = false
                                settingsOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Проверить обновления") },
                            enabled = updateState !is UpdateUiState.Checking &&
                                updateState !is UpdateUiState.Downloading,
                            onClick = {
                                appMenuExpanded = false
                                onCheckForUpdates()
                            },
                        )
                    }
                }
            }

            when {
                catalog.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = TextoryPalette.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }

                catalog.projects.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Проектов пока нет",
                            color = TextoryPalette.Ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Создайте пустой Markdown-документ или импортируйте существующий.",
                            color = TextoryPalette.InkMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        top = 4.dp,
                        end = 16.dp,
                        bottom = 10.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(catalog.projects, key = ProjectSummary::id) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project.id) },
                            onVersions = { onOpenVersions(project.id) },
                            onRename = {
                                renameTarget = project
                                renameValue = project.fileName.removeSuffix(".md")
                            },
                            onDiscard = { discardTarget = project },
                            onDelete = { deleteTarget = project },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeAction(
                    text = "Новый документ",
                    primary = true,
                    onClick = {
                        newProjectName = ""
                        newProjectDialogOpen = true
                    },
                    modifier = Modifier.weight(1f),
                )
                HomeAction(
                    text = "Импорт .md",
                    primary = false,
                    onClick = onImportDocument,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (newProjectDialogOpen) {
        fun createProject() {
            newProjectDialogOpen = false
            onNewProject(newProjectName)
        }

        LaunchedEffect(Unit) {
            newProjectFocusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { newProjectDialogOpen = false },
            title = { Text("Новый документ") },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Название") },
                    placeholder = { Text("Без названия") },
                    supportingText = {
                        Text("Можно оставить пустым — расширение .md добавится автоматически.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { createProject() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(newProjectFocusRequester),
                )
            },
            confirmButton = {
                TextButton(onClick = { createProject() }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { newProjectDialogOpen = false }) { Text("Отмена") }
            },
            containerColor = TextoryPalette.Surface,
        )
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Настройки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Оформление",
                        color = TextoryPalette.InkMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AppTheme.entries.forEach { theme ->
                        ThemeOption(
                            theme = theme,
                            selected = theme == appTheme,
                            onClick = { onAppThemeChanged(theme) },
                        )
                    }
                    Text(
                        text = "Тема применяется сразу и сохраняется на этом устройстве.",
                        color = TextoryPalette.InkMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) { Text("Готово") }
            },
            containerColor = TextoryPalette.Surface,
        )
    }

    renameTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать проект") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Название") },
                    singleLine = true,
                    suffix = { Text(".md") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank(),
                    onClick = {
                        onRenameProject(project.id, renameValue)
                        renameTarget = null
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Отмена") }
            },
            containerColor = TextoryPalette.Surface,
        )
    }

    discardTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { discardTarget = null },
            title = { Text("Отбросить изменения?") },
            text = { Text("Проект «${project.fileName}» вернётся к последнему сохранению.") },
            confirmButton = {
                TextButton(onClick = {
                    onDiscardChanges(project.id)
                    discardTarget = null
                }) { Text("Отбросить") }
            },
            dismissButton = {
                TextButton(onClick = { discardTarget = null }) { Text("Отмена") }
            },
            containerColor = TextoryPalette.Surface,
        )
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить проект?") },
            text = {
                Text(
                    "«${project.fileName}» и все его сохранённые версии будут удалены с этого устройства.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(project.id)
                    deleteTarget = null
                }) { Text("Удалить", color = TextoryPalette.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
            containerColor = TextoryPalette.Surface,
        )
    }

}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val preview = textoryColors(theme)
    val (title, description) = when (theme) {
        AppTheme.LIGHT -> "Светлая" to "Чистая и спокойная"
        AppTheme.SEPIA -> "Sepia Paper" to "Тёплая книжная бумага"
        AppTheme.DARK -> "Тёмная" to "Мягкая для глаз"
    }
    Surface(
        color = if (selected) TextoryPalette.AccentHighlight else TextoryPalette.Canvas,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) TextoryPalette.Accent else TextoryPalette.Border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 9.dp, end = 4.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(preview.canvas),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 31.dp, height = 19.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(preview.surface),
                )
                Box(
                    modifier = Modifier
                        .padding(end = 5.dp, bottom = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(preview.accent)
                        .align(Alignment.BottomEnd),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = title,
                    color = TextoryPalette.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    color = TextoryPalette.InkMuted,
                    fontSize = 12.sp,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onVersions: () -> Unit,
    onRename: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        color = TextoryPalette.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TextoryPalette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TextoryPalette.AccentHighlight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "MD",
                    color = TextoryPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 4.dp),
            ) {
                Text(
                    text = project.fileName,
                    color = TextoryPalette.Ink,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (project.hasUnsavedChanges) TextoryPalette.Accent
                                else TextoryPalette.Border,
                            ),
                    )
                    Text(
                        text = buildString {
                            append(if (project.hasUnsavedChanges) "Есть изменения" else "Сохранено")
                            if (project.savedVersionCount > 0) {
                                append(" · ")
                                append(versionCountLabel(project.savedVersionCount))
                            }
                        },
                        color = TextoryPalette.InkMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true }
                        .semantics { contentDescription = "Действия с проектом" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "⋮", color = TextoryPalette.Ink, fontSize = 24.sp)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = TextoryPalette.Surface,
                ) {
                    if (project.savedVersionCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Все версии") },
                            onClick = { menuExpanded = false; onVersions() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    if (project.hasUnsavedChanges) {
                        DropdownMenuItem(
                            text = { Text("Отбросить изменения") },
                            onClick = { menuExpanded = false; onDiscard() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeAction(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (primary) TextoryPalette.Accent else TextoryPalette.Canvas,
        shape = RoundedCornerShape(13.dp),
        border = if (primary) null else BorderStroke(1.dp, TextoryPalette.Border),
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (primary) TextoryPalette.OnAccent else TextoryPalette.Ink,
                fontSize = 14.sp,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

private fun versionCountLabel(count: Int): String {
    val suffix = when {
        count % 100 in 11..14 -> "версий"
        count % 10 == 1 -> "версия"
        count % 10 in 2..4 -> "версии"
        else -> "версий"
    }
    return "$count $suffix"
}

internal fun documentCountLabel(count: Int): String {
    val suffix = when {
        count % 100 in 11..14 -> "документов"
        count % 10 == 1 -> "документ"
        count % 10 in 2..4 -> "документа"
        else -> "документов"
    }
    return "$count $suffix"
}
