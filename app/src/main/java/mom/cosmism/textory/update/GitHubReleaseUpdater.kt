package mom.cosmism.textory.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mom.cosmism.textory.BuildConfig
import org.json.JSONObject

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$")

        fun parse(value: String): SemanticVersion? {
            val match = PATTERN.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
            )
        }
    }
}

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val digest: String?,
)

internal data class GitHubRelease(
    val version: SemanticVersion,
    val title: String,
    val asset: ReleaseAsset,
)

internal class UpdateException(message: String) : Exception(message)

internal class GitHubReleaseUpdater(
    private val context: Context,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {
    fun hasValidatedInternet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = openConnection(latestReleaseUrl, GITHUB_ACCEPT)
        try {
            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> throw UpdateException("Публичные релизы Textory пока не найдены")
                HttpURLConnection.HTTP_FORBIDDEN,
                HTTP_TOO_MANY_REQUESTS,
                -> throw UpdateException("GitHub временно ограничил число запросов. Попробуйте позже")
                else -> throw UpdateException("GitHub вернул ошибку $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndVerify(
        release: GitHubRelease,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (release.asset.size !in 1..MAX_APK_BYTES) {
            throw UpdateException("Некорректный размер APK")
        }

        val updateDirectory = File(context.cacheDir, UPDATE_CACHE_DIRECTORY).apply { mkdirs() }
        updateDirectory.listFiles()?.forEach(File::delete)
        val partialFile = File(updateDirectory, "Textory-${release.version}.apk.part")
        val apkFile = File(updateDirectory, "Textory-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openConnection(release.asset.downloadUrl, APK_ACCEPT)

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw UpdateException("Не удалось скачать APK: ошибка ${connection.responseCode}")
            }
            val responseLength = connection.contentLengthLong
            if (responseLength > MAX_APK_BYTES) throw UpdateException("APK слишком большой")

            var downloaded = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                partialFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        currentCoroutineContext().ensureActive()
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) throw UpdateException("APK слишком большой")
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        val progress = ((downloaded * 100L) / release.asset.size).toInt().coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }

            if (downloaded != release.asset.size) {
                throw UpdateException("Размер загруженного APK не совпадает с релизом")
            }
            verifyDigest(digest.digest().toHex(), release.asset.digest)
            if (!partialFile.renameTo(apkFile)) throw UpdateException("Не удалось подготовить APK")
            verifyPackage(apkFile, release.version)
            onProgress(100)
            apkFile
        } catch (error: Exception) {
            partialFile.delete()
            apkFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyPackage(apkFile: File, releaseVersion: SemanticVersion) {
        val packageInfo = packageArchiveInfo(apkFile)
            ?: throw UpdateException("Android не распознал загруженный APK")
        if (packageInfo.packageName != context.packageName) {
            throw UpdateException("APK принадлежит другому приложению")
        }

        val packageVersion = SemanticVersion.parse(packageInfo.versionName.orEmpty())
        if (packageVersion != releaseVersion) {
            throw UpdateException("Версия внутри APK не совпадает с GitHub-релизом")
        }
        if (PackageInfoCompat.getLongVersionCode(packageInfo) <= BuildConfig.VERSION_CODE.toLong()) {
            throw UpdateException("APK не новее установленной версии")
        }

        val signerDigests = signingCertificates(packageInfo).map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }
        if (EXPECTED_SIGNER_SHA256 !in signerDigests) {
            throw UpdateException("APK подписан неизвестным сертификатом")
        }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apkFile: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun signingCertificates(packageInfo: PackageInfo) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }

    private fun openConnection(url: String, accept: String): HttpURLConnection {
        val uri = URI(url)
        if (uri.scheme != "https") throw UpdateException("Небезопасный адрес обновления")
        if (uri.host !in ALLOWED_DOWNLOAD_HOSTS && uri.host != GITHUB_API_HOST) {
            throw UpdateException("Неизвестный сервер обновления")
        }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Textory/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Krablante/textory/releases/latest"
        private const val GITHUB_API_HOST = "api.github.com"
        private const val GITHUB_ACCEPT = "application/vnd.github+json"
        private const val APK_ACCEPT = "application/octet-stream"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val UPDATE_CACHE_DIRECTORY = "updates"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_APK_BYTES = 100L * 1024L * 1024L
        private const val EXPECTED_SIGNER_SHA256 =
            "31ebc562eeda99a8746e091bdcbf680819aa7c02e11dabae6315bc691826fb78"
        private val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        internal fun parseRelease(json: String): GitHubRelease {
            val root = JSONObject(json)
            val tag = root.optString("tag_name")
            val version = SemanticVersion.parse(tag)
                ?: throw UpdateException("У релиза некорректная версия: $tag")
            val assets = root.optJSONArray("assets")
                ?: throw UpdateException("В релизе нет APK")
            val candidates = buildList {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val name = asset.optString("name")
                    if (!name.endsWith(".apk", ignoreCase = true) || name.contains("debug", ignoreCase = true)) {
                        continue
                    }
                    val downloadUrl = asset.optString("browser_download_url")
                    val uri = runCatching { URI(downloadUrl) }.getOrNull() ?: continue
                    if (uri.scheme != "https" || uri.host != "github.com") continue
                    add(
                        ReleaseAsset(
                            name = name,
                            downloadUrl = downloadUrl,
                            size = asset.optLong("size", -1L),
                            digest = asset.optString("digest").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
            val apk = candidates.firstOrNull { it.name.startsWith("Textory-", ignoreCase = true) }
                ?: candidates.firstOrNull()
                ?: throw UpdateException("В релизе нет подходящего APK")
            return GitHubRelease(
                version = version,
                title = root.optString("name").ifBlank { "Textory $version" },
                asset = apk,
            )
        }

        internal fun verifyDigest(actual: String, declared: String?) {
            if (declared == null) throw UpdateException("В GitHub-релизе нет SHA-256 digest")
            val expected = declared.removePrefix("sha256:").lowercase()
            if (!expected.matches(Regex("[0-9a-f]{64}"))) {
                throw UpdateException("GitHub вернул некорректный digest")
            }
            if (actual.lowercase() != expected) {
                throw UpdateException("Контрольная сумма APK не совпадает")
            }
        }
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
