package mom.cosmism.textory.data

import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class StoredVersion(
    val id: String,
    val savedAt: Long,
)

class VersionArchive(private val directory: File) {
    fun list(): List<StoredVersion> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .mapNotNull { file ->
                val match = FILE_PATTERN.matchEntire(file.name) ?: return@mapNotNull null
                StoredVersion(id = file.name, savedAt = match.groupValues[1].toLong())
            }
            .sortedByDescending(StoredVersion::savedAt)
    }

    fun archive(text: String, savedAt: Long = System.currentTimeMillis()): StoredVersion {
        directory.mkdirs()
        val id = "$savedAt-${UUID.randomUUID()}.md.gz"
        val target = File(directory, id)
        val temporary = File.createTempFile("pending-", ".tmp", directory)
        try {
            GZIPOutputStream(temporary.outputStream().buffered()).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(text)
            }
            check(temporary.renameTo(target)) { "Could not publish version snapshot" }
        } finally {
            temporary.delete()
        }
        return StoredVersion(id = id, savedAt = savedAt)
    }

    fun read(id: String): String {
        require(FILE_PATTERN.matches(id)) { "Invalid version id" }
        val file = File(directory, id)
        require(file.isFile && file.parentFile == directory) { "Version does not exist" }
        GZIPInputStream(file.inputStream().buffered()).bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(16 * 1024)
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                require(result.length + read <= MAX_VERSION_CHARS) { "Version is too large" }
                result.append(buffer, 0, read)
            }
            return result.toString()
        }
    }

    fun clear() {
        directory.listFiles().orEmpty().forEach(File::delete)
        directory.delete()
    }

    companion object {
        private val FILE_PATTERN = Regex("^(\\d+)-[0-9a-f-]+\\.md\\.gz$")
        private const val MAX_VERSION_CHARS = 10 * 1024 * 1024
    }
}
