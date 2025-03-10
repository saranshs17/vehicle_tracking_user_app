package com.example.vehicle_tracking_user_app.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.example.vehicle_tracking_user_app.R
import com.example.vehicle_tracking_user_app.models.DriverRequest
import com.example.vehicle_tracking_user_app.models.DriverResponse
import com.example.vehicle_tracking_user_app.models.GenericResponse
import com.example.vehicle_tracking_user_app.network.ApiService
import com.example.vehicle_tracking_user_app.network.RetrofitClient
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var userLocation: Location? = null

    private lateinit var geoFire: GeoFire

    // Bottom sheet is a ConstraintLayout
    private lateinit var bottomSheet: ConstraintLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverContact: TextView
    private lateinit var btnSendRequest: Button
    private lateinit var dragHandle: View

    private var selectedDriverId: String? = null
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Initialize bottom sheet views
        bottomSheet = findViewById(R.id.bottomSheet)
        tvDriverName = findViewById(R.id.tvDriverName)
        tvDriverContact = findViewById(R.id.tvDriverContact)
        btnSendRequest = findViewById(R.id.btnSendRequest)
        dragHandle = findViewById(R.id.dragHandle)

        // Configure the bottom sheet behavior
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // Make sure dragging is properly enabled
        bottomSheetBehavior.isDraggable = true
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.skipCollapsed = false  // Allow the sheet to be collapsed rather than immediately hidden
        bottomSheetBehavior.peekHeight = 120  // Show a small part of the sheet when collapsed

        // Set up behavior states
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        // Clear selected driver when sheet is hidden
                        selectedDriverId = null
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        // Optionally show minimal information when collapsed
                        btnSendRequest.visibility = View.GONE
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        // Make sure button is visible in expanded state
                        btnSendRequest.visibility = View.VISIBLE
                    }
                    BottomSheetBehavior.STATE_DRAGGING -> {
                        // Optional: Handle dragging state if needed
                    }
                    BottomSheetBehavior.STATE_SETTLING -> {
                        // Optional: Handle settling state if needed
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Animate UI elements based on slide position
                // slideOffset ranges from -1 (hidden) to 1 (expanded)

                // Optional: Fade in/out elements based on slide position
                val alpha = 0.5f + slideOffset / 2
                tvDriverContact.alpha = alpha
                btnSendRequest.alpha = alpha
            }
        })

        // Add manual collapse/expand capability with touch on the drag handle
        dragHandle.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        // Set up button click listener
        btnSendRequest.setOnClickListener {
            selectedDriverId?.let { driverId ->
                sendDriverRequest(driverId)
            }
        }

        // Bottom navigation
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Setup GeoFire for "driver_locations"
        val databaseRef = FirebaseDatabase.getInstance().getReference("driver_locations")
        geoFire = GeoFire(databaseRef)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Request location permissions if needed
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        mMap.isMyLocationEnabled = true

        // Retrieve user location
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLocation = location
                val userLatLng = LatLng(location.latitude, location.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                queryNearbyDrivers(location)
            } else {
                Toast.makeText(this, "Unable to retrieve location.", Toast.LENGTH_SHORT).show()
            }
        }

        // Marker click -> fetch driver details from Node.js (via Retrofit)
        mMap.setOnMarkerClickListener { marker: Marker ->
            val driverId = marker.title
            if (driverId != null) {
                selectedDriverId = driverId
                fetchDriverDetailsFromBackend(driverId)
            }
            true
        }
    }

    private fun queryNearbyDrivers(location: Location) {
        val geoQuery = geoFire.queryAtLocation(GeoLocation(location.latitude, location.longitude), 5.0)
        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) {
                val driverLatLng = LatLng(location.latitude, location.longitude)
                runOnUiThread {
                    val markerOptions = MarkerOptions().position(driverLatLng).title(key)
                    mMap.addMarker(markerOptions)
                }
            }
            override fun onKeyExited(key: String) {}
            override fun onKeyMoved(key: String, location: GeoLocation) {}
            override fun onGeoQueryReady() {}
            override fun onGeoQueryError(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "GeoQuery error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchDriverDetailsFromBackend(driverId: String) {
        val token = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("token", "") ?: ""
        val apiService = RetrofitClient.instance.create(ApiService::class.java)
        apiService.getDriverById("Bearer $token", driverId)
            .enqueue(object : Callback<DriverResponse> {
                override fun onResponse(call: Call<DriverResponse>, response: Response<DriverResponse>) {
                    if (response.isSuccessful) {
                        val driverData = response.body()
                        if (driverData != null) {
                            tvDriverName.text = "Driver: ${driverData.name}"
                            tvDriverContact.text = "Contact: ${driverData.phone}"
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    } else {
                        Toast.makeText(this@HomeActivity, "Driver details not found (API).", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<DriverResponse>, t: Throwable) {
                    Toast.makeText(this@HomeActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // Method to send a driver request
    private fun sendDriverRequest(driverId: String) {
        val token = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("token", "") ?: ""
        val apiService = RetrofitClient.instance.create(ApiService::class.java)
        apiService.requestDriver("Bearer $token", DriverRequest(driverId = driverId))
            .enqueue(object : Callback<GenericResponse> {
                override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@HomeActivity, "Request sent successfully.", Toast.LENGTH_SHORT).show()
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    } else {
                        Toast.makeText(this@HomeActivity, "Failed to send request.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                    Toast.makeText(this@HomeActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            onMapReady(mMap)
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
        }
    }
}