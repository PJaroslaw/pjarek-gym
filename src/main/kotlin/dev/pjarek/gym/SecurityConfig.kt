package dev.pjarek.gym

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.access.AccessDeniedHandler

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(jdbc: JdbcTemplate): UserDetailsService = UserDetailsService { username ->
        val users = jdbc.query(
            "SELECT username,password_hash,role,enabled FROM app_user WHERE username = ?",
            { rs, _ -> User.withUsername(rs.getString("username"))
                .password(rs.getString("password_hash"))
                .roles(rs.getString("role"))
                .disabled(!rs.getBoolean("enabled")).build() }, username
        )
        users.firstOrNull() ?: throw UsernameNotFoundException("User not found")
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val forbiddenHandler = AccessDeniedHandler { request, response, _ ->
            response.status = 403
            request.getRequestDispatcher("/forbidden").forward(request, response)
        }
        return http
        .authorizeHttpRequests { auth -> auth
            .requestMatchers("/login", "/forbidden", "/assets/**", "/exercise-images/**", "/error").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        }
        .exceptionHandling { exceptions -> exceptions.accessDeniedHandler(forbiddenHandler) }
        .formLogin { form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll() }
        .logout { logout -> logout.logoutSuccessUrl("/login?logout") }
        .build()
    }
}
