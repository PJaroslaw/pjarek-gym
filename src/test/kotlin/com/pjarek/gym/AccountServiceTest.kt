package com.pjarek.gym

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class AccountServiceTest {
    private val repository = mock(AccountRepository::class.java)
    private val service = AccountService(repository, LongPasswordEncoder())

    @Test
    fun `valid weight preference is saved for the signed in account`() {
        val id = UUID.randomUUID()
        `when`(repository.findByUsername("athlete")).thenReturn(account(id))

        service.updateWeightUnit("athlete", "LB")

        verify(repository).updateWeightUnit(id, "LB")
    }

    @Test
    fun `invalid weight preference never reaches persistence`() {
        assertThrows(IllegalArgumentException::class.java) { service.updateWeightUnit("athlete", "ST") }
        verifyNoInteractions(repository)
    }

    private fun account(id: UUID) = Account(
        id, "athlete", "hash", "USER", true, false, "KG", "SYSTEM", Timestamp.from(Instant.now())
    )
}
