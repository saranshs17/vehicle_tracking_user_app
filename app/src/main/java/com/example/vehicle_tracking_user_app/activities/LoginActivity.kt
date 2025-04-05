package com.example.vehicle_tracking_user_app.activities


import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vehicle_tracking_user_app.R
import com.example.vehicle_tracking_user_app.models.LoginRequest
import com.example.vehicle_tracking_user_app.models.LoginResponse
import com.example.vehicle_tracking_user_app.network.ApiService
import com.example.vehicle_tracking_user_app.network.RetrofitClient
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnSignup: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnSignup = findViewById(R.id.btnSignup)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiService = RetrofitClient.instance.create(ApiService::class.java)
            val loginRequest = LoginRequest(email, password)
            apiService.login(loginRequest).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        val token = response.body()?.token ?: ""
                        val userId = response.body()?.userId ?: ""
                        // Save token in SharedPreferences for subsequent calls.
                        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        sharedPref.edit().apply {
                            putString("token", token)
                            putString("userId", userId)
                            apply()
                        }

                        val userRef = FirebaseDatabase.getInstance().getReference("user_locations")
                        val geoFire = GeoFire(userRef)
                        geoFire.setLocation(userId, GeoLocation(0.0, 0.0)) { key, error ->
                            if (error != null) {
                                // Log error if needed.
                            } else {
                                // Optionally log success.
                            }
                        }

                        // Navigate to HomeActivity.
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Toast.makeText(this@LoginActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        btnSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }
}
