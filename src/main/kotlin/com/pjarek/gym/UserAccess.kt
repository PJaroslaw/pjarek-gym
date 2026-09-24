package com.pjarek.gym

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component

@Component
class UserAccess(private val jdbc: JdbcTemplate) {
    fun userId(user: UserDetails): Long = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long::class.java, user.username)!!

    fun ownerScope(user: UserDetails): Long = if (user.authorities.any { it.authority == "ROLE_ADMIN" }) -1L else userId(user)

    fun ownedRoutine(id: UUID, owner: Long) {
        val count = if (owner < 0) jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=?", Int::class.java, id)
        else jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=? AND owner_id=?", Int::class.java, id, owner)
        if (count != 1) throw AccessDeniedException("Routine is not available")
    }

    fun ownedDay(day: Long, routine: UUID) {
        if (jdbc.queryForObject("SELECT count(*) FROM routine_day WHERE id=? AND routine_id=?", Int::class.java, day, routine) != 1) {
            throw AccessDeniedException("Routine day is not available")
        }
    }

    fun ownedWorkout(id: UUID, owner: Long) {
        val count = if (owner < 0) jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, id)
        else jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=? AND owner_id=?", Int::class.java, id, owner)
        if (count != 1) throw AccessDeniedException("Workout is not available")
    }

    fun requireActive(id: UUID) {
        require(jdbc.queryForObject("SELECT status FROM workout WHERE id=?", String::class.java, id) == "IN_PROGRESS")
    }
}
