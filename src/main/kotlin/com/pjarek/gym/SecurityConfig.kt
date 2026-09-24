package com.pjarek.gym

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = LongPasswordEncoder()

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
    fun filterChain(http: HttpSecurity, jdbc: JdbcTemplate): SecurityFilterChain {
        val forbiddenHandler = AccessDeniedHandler { request, response, _ ->
            response.status = 403
            request.getRequestDispatcher("/forbidden").forward(request, response)
        }
        return http
        .addFilterBefore(TemporaryPasswordFilter(jdbc), AuthorizationFilter::class.java)
        .authorizeHttpRequests { auth -> auth
            .requestMatchers("/login", "/forbidden", "/assets/**", "/exercise-images/**", "/error").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        }
        .exceptionHandling { exceptions -> exceptions.accessDeniedHandler(forbiddenHandler) }
        .formLogin { form -> form.loginPage("/login").successHandler { _, response, authentication ->
            val username = authentication.name
            val mustChange = jdbc.queryForObject("SELECT must_change_password FROM app_user WHERE username=?", Boolean::class.java, username)!!
            response.sendRedirect(if (mustChange) "/change-password" else "/")
        }.permitAll() }
        .logout { logout -> logout.logoutSuccessUrl("/login?logout") }
        .build()
    }
}

private class TemporaryPasswordFilter(private val jdbc: JdbcTemplate) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI == "/login" || request.requestURI == "/logout" ||
            request.requestURI == "/change-password" || request.requestURI == "/error" ||
            request.requestURI == "/forbidden" || request.requestURI.startsWith("/assets/") ||
            request.requestURI.startsWith("/exercise-images/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication.isAuthenticated && authentication !is AnonymousAuthenticationToken) {
            val mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM app_user WHERE username=?",
                Boolean::class.java,
                authentication.name
            )!!
            if (mustChange) {
                response.sendRedirect("/change-password")
                return
            }
        }
        chain.doFilter(request, response)
    }
}
