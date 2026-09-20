package com.project.sns.post.presentation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NotNullElementsValidator::class])
annotation class NotNullElements(
    val message: String = "목록에 null 이 있을 수 없습니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NotNullElementsValidator : ConstraintValidator<NotNullElements, Collection<*>> {
    override fun isValid(value: Collection<*>?, context: ConstraintValidatorContext): Boolean =
        value == null || value.all { it != null }
}
