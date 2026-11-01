package com.pjarek.gym

import java.sql.Timestamp
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

data class AdminAccount(
    val id: UUID,
    val username: String,
    val role: String,
    val enabled: Boolean,
    val mustChangePassword: Boolean,
    val createdAt: Timestamp
)

@Service
class AccountService(private val accounts: AccountRepository, private val encoder: PasswordEncoder) {
    fun account(username: String): Account = accounts.findByUsername(username)
        ?: throw IllegalArgumentException("User not found")

    fun account(id: UUID): Account = accounts.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    fun adminAccount(id: UUID): AdminAccount = account(id).toAdminAccount()

    fun allAccounts(): List<AdminAccount> = accounts.all().map { it.toAdminAccount() }

    fun updateWeightUnit(username: String, unit: String) {
        require(unit in listOf("KG", "LB"))
        accounts.updateWeightUnit(account(username).id, unit)
    }

    fun updateThemeMode(username: String, mode: String) {
        require(mode in listOf("LIGHT", "DARK", "SYSTEM"))
        accounts.updateThemeMode(account(username).id, mode)
    }

    fun changePassword(username: String, currentPassword: String, newPassword: String) {
        val account = account(username)
        require(encoder.matches(currentPassword, account.passwordHash)) { "Current password is not correct" }
        require(newPassword.isNotEmpty()) { "Enter a new password." }
        accounts.updatePassword(account.id, encoder.encode(newPassword)!!)
    }

    fun saveRequiredPassword(username: String, newPassword: String) {
        require(newPassword.isNotEmpty()) { "Enter a new password." }
        require(accounts.updateRequiredPassword(account(username).id, encoder.encode(newPassword)!!) == 1) {
            "A password change is not currently required."
        }
    }

    fun create(username: String, password: String, role: String, temporaryPassword: Boolean) {
        require(username.matches(Regex("[A-Za-z0-9_.-]{3,80}")) && password.isNotEmpty() && role in listOf("USER", "ADMIN")) {
            "Enter a valid username, a non-empty password, and a valid role."
        }
        accounts.create(username, encoder.encode(password)!!, role, temporaryPassword)
    }

    fun delete(id: UUID, currentUsername: String) {
        if (id == account(currentUsername).id) throw AccessDeniedException("Administrators cannot delete their own account")
        require(accounts.delete(id) == 1) { "User not found" }
    }

    fun setEnabled(id: UUID, currentUsername: String, enabled: Boolean) {
        if (account(id).username == currentUsername) throw AccessDeniedException("Administrators cannot disable their own account")
        require(accounts.updateEnabled(id, enabled) == 1) { "User not found" }
    }

    fun resetPassword(id: UUID, password: String, temporaryPassword: Boolean) {
        require(password.isNotEmpty()) { "Enter a password." }
        require(accounts.resetPassword(id, encoder.encode(password)!!, temporaryPassword) == 1) { "User not found" }
    }

    private fun Account.toAdminAccount() = AdminAccount(id, username, role, enabled, mustChangePassword, createdAt)
}
