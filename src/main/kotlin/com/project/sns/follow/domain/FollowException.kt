package com.project.sns.follow.domain

import com.project.sns.common.exception.ApplicationException
import com.project.sns.common.exception.ErrorCode
import com.project.sns.common.exception.ErrorType

enum class FollowErrorCode(
    override val message: String,
    override val type: ErrorType,
) : ErrorCode {
    SELF_FOLLOW_NOT_ALLOWED(
        message = "자기 자신을 팔로우할 수 없습니다.",
        type = ErrorType.BAD_REQUEST,
    ),
    ;

    override val code: String = name
}

class SelfFollowNotAllowedException : ApplicationException(FollowErrorCode.SELF_FOLLOW_NOT_ALLOWED)
