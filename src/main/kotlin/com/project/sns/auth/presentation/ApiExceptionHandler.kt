package com.project.sns.auth.presentation

import com.project.sns.common.exception.ApplicationException
import com.project.sns.common.exception.ErrorType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(exception: ApplicationException): ResponseEntity<ApiErrorResponse> {
        val errorCode = exception.errorCode
        return ResponseEntity.status(errorCode.type.toHttpStatus()).body(
            ApiErrorResponse(
                code = errorCode.code,
                message = errorCode.message,
            ),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(exception: MethodArgumentNotValidException) = ApiErrorResponse(
        code = "INVALID_REQUEST",
        message = "요청 값이 올바르지 않습니다.",
        fieldErrors = exception.bindingResult.fieldErrors
            .groupBy(FieldError::getField)
            .mapValues { (_, errors) -> errors.first().defaultMessage ?: "올바르지 않은 값입니다." },
    )

    private fun ErrorType.toHttpStatus(): HttpStatus = when (this) {
        ErrorType.BAD_REQUEST -> HttpStatus.BAD_REQUEST
        ErrorType.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        ErrorType.FORBIDDEN -> HttpStatus.FORBIDDEN
        ErrorType.CONFLICT -> HttpStatus.CONFLICT
        ErrorType.INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
    }
}

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)
