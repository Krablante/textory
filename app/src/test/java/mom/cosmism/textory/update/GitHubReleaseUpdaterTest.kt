package mom.cosmism.textory.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdaterTest {
    @Test
    fun semanticVersionParsesTagsAndLocalSuffixes() {
        assertEquals(SemanticVersion(0, 11, 0), SemanticVersion.parse("v0.11.0"))
        assertEquals(SemanticVersion(0, 11, 0), SemanticVersion.parse("0.11.0-local"))
        assertEquals(SemanticVersion(2, 0, 1), SemanticVersion.parse("2.0.1+35"))
        assertNull(SemanticVersion.parse("release-11"))
    }

    @Test
    fun semanticVersionComparesNumericComponents() {
        assertTrue(SemanticVersion(0, 11, 0) > SemanticVersion(0, 10, 9))
        assertTrue(SemanticVersion(1, 0, 0) > SemanticVersion(0, 99, 99))
        assertEquals(0, SemanticVersion(3, 2, 1).compareTo(SemanticVersion(3, 2, 1)))
    }

    @Test
    fun releaseParserChoosesProductionTextoryApk() {
        val release = GitHubReleaseUpdater.parseRelease(
            """
            {
              "tag_name": "v0.12.0",
              "name": "Textory 0.12.0",
              "assets": [
                {
                  "name": "Textory-0.12.0.aab",
                  "browser_download_url": "https://github.com/Krablante/textory/releases/download/v0.12.0/Textory.aab",
                  "size": 120,
                  "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                },
                {
                  "name": "Textory-0.12.0-debug.apk",
                  "browser_download_url": "https://github.com/Krablante/textory/releases/download/v0.12.0/Textory-debug.apk",
                  "size": 130,
                  "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                },
                {
                  "name": "Textory-0.12.0.apk",
                  "browser_download_url": "https://github.com/Krablante/textory/releases/download/v0.12.0/Textory.apk",
                  "size": 140,
                  "digest": "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(SemanticVersion(0, 12, 0), release.version)
        assertEquals("Textory 0.12.0", release.title)
        assertEquals("Textory-0.12.0.apk", release.asset.name)
        assertEquals(140L, release.asset.size)
    }

    @Test
    fun releaseParserRejectsForeignDownloadHost() {
        val error = assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.parseRelease(
                """
                {
                  "tag_name": "v0.12.0",
                  "assets": [{
                    "name": "Textory-0.12.0.apk",
                    "browser_download_url": "https://example.com/Textory.apk",
                    "size": 140
                  }]
                }
                """.trimIndent(),
            )
        }
        assertEquals("В релизе нет подходящего APK", error.message)
    }

    @Test
    fun digestVerificationAcceptsGitHubFormatAndRejectsMismatch() {
        val digest = "a".repeat(64)
        GitHubReleaseUpdater.verifyDigest(digest, "sha256:$digest")

        assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.verifyDigest("b".repeat(64), "sha256:$digest")
        }
        assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.verifyDigest(digest, "md5:abcd")
        }
        assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.verifyDigest(digest, declared = null)
        }
    }
}
