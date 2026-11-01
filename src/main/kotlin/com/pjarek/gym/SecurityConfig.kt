package com.pjarek.gym

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    fun userDetailsService(accounts: AccountRepository): UserDetailsService = UserDetailsService { username ->
        val account = accounts.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found")
        User.withUsername(account.username).password(account.passwordHash).roles(account.role)
            .disabled(!account.enabled).build()
    }

    @Bean
    fun filterChain(http: HttpSecurity, accounts: AccountService): SecurityFilterChain {
        val forbiddenHandler = AccessDeniedHandler { request, response, _ ->
            response.status = 403
            request.getRequestDispatcher("/forbidden").forward(request, response)
        }
        return http
        .addFilterBefore(TemporaryPasswordFilter(accounts), AuthorizationFilter::class.java)
        .authorizeHttpRequests { auth -> auth
            .requestMatchers("/login", "/forbidden", "/favicon.ico", "/assets/**", "/exercise-images/**", "/error").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        }
        .exceptionHandling { exceptions -> exceptions.accessDeniedHandler(forbiddenHandler) }
        .formLogin { form -> form.loginPage("/login").successHandler { _, response, authentication ->
            val username = authentication.name
            val mustChange = accounts.account(username).mustChangePassword
            response.sendRedirect(if (mustChange) "/change-password" else "/")
        }.permitAll() }
        .logout { logout -> logout.logoutSuccessUrl("/login?logout") }
        .build()
    }
}

private class TemporaryPasswordFilter(private val accounts: AccountService) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI == "/login" || request.requestURI == "/logout" ||
            request.requestURI == "/change-password" || request.requestURI == "/error" ||
            request.requestURI == "/forbidden" || request.requestURI.startsWith("/assets/") ||
            request.requestURI.startsWith("/exercise-images/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication.isAuthenticated && authentication !is AnonymousAuthenticationToken) {
            val mustChange = accounts.account(authentication.name).mustChangePassword
            if (mustChange) {
                response.sendRedirect("/change-password")
                return
            }
        }
        chain.doFilter(request, response)
    }
}
