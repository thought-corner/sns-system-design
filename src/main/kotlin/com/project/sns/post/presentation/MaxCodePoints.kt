package com.project.sns.post.presentation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

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
