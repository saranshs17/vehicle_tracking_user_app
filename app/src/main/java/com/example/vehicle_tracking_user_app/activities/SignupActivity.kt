package com.example.vehicle_tracking_user_app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vehicle_tracking_user_app.R
import com.example.vehicle_tracking_user_app.models.GenericResponse
import com.example.vehicle_tracking_user_app.models.UserSignupRequest
import com.example.vehicle_tracking_user_app.network.ApiService
import com.example.vehicle_tracking_user_app.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSignup: Button
    private lateinit var btnGoToLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etPhone = findViewById(R.id.etPhone)
        btnSignup = findViewById(R.id.btnSignup)
        btnGoToLogin = findViewById(R.id.btnGoToLogin)

        btnSignup.setOnClickListener {
            val name = etName.text.toString()
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            val phone = etPhone.text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiService = RetrofitClient.instance.create(ApiService::class.java)
            val signupRequest = UserSignupRequest(name, email, password, phone)
            apiService.signup(signupRequest).enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SignupActivity, "Signup successful! Please login.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@SignupActivity, "Signup failed", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@SignupActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
