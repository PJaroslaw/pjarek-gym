package com.pjarek.gym

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer

class SchemaUpgradeIntegrationTest {
    @Test
    fun `upgrades deployed v2 records into rep ranges elapsed workout times and kilogram weights`() {
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        migrate(dataSource, "2")
        val jdbc = JdbcTemplate(dataSource)
        val ownerId = jdbc.queryForObject(
            "INSERT INTO app_user(username,password_hash,role) VALUES ('migration-test','unused','USER') RETURNING id",
            Long::class.java
        )!!
        val exerciseId = jdbc.queryForObject(
            "INSERT INTO exercise(name) VALUES ('Migration exercise') RETURNING id",
            Long::class.java
        )!!
        val routineId = jdbc.queryForObject(
            "INSERT INTO routine(owner_id,name) VALUES (?, 'Migration routine') RETURNING id",
            UUID::class.java,
            ownerId
        )!!
        val dayId = jdbc.queryForObject(
            "INSERT INTO routine_day(routine_id,name,position) VALUES (?, 'Day', 1) RETURNING id",
            Long::class.java,
            routineId
        )!!
        jdbc.update("INSERT INTO routine_item(day_id,exercise_id,position,rep_target) VALUES (?,?,1,'6 - 8')", dayId, exerciseId)
        jdbc.update("INSERT INTO routine_item(day_id,exercise_id,position,rep_target) VALUES (?,?,2,'14')", dayId, exerciseId)
        jdbc.update("INSERT INTO routine_item(day_id,exercise_id,position,rep_target) VALUES (?,?,3,'invalid')", dayId, exerciseId)

        val completedId = insertWorkout(jdbc, ownerId, "COMPLETED", Instant.now().minusSeconds(1000))
        val workoutExerciseId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,rep_target) VALUES (?,?,1,'10-15') RETURNING id",
            Long::class.java,
            completedId,
            exerciseId
        )!!
        jdbc.update("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight,weight_unit) VALUES (?,1,8,100,'LB')", workoutExerciseId)
        addEvent(jdbc, completedId, "PAUSED", 200)
        addEvent(jdbc, completedId, "RESUMED", 300)
        addEvent(jdbc, completedId, "PAUSED", 500)
        addEvent(jdbc, completedId, "RESUMED", 600)
        jdbc.update("UPDATE workout SET completed_at=started_at+interval '900 seconds' WHERE id=?", completedId)

        val pausedId = insertWorkout(jdbc, ownerId, "PAUSED", Instant.now().minusSeconds(1000))
        addEvent(jdbc, pausedId, "PAUSED", 300)
        addEvent(jdbc, pausedId, "RESUMED", 400)
        addEvent(jdbc, pausedId, "PAUSED", 900)

        val activeId = insertWorkout(jdbc, ownerId, "IN_PROGRESS", Instant.now().minusSeconds(600))
        addEvent(jdbc, activeId, "PAUSED", 100)
        addEvent(jdbc, activeId, "RESUMED", 500)

        migrate(dataSource, null)

        assertEquals(
            listOf("6:8", "14:14", "8:12"),
            jdbc.queryForList("SELECT min_reps || ':' || max_reps FROM routine_item ORDER BY position", String::class.java)
        )
        assertEquals("10:15", jdbc.queryForObject(
            "SELECT min_reps || ':' || max_reps FROM workout_exercise WHERE workout_id=?",
            String::class.java,
            completedId
        ))
        assertEquals(700, jdbc.queryForObject("SELECT elapsed_seconds FROM workout WHERE id=?", Int::class.java, completedId))
        assertEquals(800, jdbc.queryForObject("SELECT elapsed_seconds FROM workout WHERE id=?", Int::class.java, pausedId))
        assertEquals(100, jdbc.queryForObject("SELECT elapsed_seconds FROM workout WHERE id=?", Int::class.java, activeId))
        assertEquals(BigDecimal("45.36"), jdbc.queryForObject("SELECT weight FROM workout_set WHERE workout_exercise_id=?", BigDecimal::class.java, workoutExerciseId))
        val activeSince = jdbc.queryForObject("SELECT active_since FROM workout WHERE id=?", java.sql.Timestamp::class.java, activeId)!!.toInstant()
        assertTrue(activeSince.isAfter(Instant.now().minusSeconds(120)))
        assertTrue(activeSince.isBefore(Instant.now().minusSeconds(80)))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name IN ('routine_item','workout_exercise') AND column_name='rep_target'", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='workout_set' AND column_name='weight_unit'", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout WHERE status IN ('PAUSED','COMPLETED') AND active_since IS NOT NULL", Int::class.java))
        assertEquals(true, jdbc.queryForObject("SELECT active FROM exercise WHERE id=?", Boolean::class.java, exerciseId))
    }

    private fun insertWorkout(jdbc: JdbcTemplate, ownerId: Long, status: String, startedAt: Instant): UUID {
        val workoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,title,status,started_at) VALUES (?, ?, ?, ?) RETURNING id",
            UUID::class.java,
            ownerId,
            status,
            status,
            java.sql.Timestamp.from(startedAt)
        )!!
        if (status == "COMPLETED") {
            jdbc.update("UPDATE workout SET completed_at=started_at+interval '900 seconds' WHERE id=?", workoutId)
        }
        return workoutId
    }

    private fun addEvent(jdbc: JdbcTemplate, workoutId: UUID, kind: String, secondsAfterStart: Long) {
        jdbc.update(
            "INSERT INTO workout_event(workout_id,kind,occurred_at) SELECT ?, ?, started_at + interval '1 second' * ? FROM workout WHERE id=?",
            workoutId,
            kind,
            secondsAfterStart,
            workoutId
        )
    }

    private fun migrate(dataSource: DriverManagerDataSource, target: String?) {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
        if (target != null) configuration.target(MigrationVersion.fromVersion(target))
        configuration.load().migrate()
    }

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine").apply {
            start()
            Runtime.getRuntime().addShutdownHook(Thread { stop() })
        }
    }
}
