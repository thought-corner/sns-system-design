package com.project.sns.post.presentation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * 코드포인트 수 상한. `@Size` 는 UTF-16 단위(`String.length`)로 세어 이모지 하나를 2 로 보지만
 * PostgreSQL `VARCHAR(n)` 은 문자(코드포인트) 단위라, "500자" 계약을 DB 와 같은 단위로 검증한다.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxCodePointsValidator::class])
annotation class MaxCodePoints(
    val value: Int,
    val message: String = "본문은 {value}자 이하여야 합니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MaxCodePointsValidator : ConstraintValidator<MaxCodePoints, String> {
    private var max = 0

    override fun initialize(constraintAnnotation: MaxCodePoints) {
        max = constraintAnnotation.value
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || value.codePointCount(0, value.length) <= max
}
