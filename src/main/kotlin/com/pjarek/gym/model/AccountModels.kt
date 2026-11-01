package com.pjarek.gym.model

import java.sql.Timestamp
import java.util.UUID

data class Account(
    val id: UUID,
    val username: String,
    val passwordHash: String,
    val role: String,
    val enabled: Boolean,
    val mustChangePassword: Boolean,
    val weightUnit: String,
    val themeMode: String,
    val createdAt: Timestamp
)

data class AdminAccount(
    val id: UUID,
    val username: String,
    val role: String,
    val enabled: Boolean,
    val mustChangePassword: Boolean,
    val createdAt: Timestamp
)
