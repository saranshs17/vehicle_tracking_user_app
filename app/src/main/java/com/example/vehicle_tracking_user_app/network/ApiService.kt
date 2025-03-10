package com.example.vehicle_tracking_user_app.network

import com.example.vehicle_tracking_user_app.models.*
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    @POST("api/user/signup")
    fun signup(@Body user: UserSignupRequest): Call<GenericResponse>

    @POST("api/user/login")
    fun login(@Body loginRequest: LoginRequest): Call<LoginResponse>

    @GET("api/user/profile")
    fun getProfile(@Header("Authorization") token: String): Call<UserProfile>

    @PUT("api/user/profile")
    fun updateProfile(@Header("Authorization") token: String, @Body updateRequest: UpdateProfileRequest): Call<UserProfile>

    @PUT("api/user/updateToken")
    fun updateToken(@Header("Authorization") token: String, @Body tokenUpdate: TokenUpdateRequest ) : Call<GenericResponse>

    @GET("api/driver/{driverId}")
    fun getDriverById(
        @Header("Authorization") token: String,
        @Path("driverId") driverId: String
    ): Call<DriverResponse>

    @POST("api/user/request")
    fun requestDriver(@Header("Authorization") token: String, @Body request: DriverRequest): Call<GenericResponse>
}