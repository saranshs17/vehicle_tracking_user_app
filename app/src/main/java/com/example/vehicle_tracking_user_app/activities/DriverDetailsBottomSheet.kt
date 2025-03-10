package com.example.vehicle_tracking_user_app.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.vehicle_tracking_user_app.R
import com.example.vehicle_tracking_user_app.models.DriverRequest
import com.example.vehicle_tracking_user_app.models.GenericResponse
import com.example.vehicle_tracking_user_app.network.ApiService
import com.example.vehicle_tracking_user_app.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DriverDetailsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverContact: TextView
    private lateinit var btnSendRequest: Button
    private var driverId: String? = null

    companion object {
        fun newInstance(driverId: String): DriverDetailsBottomSheet {
            val fragment = DriverDetailsBottomSheet()
            val args = Bundle()
            args.putString("driverId", driverId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_driver_details, container, false)
        tvDriverName = view.findViewById(R.id.tvDriverName)
        tvDriverContact = view.findViewById(R.id.tvDriverContact)
        btnSendRequest = view.findViewById(R.id.btnSendRequest)

        driverId = arguments?.getString("driverId")
        // In a real app, fetch the complete driver details from your backend using driverId.
        tvDriverName.text = "Driver ID: $driverId"
        tvDriverContact.text = "Contact: +1234567890"  // Replace with real data if available.

        btnSendRequest.setOnClickListener {
            driverId?.let { id ->
                sendDriverRequest(id)
            }
        }
        return view
    }

    private fun sendDriverRequest(driverId: String) {
        // Retrieve the stored token from SharedPreferences.
        val token = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("token", "") ?: ""
        val apiService = RetrofitClient.instance.create(ApiService::class.java)
        apiService.requestDriver("Bearer $token", DriverRequest(driverId))
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Request sent successfully.", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else {
                        Toast.makeText(context, "Failed to send request.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
