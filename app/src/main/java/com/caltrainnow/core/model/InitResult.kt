package com.caltrainnow.core.model

/**
 * Result of the schedule initialization process.
 */
data class InitResult(
    val success: Boolean,
    val stationCount: Int = 0,
    val tripCount: Int = 0,
    val stopTimeCount: Int = 0,
    val validationResult: ValidationResult? = null,
    val errorMessage: String? = null
)
