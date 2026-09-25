package com.pjarek.gym

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.zip.ZipFile
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class ExerciseDatasetSyncService(
    private val mapper: JsonMapper,
    private val importer: ExerciseCatalogImporter,
    @Value("\${app.exercise-image-dir}") private val imageDir: String,
    @Value("\${app.exercise-sync.url}") private val archiveUrl: String,
    @Value("\${app.exercise-sync.on-startup:true}") private val enabledOnStartup: Boolean,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun syncOnStartup() {
        if (enabledOnStartup) syncSafely()
    }

    @Scheduled(cron = "\${app.exercise-sync.cron:-}", zone = "\${app.exercise-sync.zone:UTC}")
    fun syncWeekly() = syncSafely()

    fun syncSafely() {
        try {
            sync(URI.create(archiveUrl), Path.of(imageDir).toAbsolutePath().normalize())
        } catch (exception: Exception) {
            logger.warn("Exercise catalog sync failed; the current catalog and local images remain available", exception)
        }
    }

    internal fun sync(uri: URI, root: Path): SyncResult {
        Files.createDirectories(root)
        val current = root.resolve("current")
        val currentImages = current.resolve("exercises")
        val legacyImages = root.resolve("exercises")
        val etagFile = root.resolve(".upstream-etag")
        val etag = if (Files.exists(currentImages) && Files.exists(etagFile)) Files.readString(etagFile).trim() else null
        val requestBuilder = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET()
        if (!etag.isNullOrBlank()) requestBuilder.header("If-None-Match", etag)
        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() == 304) {
            response.body().close()
            return SyncResult.NotModified
        }
        if (response.statusCode() != 200) {
            response.body().close()
            error("Upstream returned HTTP ${response.statusCode()}")
        }

        val downloaded = Files.createTempFile(root, ".exercise-catalog-", ".zip")
        val stage = root.resolve(".staging-${UUID.randomUUID()}")
        try {
            response.body().use { input -> copyBounded(input, downloaded, MAX_ARCHIVE_BYTES) }
            val upstreamEtag = response.headers().firstValue("ETag").orElse(null)
            ZipFile(downloaded.toFile()).use { zip ->
                val catalogEntry = zip.entries().asSequence().firstOrNull { it.name.endsWith("/dist/exercises.json") || it.name == "dist/exercises.json" }
                    ?: error("Upstream archive does not contain dist/exercises.json")
                require(catalogEntry.size in 1..MAX_CATALOG_BYTES) { "Upstream catalog has an invalid size" }
                val catalog = zip.getInputStream(catalogEntry).use { mapper.readTree(it) }
                val imagePaths = importer.validate(catalog)
                val stagedImages = stage.resolve("exercises")
                Files.createDirectories(stagedImages)
                copyExistingImages(currentImages.takeIf(Files::exists) ?: legacyImages.takeIf(Files::exists), stagedImages)
                val imageEntries = zip.entries().asSequence().associateBy { entry -> imagePath(entry.name) }
                var downloadedImageBytes = 0L
                imagePaths.forEach { imagePath ->
                    val entry = imageEntries[imagePath]
                    if (entry != null) {
                        require(entry.size in 1..MAX_IMAGE_BYTES) { "Upstream image has an invalid size: $imagePath" }
                        downloadedImageBytes += entry.size
                        require(downloadedImageBytes <= MAX_EXTRACTED_IMAGE_BYTES) { "Upstream images exceed the allowed total size" }
                        val target = stagedImages.resolve(imagePath).normalize()
                        require(target.startsWith(stagedImages)) { "Upstream image path is invalid" }
                        Files.createDirectories(target.parent)
                        zip.getInputStream(entry).use { input -> Files.newOutputStream(target).use { output -> copyBounded(input, output, MAX_IMAGE_BYTES) } }
                    }
                    require(Files.isRegularFile(stagedImages.resolve(imagePath))) { "Upstream image is missing: $imagePath" }
                }

                val versionId = upstreamEtag?.let(::digest) ?: digest(downloaded)
                val versions = root.resolve("versions")
                Files.createDirectories(versions)
                val version = versions.resolve(versionId)
                if (Files.exists(version)) deleteRecursively(version)
                try {
                    Files.move(stage, version, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(stage, version)
                }
                importer.import(catalog)
                activate(root, version)
                if (upstreamEtag != null) writeAtomically(etagFile, upstreamEtag)
                deleteRecursively(legacyImages)
                Files.list(versions).use { paths -> paths.filter { it != version }.forEach(::deleteRecursively) }
                return SyncResult.Updated(catalog.size())
            }
        } finally {
            Files.deleteIfExists(downloaded)
            deleteRecursively(stage)
        }
    }

    private fun activate(root: Path, version: Path) {
        val temporaryLink = root.resolve(".current-${UUID.randomUUID()}")
        try {
            Files.createSymbolicLink(temporaryLink, root.relativize(version))
            try {
                Files.move(temporaryLink, root.resolve("current"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryLink, root.resolve("current"), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryLink)
        }
    }

    private fun copyExistingImages(source: Path?, destination: Path) {
        if (source == null || !Files.exists(source)) return
        Files.walk(source).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { file ->
                val target = destination.resolve(source.relativize(file)).normalize()
                require(target.startsWith(destination))
                Files.createDirectories(target.parent)
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun copyBounded(input: InputStream, outputPath: Path, maximum: Long) {
        Files.newOutputStream(outputPath).use { output -> copyBounded(input, output, maximum) }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, maximum: Long) {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximum) { "Upstream exercise data exceeds the allowed size" }
            output.write(buffer, 0, count)
        }
    }

    private fun writeAtomically(path: Path, value: String) {
        val temporary = Files.createTempFile(path.parent, ".etag-", ".part")
        try {
            Files.writeString(temporary, value)
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun imagePath(entryName: String): String? = when {
        entryName.startsWith("exercises/") -> entryName.removePrefix("exercises/")
        "/exercises/" in entryName -> entryName.substringAfter("/exercises/")
        else -> null
    }

    private fun digest(path: Path): String = Files.newInputStream(path).use { input ->
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            sha.update(buffer, 0, count)
        }
        sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    sealed interface SyncResult {
        data object NotModified : SyncResult
        data class Updated(val exerciseCount: Int) : SyncResult
    }

    companion object {
        private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        private const val MAX_CATALOG_BYTES = 20L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
        private const val MAX_EXTRACTED_IMAGE_BYTES = 1024L * 1024 * 1024
    }
}
