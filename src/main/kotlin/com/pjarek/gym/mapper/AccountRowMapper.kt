package com.pjarek.gym.mapper

import com.pjarek.gym.model.Account
import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.RowMapper

object AccountRowMapper : RowMapper<Account> {
    override fun mapRow(rs: ResultSet, rowNum: Int): Account = Account(
        rs.getObject("id", UUID::class.java), rs.getString("username"), rs.getString("password_hash"),
        rs.getString("role"), rs.getBoolean("enabled"), rs.getBoolean("must_change_password"),
        rs.getString("weight_unit"), rs.getString("theme_mode"), rs.getTimestamp("created_at")
    )
}
