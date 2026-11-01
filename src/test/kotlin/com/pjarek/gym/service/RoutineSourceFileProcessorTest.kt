package com.pjarek.gym.service

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoutineSourceFileProcessorTest {
    private val processor = RoutineSourceFileProcessor()

    @Test
    fun `extracts pdf text and renders pages for visual interpretation`() {
        val document = PDDocument()
        document.addPage(PDPage())
        PDPageContentStream(document, document.getPage(0)).use { content ->
            content.beginText()
            content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            content.newLineAtOffset(40f, 700f)
            content.showText("Workout A: Squat, 3 sets of 8")
            content.endText()
        }
        val output = ByteArrayOutputStream()
        document.save(output)
        document.close()

        val prepared = processor.prepare(RoutineSourceFile("routine.pdf", "application/pdf", output.toByteArray()))

        assertTrue(prepared.extractedText.orEmpty().contains("Workout A"))
        assertEquals(1, prepared.images.size)
        assertEquals("image/png", prepared.images.single().contentType)
        assertTrue(prepared.images.single().bytes.isNotEmpty())
    }

    @Test
    fun `rejects unreadable pdf data`() {
        assertThrows<IllegalArgumentException> {
            processor.prepare(RoutineSourceFile("routine.pdf", "application/pdf", "%PDF-bad".toByteArray()))
        }
    }
}
