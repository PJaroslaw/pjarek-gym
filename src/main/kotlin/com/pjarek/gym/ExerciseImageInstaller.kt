package com.pjarek.gym

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class ExerciseImageInstaller(@Value("\${app.exercise-image-dir}") private val imageDir: String) {
    fun install() {
        val root = Path.of(imageDir).toAbsolutePath().normalize()
        Files.createDirectories(root)
        val marker = root.resolve(".dataset-a859101d633a01c4a1a920d6a8ce41dabba0705f")
        if (Files.exists(marker)) return
        GZIPInputStream(ClassPathResource("exercise-images.tar.gz").inputStream.buffered()).use { input ->
            while (true) {
                val header = input.readNBytes(512)
                if (header.size != 512 || header.all { it.toInt() == 0 }) break
                val name = field(header, 0, 100)
                val prefix = field(header, 345, 155)
                val relative = if (prefix.isBlank()) name else "$prefix/$name"
                val size = field(header, 124, 12).trim().toLong(8)
                val type = header[156].toInt().toChar()
                if (type == '\u0000' || type == '0') {
                    if (relative.matches(Regex("exercises/[A-Za-z0-9_-]+/[0-3]\\.jpg")) && size in 1L..5_000_000L) {
                        val target = root.resolve(relative).normalize()
                        require(target.startsWith(root))
                        Files.createDirectories(target.parent)
                        if (!Files.exists(target)) {
                            val temp = Files.createTempFile(target.parent, "exercise-", ".part")
                            try {
                                Files.newOutputStream(temp).use { output -> copyExactly(input, output, size) }
                                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                            } finally {
                                Files.deleteIfExists(temp)
                            }
                        } else {
                            input.skipNBytes(size)
                        }
                    } else {
                        input.skipNBytes(size)
                    }
                    val padding = (512 - size % 512) % 512
                    input.skipNBytes(padding)
                } else {
                    input.skipNBytes(size + ((512 - size % 512) % 512))
                }
            }
        }
        Files.writeString(marker, "Free Exercise DB a859101d633a01c4a1a920d6a8ce41dabba0705f")
    }

    private fun field(bytes: ByteArray, start: Int, length: Int): String =
        bytes.copyOfRange(start, start + length).takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.UTF_8)

    private fun copyExactly(input: InputStream, output: OutputStream, size: Long) {
        val buffer = ByteArray(16 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(read > 0) { "Exercise image archive is incomplete" }
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
}
