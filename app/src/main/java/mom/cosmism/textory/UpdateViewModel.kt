package mom.cosmism.textory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mom.cosmism.textory.update.GitHubRelease
import mom.cosmism.textory.update.GitHubReleaseUpdater
import mom.cosmism.textory.update.InstallLaunchResult
import mom.cosmism.textory.update.SemanticVersion
import mom.cosmism.textory.update.UpdateException
import mom.cosmism.textory.update.UpdateInstaller

internal sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Current(val version: String) : UpdateUiState
    data class Available(val release: GitHubRelease) : UpdateUiState
    data class Downloading(val release: GitHubRelease, val progress: Int) : UpdateUiState
    data class Ready(
        val release: GitHubRelease,
        val apkFile: File,
        val requiresInstallPermission: Boolean = false,
    ) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

internal class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updater = GitHubReleaseUpdater(application)
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private var operation: Job? = null
    private var activeCheckIsSilent = false

    init {
        if (updater.hasValidatedInternet()) startUpdateCheck(silent = true)
    }

    fun checkForUpdates() {
        if (operation?.isActive == true) {
            if (activeCheckIsSilent) {
                activeCheckIsSilent = false
                _state.value = UpdateUiState.Checking
            }
            return
        }
        startUpdateCheck(silent = false)
    }

    private fun startUpdateCheck(silent: Boolean) {
        activeCheckIsSilent = silent
        if (!silent) _state.value = UpdateUiState.Checking
        operation = viewModelScope.launch {
            try {
                val release = updater.fetchLatestRelease()
                val current = SemanticVersion.parse(BuildConfig.VERSION_NAME)
                    ?: throw UpdateException("Некорректная версия установленного приложения")
                operation = null
                _state.value = resolveUpdateCheckState(release, current, activeCheckIsSilent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operation = null
                _state.value = if (activeCheckIsSilent) {
                    UpdateUiState.Idle
                } else {
                    UpdateUiState.Failed(error.userMessage("Не удалось проверить обновления"))
                }
            }
        }
    }

    fun downloadUpdate() {
        val release = (_state.value as? UpdateUiState.Available)?.release ?: return
        if (operation?.isActive == true) return
        activeCheckIsSilent = false
        _state.value = UpdateUiState.Downloading(release, progress = 0)
        operation = viewModelScope.launch {
            try {
                val apkFile = updater.downloadAndVerify(release) { progress ->
                    _state.value = UpdateUiState.Downloading(release, progress)
                }
                operation = null
                _state.value = UpdateUiState.Ready(release, apkFile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operation = null
                _state.value = UpdateUiState.Failed(error.userMessage("Не удалось скачать обновление"))
            }
        }
    }

    fun installUpdate() {
        val ready = _state.value as? UpdateUiState.Ready ?: return
        try {
            when (UpdateInstaller.launch(getApplication(), ready.apkFile)) {
                InstallLaunchResult.INSTALLER -> Unit
                InstallLaunchResult.PERMISSION_SETTINGS -> {
                    _state.value = ready.copy(requiresInstallPermission = true)
                }
            }
        } catch (error: Exception) {
            _state.value = UpdateUiState.Failed(error.userMessage("Не удалось открыть установщик Android"))
        }
    }

    fun cancelOperation() {
        operation?.cancel()
        operation = null
        _state.value = UpdateUiState.Idle
    }

    fun dismiss() {
        if (_state.value !is UpdateUiState.Checking && _state.value !is UpdateUiState.Downloading) {
            _state.value = UpdateUiState.Idle
        }
    }

    private fun Exception.userMessage(fallback: String): String =
        (this as? UpdateException)?.message?.takeIf(String::isNotBlank) ?: fallback
}

internal fun resolveUpdateCheckState(
    release: GitHubRelease,
    current: SemanticVersion,
    silent: Boolean,
): UpdateUiState = when {
    release.version > current -> UpdateUiState.Available(release)
    silent -> UpdateUiState.Idle
    else -> UpdateUiState.Current(current.toString())
}
