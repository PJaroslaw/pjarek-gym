package com.pjarek.gym

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper

class ExerciseDatasetSyncIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var sync: ExerciseDatasetSyncService
    @Autowired private lateinit var importer: ExerciseCatalogImporter
    @Autowired private lateinit var mapper: JsonMapper

    private val tempRoots = mutableListOf<Path>()

    @AfterEach
    fun restoreBundledCatalog() {
        importer.import(mapper.readTree(javaClass.getResourceAsStream("/exercises.json")!!))
        tempRoots.forEach { root ->
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
        tempRoots.clear()
    }

    @Test
    fun `downloads a changed archive caches its images and sends the etag on the next check`() {
        val root = Files.createTempDirectory("exercise-sync-test")
        tempRoots.add(root)
        val requests = AtomicInteger()
        val archive = archive("Synced exercise", includeImage = true)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/catalog.zip") { exchange ->
            requests.incrementAndGet()
            if (exchange.requestHeaders.getFirst("If-None-Match") == "\"dataset-1\"") {
                exchange.responseHeaders.add("ETag", "\"dataset-1\"")
                exchange.sendResponseHeaders(304, -1)
            } else {
                exchange.responseHeaders.add("ETag", "\"dataset-1\"")
                exchange.sendResponseHeaders(200, archive.size.toLong())
                exchange.responseBody.use { it.write(archive) }
            }
        }
        server.start()
        try {
            val url = URI("http://127.0.0.1:${server.address.port}/catalog.zip")
            assertEquals(ExerciseDatasetSyncService.SyncResult.Updated(100), sync.sync(url, root))
            assertTrue(Files.isRegularFile(root.resolve("current/exercises/Shared/0.jpg")))
            assertEquals("synced exercise", jdbc.queryForObject("SELECT lower(name) FROM exercise WHERE source_id='sync-exercise-0'", String::class.java))
            assertEquals(false, jdbc.queryForObject("SELECT active FROM exercise WHERE name='Incline Dumbbell Press'", Boolean::class.java))
            assertEquals(ExerciseDatasetSyncService.SyncResult.NotModified, sync.sync(url, root))
            assertEquals(2, requests.get())
            assertFalse(Files.exists(root.resolve("exercises")))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `rejects an incomplete archive without deactivating the current catalog`() {
        val root = Files.createTempDirectory("exercise-sync-invalid")
        tempRoots.add(root)
        val activeBefore = jdbc.queryForObject("SELECT count(*) FROM exercise WHERE active=TRUE", Int::class.java)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val archive = archive("Broken exercise", includeImage = false)
        server.createContext("/catalog.zip") { exchange ->
            exchange.responseHeaders.add("ETag", "\"bad-dataset\"")
            exchange.sendResponseHeaders(200, archive.size.toLong())
            exchange.responseBody.use { it.write(archive) }
        }
        server.start()
        try {
            val url = URI("http://127.0.0.1:${server.address.port}/catalog.zip")
            assertThrows(IllegalArgumentException::class.java) { sync.sync(url, root) }
            assertEquals(activeBefore, jdbc.queryForObject("SELECT count(*) FROM exercise WHERE active=TRUE", Int::class.java))
            assertFalse(Files.exists(root.resolve("current")))
        } finally {
            server.stop(0)
        }
    }

    private fun archive(firstName: String, includeImage: Boolean): ByteArray {
        val catalog = (0 until 100).joinToString(",", "[", "]") { index ->
            val name = if (index == 0) firstName else "Synced exercise $index"
            """{"id":"sync-exercise-$index","name":"$name","category":"strength","equipment":"barbell","level":"beginner","force":"push","mechanic":"compound","primaryMuscles":["chest"],"secondaryMuscles":[],"instructions":["Lift."],"images":["Shared/0.jpg"]}"""
        }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("free-exercise-db-main/dist/exercises.json"))
            zip.write(catalog.toByteArray())
            zip.closeEntry()
            if (includeImage) {
                zip.putNextEntry(ZipEntry("free-exercise-db-main/exercises/Shared/0.jpg"))
                zip.write(byteArrayOf(1, 2, 3, 4))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
