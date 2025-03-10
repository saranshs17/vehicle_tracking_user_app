package com.example.vehicle_tracking_user_app.models

data class UserSignupRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String,
    val photo: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String
)

data class UserProfile(
    val _id: String,
    val name: String,
    val email: String,
    val phone: String,
    val photo: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class UpdateProfileRequest(
    val name: String?,
    val email: String?,
    val phone: String?,
    val photo: String?
)

data class DriverRequest(
    val driverId: String
)

data class GenericResponse(
    val message: String
)

data class TokenUpdateRequest(
    val token: String
)
data class DriverResponse(
    val _id: String,
    val name: String,
    val email: String,
    val phone: String,
    val plateNumber: String,
    val status: String,
)