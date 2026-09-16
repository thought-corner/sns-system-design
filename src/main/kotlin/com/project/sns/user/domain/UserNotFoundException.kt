package com.project.sns.user.domain

import com.project.sns.common.exception.ApplicationException

class UserNotFoundException : ApplicationException(UserErrorCode.USER_NOT_FOUND)
