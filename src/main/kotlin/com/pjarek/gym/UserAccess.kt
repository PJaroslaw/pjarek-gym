package com.pjarek.gym

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component

@Component
class UserAccess(private val jdbc: JdbcTemplate) {
    fun userId(user: UserDetails): UUID = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", UUID::class.java, user.username)!!

    fun ownedRoutine(id: UUID, owner: UUID) {
        val count = jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=? AND owner_id=?", Int::class.java, id, owner)
        if (count != 1) throw AccessDeniedException("Routine is not available")
    }

    fun ownedDay(day: UUID, routine: UUID) {
        if (jdbc.queryForObject("SELECT count(*) FROM routine_day WHERE id=? AND routine_id=?", Int::class.java, day, routine) != 1) {
            throw AccessDeniedException("Routine day is not available")
        }
    }

    fun ownedWorkout(id: UUID, owner: UUID) {
        val count = jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=? AND owner_id=?", Int::class.java, id, owner)
        if (count != 1) throw AccessDeniedException("Workout is not available")
    }

    fun requireActive(id: UUID) {
        require(jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, id) == "IN_PROGRESS")
    }
}
