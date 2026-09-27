package com.pjarek.gym

import java.util.UUID
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AdminUserIntegrationTest : IntegrationTestSupport() {
    @Test
    fun `user list separates edit from read only data inspection`() {
        val userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", UUID::class.java)!!
        val routineId = jdbc.queryForObject(
            "INSERT INTO routine(owner_id,name) VALUES (?, 'Admin placement routine') RETURNING id",
            UUID::class.java,
            userId
        )!!
        val workoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,title) VALUES (?, 'Admin placement workout') RETURNING id",
            UUID::class.java,
            userId
        )!!
        val dayId = jdbc.queryForObject(
            "INSERT INTO routine_day(routine_id,name,position) VALUES (?, 'Inspect day', 1) RETURNING id",
            UUID::class.java,
            routineId
        )!!
        val exercise = jdbc.queryForMap("SELECT id,name FROM exercise ORDER BY id LIMIT 1")
        val exerciseId = exercise["id"] as Long
        val exerciseName = exercise["name"] as String
        jdbc.update(
            "INSERT INTO routine_item(day_id,exercise_id,position,planned_sets,min_reps,max_reps) VALUES (?,?,1,4,6,10)",
            dayId,
            exerciseId
        )
        val workoutExerciseId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position) VALUES (?,?,1) RETURNING id",
            UUID::class.java,
            workoutId,
            exerciseId
        )!!
        jdbc.update("INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,1,9,42)", workoutExerciseId)

        val admin = user("integration-admin").roles("ADMIN")
        mockMvc.perform(get("/admin/users").with(admin))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Inspect")))
            .andExpect(content().string(containsString("Edit")))
            .andExpect(content().string(not(containsString("Disable user"))))
            .andExpect(content().string(not(containsString("Delete user"))))

        val editPage = mockMvc.perform(get("/admin/users/$userId").with(admin))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(editPage.contains("Set password"))
        assertTrue(editPage.contains("Disable user"))
        assertTrue(editPage.contains("Delete user and all data"))
        assertFalse(editPage.contains("Admin placement routine"))
        assertFalse(editPage.contains("Admin placement workout"))

        val inspectPage = mockMvc.perform(get("/admin/users/$userId/inspect").with(admin))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(inspectPage.contains("Admin placement routine"))
        assertTrue(inspectPage.contains("Admin placement workout"))
        assertTrue(inspectPage.contains("1 days · 1 exercises"))
        assertTrue(inspectPage.contains("1 exercises · 1 sets"))
        assertTrue(inspectPage.contains("/admin/users/$userId/inspect/routines/$routineId"))
        assertTrue(inspectPage.contains("/admin/users/$userId/inspect/workouts/$workoutId"))
        assertFalse(inspectPage.contains("Inspect day"))
        assertFalse(inspectPage.contains("42.00 kg"))
        assertFalse(inspectPage.contains("/admin/routines/$routineId/delete"))
        assertFalse(inspectPage.contains("/admin/workouts/$workoutId/delete"))
        assertFalse(inspectPage.contains("Delete user"))

        val routineDetails = mockMvc.perform(get("/admin/users/$userId/inspect/routines/$routineId").with(admin))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(routineDetails.contains("Inspect day"))
        assertTrue(routineDetails.contains(exerciseName))
        assertFalse(routineDetails.contains("Edit plan"))
        assertFalse(routineDetails.contains("Remove exercise"))
        assertFalse(routineDetails.contains("Delete day"))

        val workoutDetails = mockMvc.perform(get("/admin/users/$userId/inspect/workouts/$workoutId").with(admin))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(workoutDetails.contains(exerciseName))
        assertTrue(workoutDetails.contains("42.00 kg"))
        assertFalse(workoutDetails.contains("Delete workout"))

        val foreignOwnerId = jdbc.queryForObject(
            "INSERT INTO app_user(username,password_hash,role) VALUES (?, 'unused','USER') RETURNING id",
            UUID::class.java,
            "inspection-foreign-${UUID.randomUUID()}"
        )!!
        val foreignRoutineId = jdbc.queryForObject(
            "INSERT INTO routine(owner_id,name) VALUES (?, 'Foreign routine') RETURNING id",
            UUID::class.java,
            foreignOwnerId
        )!!
        val foreignWorkoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,title) VALUES (?, 'Foreign workout') RETURNING id",
            UUID::class.java,
            foreignOwnerId
        )!!
        mockMvc.perform(get("/admin/users/$userId/inspect/routines/$foreignRoutineId").with(admin))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/admin/users/$userId/inspect/workouts/$foreignWorkoutId").with(admin))
            .andExpect(status().isNotFound)
        jdbc.update("DELETE FROM app_user WHERE id=?", foreignOwnerId)
        mockMvc.perform(post("/admin/routines/$routineId/delete").with(admin).with(csrf()))
            .andExpect(status().isNotFound)
        mockMvc.perform(post("/admin/workouts/$workoutId/delete").with(admin).with(csrf()))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/routines/$routineId").with(admin))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/routines/$routineId/delete").with(admin).with(csrf()))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/workouts/$workoutId").with(admin))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/workouts/$workoutId/delete").param("returnTo", "overview").with(admin).with(csrf()))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/admin/users/$userId/inspect").with(user("exercise-search-test")))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/admin/users/$userId/inspect/routines/$routineId").with(user("exercise-search-test")))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/admin/users/${UUID.randomUUID()}/inspect").with(admin))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `inspection paginates older sessions`() {
        val userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", UUID::class.java)!!
        repeat(21) { index ->
            jdbc.update("INSERT INTO workout(owner_id,title) VALUES (?,?)", userId, "Inspection session $index")
            jdbc.update("INSERT INTO routine(owner_id,name) VALUES (?,?)", userId, "Inspection routine $index")
        }
        val admin = user("integration-admin").roles("ADMIN")

        mockMvc.perform(get("/admin/users/$userId/inspect").with(admin))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Older sessions →")))
            .andExpect(content().string(containsString("sessionsPage=1")))
            .andExpect(content().string(containsString("More routines →")))
            .andExpect(content().string(containsString("routinesPage=1")))

        mockMvc.perform(get("/admin/users/$userId/inspect").param("sessionsPage", "1").with(admin))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Newer sessions")))
            .andExpect(content().string(not(containsString("Older sessions →"))))

        mockMvc.perform(get("/admin/users/$userId/inspect").param("routinesPage", "1").with(admin))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Previous routines")))
            .andExpect(content().string(not(containsString("More routines →"))))
    }

    @Test
    fun `admin can delete another admin and all their data but not their own account`() {
        val adminId = jdbc.queryForObject(
            "INSERT INTO app_user(username,password_hash,role) VALUES ('user-delete-test','unused','ADMIN') RETURNING id",
            UUID::class.java
        )!!
        val routineId = jdbc.queryForObject(
            "INSERT INTO routine(owner_id,name) VALUES (?, 'Delete cascade test') RETURNING id",
            UUID::class.java,
            adminId
        )!!
        val dayId = jdbc.queryForObject(
            "INSERT INTO routine_day(routine_id,name,position) VALUES (?, 'Day', 1) RETURNING id",
            UUID::class.java,
            routineId
        )!!
        val exerciseId = jdbc.queryForObject("SELECT id FROM exercise LIMIT 1", Long::class.java)!!
        val itemId = jdbc.queryForObject(
            "INSERT INTO routine_item(day_id,exercise_id,position) VALUES (?,?,1) RETURNING id",
            UUID::class.java,
            dayId,
            exerciseId
        )!!
        val workoutId = jdbc.queryForObject(
            "INSERT INTO workout(owner_id,routine_day_id,title) VALUES (?,?,'Delete cascade workout') RETURNING id",
            UUID::class.java,
            adminId,
            dayId
        )!!
        val workoutExerciseId = jdbc.queryForObject(
            "INSERT INTO workout_exercise(workout_id,exercise_id,position) VALUES (?,?,1) RETURNING id",
            UUID::class.java,
            workoutId,
            exerciseId
        )!!
        val workoutSetId = jdbc.queryForObject(
            "INSERT INTO workout_set(workout_exercise_id,set_number,reps,weight) VALUES (?,1,8,20) RETURNING id",
            UUID::class.java,
            workoutExerciseId
        )!!
        val eventId = jdbc.queryForObject(
            "INSERT INTO workout_event(workout_id,kind) VALUES (?,'STARTED') RETURNING id",
            UUID::class.java,
            workoutId
        )!!

        mockMvc.perform(post("/admin/users/${jdbc.queryForObject("SELECT id FROM app_user WHERE username='integration-admin'", UUID::class.java)}/delete")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isForbidden)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM app_user WHERE username='integration-admin'", Int::class.java))

        mockMvc.perform(post("/admin/users/$adminId/delete")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/users"))

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id=?", Int::class.java, adminId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine WHERE id=?", Int::class.java, routineId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine_day WHERE id=?", Int::class.java, dayId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routine_item WHERE id=?", Int::class.java, itemId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout WHERE id=?", Int::class.java, workoutId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_exercise WHERE id=?", Int::class.java, workoutExerciseId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_set WHERE id=?", Int::class.java, workoutSetId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM workout_event WHERE id=?", Int::class.java, eventId))
    }

    @Test
    fun `admin password reset can require a change or remain permanent`() {
        val targetId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", UUID::class.java)!!
        val originalHash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String::class.java, targetId)!!
        mockMvc.perform(post("/admin/users/$targetId/password").param("password", "")
            .param("temporaryPassword", "true")
            .header("Referer", "http://localhost/admin/users/$targetId")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/users/$targetId"))
        assertEquals(originalHash, jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String::class.java, targetId))

        mockMvc.perform(post("/admin/users/$targetId/password")
            .param("password", "temp-pass")
            .param("temporaryPassword", "true")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        var row = jdbc.queryForMap("SELECT password_hash,must_change_password FROM app_user WHERE id=?", targetId)
        assertEquals(true, row["must_change_password"])
        assertTrue(passwordEncoder.matches("temp-pass", row["password_hash"] as String))

        mockMvc.perform(post("/admin/users/$targetId/password")
            .param("password", "permanent-pass")
            .param("temporaryPassword", "false")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        row = jdbc.queryForMap("SELECT password_hash,must_change_password FROM app_user WHERE id=?", targetId)
        assertEquals(false, row["must_change_password"])
        assertTrue(passwordEncoder.matches("permanent-pass", row["password_hash"] as String))

        mockMvc.perform(post("/admin/users").param("username", "created-admin-test").param("password", "permanent-admin-pass")
            .param("role", "ADMIN").param("temporaryPassword", "false")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/users"))
        val created = jdbc.queryForMap("SELECT role,password_hash,must_change_password FROM app_user WHERE username='created-admin-test'")
        assertEquals("ADMIN", created["role"])
        assertEquals(false, created["must_change_password"])
        assertTrue(passwordEncoder.matches("permanent-admin-pass", created["password_hash"] as String))
    }

    @Test
    fun `settings and account administration validate updates and protect the signed-in admin`() {
        mockMvc.perform(post("/settings/theme-mode").param("mode", "AUTO")
            .header("Referer", "http://localhost/settings")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/settings"))
        assertEquals("SYSTEM", jdbc.queryForObject("SELECT theme_mode FROM app_user WHERE username='exercise-search-test'", String::class.java))

        jdbc.update("UPDATE app_user SET password_hash=? WHERE username='exercise-search-test'", passwordEncoder.encode("current-pass"))
        mockMvc.perform(post("/settings/password").param("currentPassword", "wrong-pass").param("newPassword", "new-pass")
            .header("Referer", "http://localhost/settings")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/settings"))
        val hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username='exercise-search-test'", String::class.java)!!
        assertTrue(passwordEncoder.matches("current-pass", hash))

        mockMvc.perform(post("/admin/users").param("username", "bad username").param("password", "pass").param("role", "USER").param("temporaryPassword", "false")
            .header("Referer", "http://localhost/admin/users")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/users"))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_user WHERE username='bad username'", Int::class.java))

        val userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='exercise-search-test'", UUID::class.java)!!
        mockMvc.perform(get("/admin/users").with(user("exercise-search-test")))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/admin/users").param("username", "forbidden").param("password", "pass")
            .param("role", "USER").param("temporaryPassword", "false")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/admin/users/$userId/enabled").param("enabled", "false")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(false, jdbc.queryForObject("SELECT enabled FROM app_user WHERE id=?", Boolean::class.java, userId))
        mockMvc.perform(post("/admin/users/$userId/enabled").param("enabled", "true")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertEquals(true, jdbc.queryForObject("SELECT enabled FROM app_user WHERE id=?", Boolean::class.java, userId))

        val adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='integration-admin'", UUID::class.java)!!
        mockMvc.perform(post("/admin/users/$adminId/enabled").param("enabled", "false")
            .header("Referer", "http://localhost/admin/users")
            .with(user("integration-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isForbidden)
        assertEquals(true, jdbc.queryForObject("SELECT enabled FROM app_user WHERE id=?", Boolean::class.java, adminId))
    }
}
