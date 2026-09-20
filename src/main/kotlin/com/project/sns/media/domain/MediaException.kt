package com.project.sns.media.domain

import com.project.sns.common.exception.ApplicationException
import com.project.sns.common.exception.ErrorCode
import com.project.sns.common.exception.ErrorType

enum class MediaErrorCode(
    override val message: String,
    override val type: ErrorType,
) : ErrorCode {
    MEDIA_NOT_FOUND(
        message = "미디어를 찾을 수 없습니다.",
        type = ErrorType.NOT_FOUND,
    ),
    MEDIA_TYPE_NOT_ALLOWED(
        message = "허용되지 않는 미디어 형식입니다.",
        type = ErrorType.BAD_REQUEST,
    ),
    MEDIA_TOO_LARGE(
        message = "미디어 크기가 상한을 넘습니다.",
        type = ErrorType.BAD_REQUEST,
    ),
    MEDIA_NOT_UPLOADED(
        message = "업로드가 완료되지 않은 미디어입니다.",
        type = ErrorType.CONFLICT,
    ),
    MEDIA_INVALID(
        message = "업로드된 파일이 선언한 미디어와 다릅니다.",
        type = ErrorType.BAD_REQUEST,
    ),
    MEDIA_NOT_READY(
        message = "완료 처리되지 않은 미디어는 첨부할 수 없습니다.",
        type = ErrorType.CONFLICT,
    ),
    MEDIA_ALREADY_ATTACHED(
        message = "이미 다른 게시글에 첨부된 미디어입니다.",
        type = ErrorType.CONFLICT,
    ),
    ;

    override val code: String = name
}

class MediaNotFoundException : ApplicationException(MediaErrorCode.MEDIA_NOT_FOUND)

class MediaTypeNotAllowedException : ApplicationException(MediaErrorCode.MEDIA_TYPE_NOT_ALLOWED)

class MediaTooLargeException : ApplicationException(MediaErrorCode.MEDIA_TOO_LARGE)

class MediaNotUploadedException : ApplicationException(MediaErrorCode.MEDIA_NOT_UPLOADED)

class MediaInvalidException : ApplicationException(MediaErrorCode.MEDIA_INVALID)

class MediaNotReadyException : ApplicationException(MediaErrorCode.MEDIA_NOT_READY)

class MediaAlreadyAttachedException : ApplicationException(MediaErrorCode.MEDIA_ALREADY_ATTACHED)
