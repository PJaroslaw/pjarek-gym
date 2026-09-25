package com.pjarek.gym

import java.math.BigDecimal
import java.math.RoundingMode

object WeightUnits {
    private val kilogramsPerPound = BigDecimal("0.45359237")
    private val poundsPerKilogram = BigDecimal.ONE.divide(kilogramsPerPound, 12, RoundingMode.HALF_UP)
    private val quarterPound = BigDecimal("0.25")

    fun toKilograms(weight: BigDecimal, unit: String): BigDecimal = when (unit) {
        "KG" -> weight.setScale(2, RoundingMode.HALF_UP)
        "LB" -> weight.multiply(kilogramsPerPound).setScale(2, RoundingMode.HALF_UP)
        else -> throw IllegalArgumentException("Unsupported weight unit.")
    }

    fun fromKilograms(weight: BigDecimal, unit: String): BigDecimal = when (unit) {
        "KG" -> weight.setScale(2, RoundingMode.HALF_UP)
        "LB" -> weight.multiply(poundsPerKilogram)
            .divide(quarterPound, 0, RoundingMode.HALF_UP)
            .multiply(quarterPound)
            .setScale(2, RoundingMode.HALF_UP)
        else -> throw IllegalArgumentException("Unsupported weight unit.")
    }

    fun isValidInput(weight: BigDecimal, unit: String): Boolean {
        val step = when (unit) {
            "KG" -> BigDecimal("0.01")
            "LB" -> quarterPound
            else -> return false
        }
        return weight >= BigDecimal.ZERO && weight <= BigDecimal("2000") &&
            weight.remainder(step).compareTo(BigDecimal.ZERO) == 0
    }
}
