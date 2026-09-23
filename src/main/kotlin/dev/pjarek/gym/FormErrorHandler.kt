package dev.pjarek.gym

import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.security.access.AccessDeniedException
import java.net.URI

@ControllerAdvice
class FormErrorHandler {
    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun forbidden() = "forbidden"

    @ExceptionHandler(EmptyResultDataAccessException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound() = "not-found"

    @ExceptionHandler(IllegalArgumentException::class, MissingServletRequestParameterException::class, MethodArgumentTypeMismatchException::class)
    fun invalidInput(request: HttpServletRequest, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute("errorMessage", "Some values are missing or invalid. Check the form and try again.")
        return "redirect:${localRefererPath(request) ?: "/"}"
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(request: HttpServletRequest, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute("errorMessage", "That change conflicts with existing data. Check the values and try again.")
        return "redirect:${localRefererPath(request) ?: "/"}"
    }

    private fun localRefererPath(request: HttpServletRequest): String? = runCatching {
        val uri = URI(request.getHeader("Referer"))
        val path = uri.rawPath ?: "/"
        if (path.startsWith("/") && !path.startsWith("//")) path else null
    }.getOrNull()
}
