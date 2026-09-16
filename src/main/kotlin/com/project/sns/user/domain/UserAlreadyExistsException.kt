package com.project.sns.user.domain

import com.project.sns.common.exception.ApplicationException
import com.project.sns.common.exception.ErrorCode
import com.project.sns.common.exception.ErrorType

enum class UserErrorCode(
    override val message: String,
    override val type: ErrorType,
) : ErrorCode {
    USER_ALREADY_EXISTS(
        message = "이미 가입된 이메일입니다.",
        type = ErrorType.CONFLICT,
    ),
    ;

    override val code: String = name
}

class UserAlreadyExistsException(cause: Throwable? = null) :
    ApplicationException(UserErrorCode.USER_ALREADY_EXISTS, cause)
