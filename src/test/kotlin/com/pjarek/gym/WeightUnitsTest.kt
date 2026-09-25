package com.pjarek.gym

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeightUnitsTest {
    @Test
    fun `converts pounds to canonical kilograms and kilograms to display pounds`() {
        assertEquals(BigDecimal("45.36"), WeightUnits.toKilograms(BigDecimal("100"), "LB"))
        assertEquals(BigDecimal("100.00"), WeightUnits.fromKilograms(BigDecimal("45.36"), "LB"))
    }

    @Test
    fun `preserves kilogram values for kilogram preference`() {
        assertEquals(BigDecimal("45.36"), WeightUnits.toKilograms(BigDecimal("45.36"), "KG"))
        assertEquals(BigDecimal("45.36"), WeightUnits.fromKilograms(BigDecimal("45.36"), "KG"))
    }

    @Test
    fun `validates values according to selected unit increments`() {
        assertTrue(WeightUnits.isValidInput(BigDecimal("10.01"), "KG"))
        assertFalse(WeightUnits.isValidInput(BigDecimal("10.001"), "KG"))
        assertTrue(WeightUnits.isValidInput(BigDecimal("10.25"), "LB"))
        assertFalse(WeightUnits.isValidInput(BigDecimal("10.1"), "LB"))
        assertFalse(WeightUnits.isValidInput(BigDecimal("2001"), "LB"))
        assertFalse(WeightUnits.isValidInput(BigDecimal("10"), "stone"))
    }

    @Test
    fun `rejects unsupported units during conversion`() {
        assertThrows(IllegalArgumentException::class.java) { WeightUnits.toKilograms(BigDecimal("10"), "stone") }
        assertThrows(IllegalArgumentException::class.java) { WeightUnits.fromKilograms(BigDecimal("10"), "stone") }
    }
}
