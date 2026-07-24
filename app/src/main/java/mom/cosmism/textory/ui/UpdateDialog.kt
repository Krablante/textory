package mom.cosmism.textory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import mom.cosmism.textory.UpdateUiState
import mom.cosmism.textory.ui.theme.TextoryPalette

@Composable
internal fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> ProgressDialog(
            title = "Проверка обновлений",
            status = "Связываемся с GitHub…",
            onCancel = onCancel,
        )

        is UpdateUiState.Current -> MessageDialog(
            title = "Обновлений нет",
            message = "Установлена актуальная версия Textory ${state.version}.",
            onDismiss = onDismiss,
        )

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Доступно обновление") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Textory ${state.release.version}",
                        color = TextoryPalette.Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.release.title != "Textory ${state.release.version}") {
                        Text(
                            text = state.release.title,
                            color = TextoryPalette.InkMuted,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        text = "APK: ${formatBytes(state.release.asset.size)}",
                        color = TextoryPalette.InkMuted,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDownload) { Text("Скачать") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Позже") }
            },
        )

        is UpdateUiState.Downloading -> ProgressDialog(
            title = "Загрузка Textory ${state.release.version}",
            status = "${state.progress}%",
            onCancel = onCancel,
        )

        is UpdateUiState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(if (state.requiresInstallPermission) "Разрешите установку" else "Обновление готово")
            },
            text = {
                Text(
                    if (state.requiresInstallPermission) {
                        "Разрешите Textory устанавливать приложения из этого источника, вернитесь сюда и нажмите «Продолжить»."
                    } else {
                        "APK Textory ${state.release.version} проверен по версии, пакету, SHA-256 и release-сертификату."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onInstall) {
                    Text(if (state.requiresInstallPermission) "Продолжить" else "Установить")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Позже") }
            },
        )

        is UpdateUiState.Failed -> MessageDialog(
            title = "Обновление не выполнено",
            message = state.message,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ProgressDialog(
    title: String,
    status: String,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = TextoryPalette.Accent,
                    strokeWidth = 2.dp,
                )
                Text(status, modifier = Modifier.padding(vertical = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        },
    )
}

@Composable
private fun MessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Хорошо") }
        },
    )
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f МБ".format(Locale.ROOT, bytes.toDouble() / (1024L * 1024L))
    bytes >= 1024L -> "%.0f КБ".format(Locale.ROOT, bytes.toDouble() / 1024L)
    else -> "$bytes Б"
}
