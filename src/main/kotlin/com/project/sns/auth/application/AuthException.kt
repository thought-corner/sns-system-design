package com.project.sns.auth.application

import com.project.sns.common.exception.ApplicationException
import com.project.sns.common.exception.ErrorCode
import com.project.sns.common.exception.ErrorType

enum class AuthErrorCode(
    override val message: String,
    override val type: ErrorType,
) : ErrorCode {
    INVALID_CREDENTIALS(
        message = "이메일 또는 비밀번호가 올바르지 않습니다.",
        type = ErrorType.UNAUTHORIZED,
    ),
    SESSION_LIMIT_EXCEEDED(
        message = "이미 로그인된 세션이 있습니다.",
        type = ErrorType.CONFLICT,
    ),
    ;

    override val code: String = name
}

class InvalidCredentialsException(cause: Throwable? = null) :
    ApplicationException(AuthErrorCode.INVALID_CREDENTIALS, cause)

class SessionLimitExceededException(cause: Throwable? = null) :
    ApplicationException(AuthErrorCode.SESSION_LIMIT_EXCEEDED, cause)
