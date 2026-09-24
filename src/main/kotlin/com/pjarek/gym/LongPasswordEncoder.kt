package com.pjarek.gym

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder

class LongPasswordEncoder : PasswordEncoder {
    private val pbkdf2 = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    override fun encode(rawPassword: CharSequence?): String? = rawPassword?.let(pbkdf2::encode)

    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean =
        rawPassword != null && encodedPassword != null && pbkdf2.matches(rawPassword, encodedPassword)
}
