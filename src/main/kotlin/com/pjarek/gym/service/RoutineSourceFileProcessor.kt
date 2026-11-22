package com.pjarek.gym.service

import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service

@Service
class RoutineSourceFileProcessor {
    fun prepare(sourceFile: RoutineSourceFile): RoutineSourceFile {
        if (sourceFile.contentType != "application/pdf") {
            return sourceFile.copy(images = listOf(RoutineSourceImage(sourceFile.contentType, sourceFile.bytes)))
        }
        try {
            Loader.loadPDF(sourceFile.bytes).use { document ->
                require(document.numberOfPages in 1..MAX_PDF_PAGES) {
                    "PDF routines must contain between 1 and $MAX_PDF_PAGES pages."
                }
                val text = PDFTextStripper().getText(document).trim().take(MAX_EXTRACTED_TEXT_LENGTH)
                val renderer = PDFRenderer(document)
                var renderedBytes = 0
                val pages = (0 until document.numberOfPages).map { page ->
                    val pageBox = document.getPage(page).cropBox
                    require(pageBox.width in 1f..MAX_PAGE_DIMENSION_POINTS && pageBox.height in 1f..MAX_PAGE_DIMENSION_POINTS) {
                        "The uploaded PDF has a page that is too large to process."
                    }
                    val image = renderer.renderImage(page, PDF_RENDER_SCALE)
                    ByteArrayOutputStream().use { output ->
                        ImageIO.write(image, "png", output)
                        val bytes = output.toByteArray()
                        renderedBytes += bytes.size
                        require(renderedBytes <= MAX_RENDERED_IMAGE_BYTES) {
                            "The uploaded PDF is too large to process. Choose a smaller file and try again."
                        }
                        RoutineSourceImage("image/png", bytes)
                    }
                }
                return sourceFile.copy(extractedText = text.takeIf(String::isNotBlank), images = pages)
            }
        } catch (error: IOException) {
            throw IllegalArgumentException("The uploaded PDF could not be read. Choose a valid PDF and try again.", error)
        }
    }

    companion object {
        const val MAX_PDF_PAGES = 8
        private const val MAX_PAGE_DIMENSION_POINTS = 1440f
        private const val MAX_RENDERED_IMAGE_BYTES = 20 * 1024 * 1024
        private const val MAX_EXTRACTED_TEXT_LENGTH = 100_000
        private const val PDF_RENDER_SCALE = 1.25f
    }
}
