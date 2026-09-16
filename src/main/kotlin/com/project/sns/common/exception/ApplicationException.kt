package com.project.sns.common.exception

abstract class ApplicationException(
    val errorCode: ErrorCode,
    cause: Throwable? = null,
) : RuntimeException(errorCode.message, cause)
