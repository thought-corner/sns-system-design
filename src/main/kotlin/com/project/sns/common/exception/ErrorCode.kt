package com.project.sns.common.exception

interface ErrorCode {
    val code: String
    val message: String
    val type: ErrorType
}

enum class ErrorType {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    CONFLICT,
    INTERNAL_SERVER_ERROR,
}
