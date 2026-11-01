package com.pjarek.gym.repository

import java.util.UUID
import org.springframework.jdbc.core.ColumnMapRowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class RoutineRepository(private val jdbc: NamedParameterJdbcTemplate) {
    private val rows = ColumnMapRowMapper()

    fun owned(id: UUID, owner: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM routine WHERE id=:id AND owner_id=:owner", mapOf("id" to id, "owner" to owner), Int::class.java
    ) == 1

    fun dayBelongs(dayId: UUID, routineId: UUID): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM routine_day WHERE id=:dayId AND routine_id=:routineId",
        mapOf("dayId" to dayId, "routineId" to routineId), Int::class.java
    ) == 1

    fun list(owner: UUID): List<Map<String, Any?>> = jdbc.query(
        "SELECT id,name FROM routine WHERE owner_id=:owner ORDER BY name", mapOf("owner" to owner), rows
    )

    fun create(owner: UUID, name: String): UUID = jdbc.queryForObject(
        "INSERT INTO routine(owner_id,name) VALUES (:owner,:name) RETURNING id",
        mapOf("owner" to owner, "name" to name), UUID::class.java
    )!!

    fun find(id: UUID): Map<String, Any?> = jdbc.query("SELECT * FROM routine WHERE id=:id", mapOf("id" to id), rows).first()

    fun days(id: UUID): List<Map<String, Any?>> = jdbc.query(
        "SELECT * FROM routine_day WHERE routine_id=:id ORDER BY position", mapOf("id" to id), rows
    )

    fun items(id: UUID): List<Map<String, Any?>> = jdbc.query(
        "SELECT ri.*,e.name exercise_name FROM routine_item ri JOIN routine_day d ON d.id=ri.day_id JOIN exercise e ON e.id=ri.exercise_id WHERE d.routine_id=:id ORDER BY d.position,ri.position",
        mapOf("id" to id), rows
    )

    fun addDay(id: UUID, name: String) {
        val position = jdbc.queryForObject(
            "SELECT coalesce(max(position),0)+1 FROM routine_day WHERE routine_id=:id", mapOf("id" to id), Int::class.java
        )!!
        jdbc.update("INSERT INTO routine_day(routine_id,name,position) VALUES (:id,:name,:position)",
            mapOf("id" to id, "name" to name, "position" to position))
    }

    fun renameDay(id: UUID, dayId: UUID, name: String) {
        jdbc.update("UPDATE routine_day SET name=:name WHERE id=:dayId AND routine_id=:id",
            mapOf("name" to name, "dayId" to dayId, "id" to id))
    }

    fun deleteDay(id: UUID, dayId: UUID) {
        val position = jdbc.queryForObject("SELECT position FROM routine_day WHERE id=:dayId AND routine_id=:id",
            mapOf("dayId" to dayId, "id" to id), Int::class.java)!!
        jdbc.update("DELETE FROM routine_day WHERE id=:dayId AND routine_id=:id", mapOf("dayId" to dayId, "id" to id))
        jdbc.update("UPDATE routine_day SET position=position-1 WHERE routine_id=:id AND position>:position",
            mapOf("id" to id, "position" to position))
    }

    fun rename(id: UUID, name: String) {
        jdbc.update("UPDATE routine SET name=:name WHERE id=:id", mapOf("name" to name, "id" to id))
    }

    fun delete(id: UUID) {
        jdbc.update("DELETE FROM routine WHERE id=:id", mapOf("id" to id))
    }

    fun deleteItem(id: UUID, itemId: UUID) {
        jdbc.update("DELETE FROM routine_item WHERE id=:itemId AND day_id IN (SELECT id FROM routine_day WHERE routine_id=:id)",
            mapOf("itemId" to itemId, "id" to id))
    }

    fun editItem(id: UUID, itemId: UUID, exerciseId: Long, sets: Int, minReps: Int, maxReps: Int, rest: Int): Int = jdbc.update(
        "UPDATE routine_item SET exercise_id=:exerciseId,planned_sets=:sets,min_reps=:minReps,max_reps=:maxReps,rest_seconds=:rest WHERE id=:itemId AND day_id IN (SELECT id FROM routine_day WHERE routine_id=:id)",
        mapOf("id" to id, "itemId" to itemId, "exerciseId" to exerciseId, "sets" to sets, "minReps" to minReps, "maxReps" to maxReps, "rest" to rest)
    )

    fun addItem(dayId: UUID, exerciseId: Long, sets: Int, minReps: Int, maxReps: Int, rest: Int) {
        val position = jdbc.queryForObject(
            "SELECT coalesce(max(position),0)+1 FROM routine_item WHERE day_id=:dayId", mapOf("dayId" to dayId), Int::class.java
        )!!
        jdbc.update(
            "INSERT INTO routine_item(day_id,exercise_id,position,planned_sets,min_reps,max_reps,rest_seconds) VALUES (:dayId,:exerciseId,:position,:sets,:minReps,:maxReps,:rest)",
            mapOf("dayId" to dayId, "exerciseId" to exerciseId, "position" to position, "sets" to sets, "minReps" to minReps, "maxReps" to maxReps, "rest" to rest)
        )
    }

    fun dayName(dayId: UUID): String = jdbc.queryForObject(
        "SELECT name FROM routine_day WHERE id=:dayId", mapOf("dayId" to dayId), String::class.java
    )!!
}
