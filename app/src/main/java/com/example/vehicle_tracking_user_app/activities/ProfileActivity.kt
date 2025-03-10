package com.example.vehicle_tracking_user_app.activities


import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vehicle_tracking_user_app.R
import com.example.vehicle_tracking_user_app.models.UpdateProfileRequest
import com.example.vehicle_tracking_user_app.models.UserProfile
import com.example.vehicle_tracking_user_app.network.ApiService
import com.example.vehicle_tracking_user_app.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnUpdate: Button
    private lateinit var token: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        btnUpdate = findViewById(R.id.btnUpdate)

        token = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("token", "") ?: ""

        loadProfile()

        btnUpdate.setOnClickListener {
            val updateRequest = UpdateProfileRequest(
                name = etName.text.toString(),
                email = etEmail.text.toString(),
                phone = etPhone.text.toString(),
                photo = null
            )
            val apiService = RetrofitClient.instance.create(ApiService::class.java)
            apiService.updateProfile("Bearer $token", updateRequest)
                .enqueue(object : Callback<UserProfile> {
                    override fun onResponse(call: Call<UserProfile>, response: Response<UserProfile>) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@ProfileActivity, "Profile updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ProfileActivity, "Update failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<UserProfile>, t: Throwable) {
                        Toast.makeText(this@ProfileActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun loadProfile() {
        val apiService = RetrofitClient.instance.create(ApiService::class.java)
        apiService.getProfile("Bearer $token")
            .enqueue(object : Callback<UserProfile> {
                override fun onResponse(call: Call<UserProfile>, response: Response<UserProfile>) {
                    if (response.isSuccessful) {
                        val profile = response.body()
                        etName.setText(profile?.name)
                        etEmail.setText(profile?.email)
                        etPhone.setText(profile?.phone)
                    }
                }
                override fun onFailure(call: Call<UserProfile>, t: Throwable) {
                    Toast.makeText(this@ProfileActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
