package com.pjarek.gym.repository

import java.math.BigDecimal
import java.util.UUID
import org.springframework.jdbc.core.ColumnMapRowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class WorkoutRepository(private val jdbc: NamedParameterJdbcTemplate) {
    private val rows = ColumnMapRowMapper()

    fun owned(id: UUID, owner: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM workout WHERE id=:id AND owner_id=:owner", mapOf("id" to id, "owner" to owner), Int::class.java
    ) == 1

    fun status(id: UUID): String? = jdbc.query(
        "SELECT status FROM workout WHERE id=:id", mapOf("id" to id), { rs, _ -> rs.getString("status") }
    ).firstOrNull()

    fun createAdHoc(owner: UUID): UUID = jdbc.queryForObject(
        "INSERT INTO workout(owner_id,title) VALUES (:owner,'Ad hoc workout') RETURNING id", mapOf("owner" to owner), UUID::class.java
    )!!

    fun createFromRoutine(owner: UUID, dayId: UUID, title: String): UUID {
        val workout = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,routine_day_id,title) VALUES (:owner,:dayId,:title) RETURNING id",
            mapOf("owner" to owner, "dayId" to dayId, "title" to title), UUID::class.java
        )!!
        jdbc.update(
            """INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot,planned_sets,min_reps,max_reps,rest_seconds)
                SELECT :workout,ri.exercise_id,ri.position,e.instructions,ri.planned_sets,ri.min_reps,ri.max_reps,ri.rest_seconds
                FROM routine_item ri JOIN exercise e ON e.id=ri.exercise_id WHERE ri.day_id=:dayId ORDER BY ri.position""",
            mapOf("workout" to workout, "dayId" to dayId)
        )
        return workout
    }

    fun addInitialExercise(workout: UUID, exerciseId: Long): UUID? = jdbc.query(
        "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT :workout,id,1,instructions FROM exercise WHERE id=:exerciseId AND active=TRUE RETURNING id",
        mapOf("workout" to workout, "exerciseId" to exerciseId), { rs, _ -> rs.getObject("id", UUID::class.java) }
    ).firstOrNull()

    fun find(id: UUID): Map<String, Any?> = jdbc.query(
        """SELECT *,CASE WHEN status='IN_PROGRESS' THEN elapsed_seconds + floor(extract(epoch FROM (now()-active_since)))::INTEGER
            ELSE elapsed_seconds END AS displayed_elapsed_seconds FROM workout WHERE id=:id""",
        mapOf("id" to id), rows
    ).first()

    fun items(id: UUID): List<Map<String, Any?>> = jdbc.query(
        "SELECT we.*,e.name,e.primary_muscles,e.equipment,e.source_id,(SELECT count(*) FROM workout_set s WHERE s.workout_exercise_id=we.id) completed_sets FROM workout_exercise we JOIN exercise e ON e.id=we.exercise_id WHERE we.workout_id=:id ORDER BY we.position",
        mapOf("id" to id), rows
    )

    fun sessionVolume(id: UUID): BigDecimal = jdbc.queryForObject(
        "SELECT coalesce(sum(s.reps*s.weight),0) FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE we.workout_id=:id",
        mapOf("id" to id), BigDecimal::class.java
    )!!

    fun sessionSets(id: UUID): List<Map<String, Any?>> = jdbc.query(
        "SELECT s.*,we.id AS workout_exercise_id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE we.workout_id=:id ORDER BY we.position,s.set_number",
        mapOf("id" to id), rows
    )

    fun storedSets(itemId: UUID): List<Map<String, Any?>> = jdbc.query(
        "SELECT s.*,we.id AS item_id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE we.id=:itemId ORDER BY s.set_number",
        mapOf("itemId" to itemId), rows
    )

    fun previousSets(owner: UUID, exerciseId: Long): List<Map<String, Any?>> = jdbc.query(
        """SELECT s.set_number,s.reps,s.weight,w.title,w.completed_at
            FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id
            JOIN workout w ON w.id=we.workout_id
            WHERE w.owner_id=:owner AND w.status='COMPLETED' AND we.exercise_id=:exerciseId
              AND w.id=(SELECT w2.id FROM workout w2 JOIN workout_exercise we2 ON we2.workout_id=w2.id
                JOIN workout_set s2 ON s2.workout_exercise_id=we2.id
                WHERE w2.owner_id=:owner AND w2.status='COMPLETED' AND we2.exercise_id=:exerciseId
                ORDER BY w2.completed_at DESC,w2.started_at DESC LIMIT 1)
            ORDER BY s.set_number""",
        mapOf("owner" to owner, "exerciseId" to exerciseId), rows
    )

    fun addExercise(id: UUID, exerciseId: Long): UUID? {
        val position = jdbc.queryForObject(
            "SELECT coalesce(max(position),0)+1 FROM workout_exercise WHERE workout_id=:id", mapOf("id" to id), Int::class.java
        )!!
        return jdbc.query(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position,instructions_snapshot) SELECT :id,id,:position,instructions FROM exercise WHERE id=:exerciseId AND active=TRUE RETURNING id",
            mapOf("id" to id, "position" to position, "exerciseId" to exerciseId), { rs, _ -> rs.getObject("id", UUID::class.java) }
        ).firstOrNull()
    }

    fun containsItem(id: UUID, itemId: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM workout_exercise WHERE id=:itemId AND workout_id=:id", mapOf("itemId" to itemId, "id" to id), Int::class.java
    ) == 1

    fun restSeconds(id: UUID, itemId: UUID): Int = jdbc.queryForObject(
        "SELECT rest_seconds FROM workout_exercise WHERE id=:itemId AND workout_id=:id",
        mapOf("itemId" to itemId, "id" to id), Int::class.java
    )!!

    fun insertSet(itemId: UUID, reps: Int, weight: BigDecimal) {
        val setNumber = jdbc.queryForObject(
            "SELECT coalesce(max(set_number),0)+1 FROM workout_set WHERE workout_exercise_id=:itemId",
            mapOf("itemId" to itemId), Int::class.java
        )!!
        jdbc.update(
            "INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (:itemId,:setNumber,:reps,:weight)",
            mapOf("itemId" to itemId, "setNumber" to setNumber, "reps" to reps, "weight" to weight)
        )
    }

    fun setItem(id: UUID, setId: UUID): UUID? = jdbc.query(
        "SELECT we.id FROM workout_set s JOIN workout_exercise we ON we.id=s.workout_exercise_id WHERE s.id=:setId AND we.workout_id=:id",
        mapOf("setId" to setId, "id" to id), { rs, _ -> rs.getObject("id", UUID::class.java) }
    ).firstOrNull()

    fun editSet(setId: UUID, reps: Int, weight: BigDecimal) {
        jdbc.update("UPDATE workout_set SET reps=:reps,weight=:weight WHERE id=:setId",
            mapOf("setId" to setId, "reps" to reps, "weight" to weight))
    }

    fun deleteSet(setId: UUID, itemId: UUID) {
        jdbc.update("DELETE FROM workout_set WHERE id=:setId", mapOf("setId" to setId))
        jdbc.update(
            "WITH ordered AS (SELECT id,row_number() OVER (ORDER BY set_number,completed_at,id) AS new_number FROM workout_set WHERE workout_exercise_id=:itemId) UPDATE workout_set s SET set_number=o.new_number FROM ordered o WHERE s.id=o.id",
            mapOf("itemId" to itemId)
        )
    }

    fun finish(id: UUID): Int = jdbc.update(
        """UPDATE workout SET elapsed_seconds=elapsed_seconds+CASE WHEN status='IN_PROGRESS'
            THEN floor(extract(epoch FROM (now()-active_since)))::INTEGER ELSE 0 END,
            active_since=NULL,status='COMPLETED',completed_at=now()
            WHERE id=:id AND status IN ('IN_PROGRESS','PAUSED')""", mapOf("id" to id)
    )

    fun pause(id: UUID): Int = jdbc.update(
        """UPDATE workout SET elapsed_seconds=elapsed_seconds+floor(extract(epoch FROM (now()-active_since)))::INTEGER,
            active_since=NULL,status='PAUSED' WHERE id=:id AND status='IN_PROGRESS'""", mapOf("id" to id)
    )

    fun resume(id: UUID): Int = jdbc.update(
        "UPDATE workout SET status='IN_PROGRESS',active_since=now() WHERE id=:id AND status='PAUSED'", mapOf("id" to id)
    )

    fun event(id: UUID, kind: String) {
        jdbc.update("INSERT INTO workout_event(workout_id,kind) VALUES (:id,:kind)", mapOf("id" to id, "kind" to kind))
    }

    fun delete(id: UUID) {
        jdbc.update("DELETE FROM workout WHERE id=:id", mapOf("id" to id))
    }
}
