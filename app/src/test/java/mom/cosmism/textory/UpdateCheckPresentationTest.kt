package mom.cosmism.textory

import mom.cosmism.textory.update.GitHubRelease
import mom.cosmism.textory.update.ReleaseAsset
import mom.cosmism.textory.update.SemanticVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPresentationTest {
    @Test
    fun silentCheckOnlySurfacesANewerRelease() {
        val current = SemanticVersion(0, 11, 1)

        val newer = resolveUpdateCheckState(release("0.11.2"), current, silent = true)
        val same = resolveUpdateCheckState(release("0.11.1"), current, silent = true)
        val older = resolveUpdateCheckState(release("0.10.9"), current, silent = true)

        assertTrue(newer is UpdateUiState.Available)
        assertEquals(UpdateUiState.Idle, same)
        assertEquals(UpdateUiState.Idle, older)
    }

    @Test
    fun manualCheckStillConfirmsCurrentVersion() {
        val current = SemanticVersion(0, 11, 2)
        assertEquals(
            UpdateUiState.Current("0.11.2"),
            resolveUpdateCheckState(release("0.11.2"), current, silent = false),
        )
    }

    private fun release(version: String) = GitHubRelease(
        version = requireNotNull(SemanticVersion.parse(version)),
        title = "Textory $version",
        asset = ReleaseAsset(
            name = "Textory-$version.apk",
            downloadUrl = "https://github.com/Krablante/textory/releases/download/v$version/Textory-$version.apk",
            size = 1,
            digest = "sha256:${"a".repeat(64)}",
        ),
    )
}
