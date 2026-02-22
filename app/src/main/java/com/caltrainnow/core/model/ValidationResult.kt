package com.caltrainnow.core.model

/**
 * Result of schedule validation checks.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
