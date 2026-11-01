package com.pjarek.gym

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

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

@Repository
class AccountRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun findByUsername(username: String): Account? = jdbc.query(
        "SELECT * FROM app_user WHERE username=:username",
        mapOf("username" to username), accountMapper
    ).firstOrNull()

    fun findById(id: UUID): Account? = jdbc.query(
        "SELECT * FROM app_user WHERE id=:id",
        mapOf("id" to id), accountMapper
    ).firstOrNull()

    fun all(): List<Account> = jdbc.query("SELECT * FROM app_user ORDER BY created_at", emptyMap<String, Any>(), accountMapper)

    fun updateWeightUnit(id: UUID, unit: String) {
        jdbc.update("UPDATE app_user SET weight_unit=:unit WHERE id=:id", mapOf("unit" to unit, "id" to id))
    }

    fun updateThemeMode(id: UUID, mode: String) {
        jdbc.update("UPDATE app_user SET theme_mode=:mode WHERE id=:id", mapOf("mode" to mode, "id" to id))
    }

    fun updatePassword(id: UUID, hash: String): Int = jdbc.update(
        "UPDATE app_user SET password_hash=:hash,must_change_password=FALSE WHERE id=:id",
        mapOf("hash" to hash, "id" to id)
    )

    fun updateRequiredPassword(id: UUID, hash: String): Int = jdbc.update(
        "UPDATE app_user SET password_hash=:hash,must_change_password=FALSE WHERE id=:id AND must_change_password=TRUE",
        mapOf("hash" to hash, "id" to id)
    )

    fun create(username: String, hash: String, role: String, temporaryPassword: Boolean) {
        jdbc.update(
            "INSERT INTO app_user(username,password_hash,role,must_change_password) VALUES (:username,:hash,:role,:temporaryPassword)",
            mapOf("username" to username, "hash" to hash, "role" to role, "temporaryPassword" to temporaryPassword)
        )
    }

    fun createAdminIfMissing(username: String, hash: String) {
        jdbc.update(
            "INSERT INTO app_user(username,password_hash,role) VALUES (:username,:hash,'ADMIN') ON CONFLICT(username) DO NOTHING",
            mapOf("username" to username, "hash" to hash)
        )
    }

    fun delete(id: UUID): Int = jdbc.update("DELETE FROM app_user WHERE id=:id", mapOf("id" to id))

    fun updateEnabled(id: UUID, enabled: Boolean): Int = jdbc.update(
        "UPDATE app_user SET enabled=:enabled WHERE id=:id", mapOf("enabled" to enabled, "id" to id)
    )

    fun resetPassword(id: UUID, hash: String, temporaryPassword: Boolean): Int = jdbc.update(
        "UPDATE app_user SET password_hash=:hash,must_change_password=:temporaryPassword WHERE id=:id",
        mapOf("hash" to hash, "temporaryPassword" to temporaryPassword, "id" to id)
    )

    private val accountMapper = RowMapper<Account> { rs, _ -> rs.toAccount() }
}

private fun ResultSet.toAccount() = Account(
    getObject("id", UUID::class.java), getString("username"), getString("password_hash"), getString("role"),
    getBoolean("enabled"), getBoolean("must_change_password"), getString("weight_unit"), getString("theme_mode"),
    getTimestamp("created_at")
)
