package com.project.sns.post.domain

import com.project.sns.common.exception.ApplicationException
import com.project.sns.common.exception.ErrorCode
import com.project.sns.common.exception.ErrorType

enum class PostErrorCode(
    override val message: String,
    override val type: ErrorType,
) : ErrorCode {
    POST_NOT_FOUND(
        message = "게시글을 찾을 수 없습니다.",
        type = ErrorType.NOT_FOUND,
    ),
    NOT_POST_AUTHOR(
        message = "게시글 작성자만 삭제할 수 있습니다.",
        type = ErrorType.FORBIDDEN,
    ),
    ;

    override val code: String = name
}

class PostNotFoundException : ApplicationException(PostErrorCode.POST_NOT_FOUND)

class NotPostAuthorException : ApplicationException(PostErrorCode.NOT_POST_AUTHOR)
