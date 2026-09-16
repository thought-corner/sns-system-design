package com.project.sns.auth.application

import jakarta.servlet.http.HttpServletRequest

interface LoginConcurrencyGuard {
    fun acquireUntilRequestCompletion(principalName: String, request: HttpServletRequest)

    fun release(request: HttpServletRequest)
}
