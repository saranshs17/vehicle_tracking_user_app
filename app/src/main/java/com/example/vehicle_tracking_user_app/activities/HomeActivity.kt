package com.example.vehicle_tracking_user_app.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.example.vehicle_tracking_user_app.R
import com.example.vehicle_tracking_user_app.models.DriverRequest
import com.example.vehicle_tracking_user_app.models.DriverResponse
import com.example.vehicle_tracking_user_app.models.GenericResponse
import com.example.vehicle_tracking_user_app.models.RequestStatusResponse
import com.example.vehicle_tracking_user_app.network.ApiService
import com.example.vehicle_tracking_user_app.network.RetrofitClient
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.FusedLocationProviderClient

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult

import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.color.DynamicColors
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var userLocation: Location? = null

    private lateinit var geoFire: GeoFire

    // Map to store markers keyed by driverId.
    private val markersMap = mutableMapOf<String, Marker>()

    // GeoFire instance for updating user's location (using "user_locations" node)
    private lateinit var userGeoFire: GeoFire

    // Bottom sheet is a ConstraintLayout
    private lateinit var bottomSheet: ConstraintLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverContact: TextView
    private lateinit var btnSendRequest: Button
    private lateinit var dragHandle: View

    private var selectedDriverId: String? = null
    private lateinit var bottomNavigationView: BottomNavigationView

    // Location updates
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var shouldHideButton = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
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
                        btnSendRequest.visibility = if (shouldHideButton) View.GONE else View.VISIBLE
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
        val driverRef = FirebaseDatabase.getInstance().getReference("driver_locations")
        geoFire = GeoFire(driverRef)

        // Setup GeoFire for "user_locations"
        val userRef = FirebaseDatabase.getInstance().getReference("user_locations")
        userGeoFire = GeoFire(userRef)

        // Initialize location updates
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationUpdates()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000    // update every 10 seconds
            fastestInterval = 5000 // fastest update every 5 seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    // Update driver's location.
//                    updateDriverLocation(location)
                    // Update user's location if needed.
                    updateUserLocation(location)
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val styleRes = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            R.raw.midnight // Your night style JSON file in res/raw
        } else {
            R.raw.gmaps       // Your day style JSON file in res/raw
        }
        try {
            val success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleRes))
            if (!success) {
                Log.e("MapStyle", "Style parsing failed.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("MapStyle", "Can't find style. Error: ", e)
        }
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
                updateUserLocation(location)
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
//                checkRequestStatusForDriver(driverId)
//                fetchDriverDetailsFromBackend(driverId)
                handleMarkerClick(driverId)
            }
            true
        }
    }
    // New version: check request status with a callback
    private fun checkRequestStatusForDriver(driverId: String, callback: (Boolean) -> Unit) {
        val token = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("token", "") ?: ""
        val apiService = RetrofitClient.instance.create(ApiService::class.java)

        apiService.getRequestForDriver("Bearer $token", driverId)
            .enqueue(object : Callback<RequestStatusResponse> {
                override fun onResponse(
                    call: Call<RequestStatusResponse>,
                    response: Response<RequestStatusResponse>
                ) {
                    if (response.isSuccessful) {
                        val status = response.body()?.status ?: "pending"
                        val accepted = status.equals("accepted", ignoreCase = true)
//                        Toast.makeText(this@HomeActivity, "${accepted}", Toast.LENGTH_SHORT).show()
                        callback(accepted)
                    } else {
                        // On error, we assume not accepted so show button.
                        callback(false)
                    }
                }
                override fun onFailure(call: Call<RequestStatusResponse>, t: Throwable) {
                    callback(false)
                    Toast.makeText(this@HomeActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // This function handles a marker click by first checking the request status,
// then fetching driver details.
    private fun handleMarkerClick(driverId: String) {
        checkRequestStatusForDriver(driverId) { accepted ->
            shouldHideButton = accepted
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
//            if (accepted) {
//                btnSendRequest.visibility = View.GONE
//            } else {
//                btnSendRequest.visibility = View.VISIBLE
//            }
            fetchDriverDetailsFromBackend(driverId)
        }
    }

    private fun queryNearbyDrivers(location: Location) {
        val geoQuery = geoFire.queryAtLocation(GeoLocation(location.latitude, location.longitude), 5.0)
        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) {
                val driverLatLng = LatLng(location.latitude, location.longitude)
                runOnUiThread {
                    if (markersMap.containsKey(key)) {
                        // Update marker position if it already exists.
                        markersMap[key]?.position = driverLatLng
                    } else {
                        val markerOptions = MarkerOptions().position(driverLatLng).title(key)
                        val marker = mMap.addMarker(markerOptions)
                        if (marker != null) {
                            markersMap[key] = marker
                        }
                    }
                }
            }

            override fun onKeyExited(key: String) {
                runOnUiThread {
                    markersMap[key]?.remove()
                    markersMap.remove(key)
                }
            }

            override fun onKeyMoved(key: String, location: GeoLocation) {
                val newLatLng = LatLng(location.latitude, location.longitude)
                runOnUiThread {
                    markersMap[key]?.position = newLatLng
                }
            }

            override fun onGeoQueryReady() {}

            override fun onGeoQueryError(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "GeoQuery error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    // Update user's location in Firebase using GeoFire.
    private fun updateUserLocation(location: Location) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val userId = prefs.getString("userId", null)
        if (userId == null) {
            Toast.makeText(this, "User ID not set. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }
        userGeoFire.setLocation(userId, GeoLocation(location.latitude, location.longitude)) { key, error ->
            if (error != null) {
                Log.e("GeoFire", "Error updating location for user: $key, error: ${error.message}")
            } else {
                Log.d("GeoFire", "User location updated successfully for: $key")
            }
        }
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
    private var userMarker: Marker? = null
    private fun startUserLocationListener(userId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("user_locations").child(userId)
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val typeIndicator = object : GenericTypeIndicator<List<Double>>() {}
                    val locationList = snapshot.child("l").getValue(typeIndicator)
                    if (locationList != null && locationList.size >= 2) {
                        val lat = locationList[0]
                        val lng = locationList[1]
                        val userLatLng = LatLng(lat, lng)
                        if (userMarker == null) {
                            userMarker = mMap.addMarker(MarkerOptions().position(userLatLng).title("Accepted User"))
                        } else {
                            userMarker?.position = userLatLng
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun checkRequestStatusForDriver(driverId: String) {
        val token = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("token", "") ?: ""
        val apiService = RetrofitClient.instance.create(ApiService::class.java)

        apiService.getRequestForDriver("Bearer $token", driverId)
            .enqueue(object : Callback<RequestStatusResponse> {
                override fun onResponse(call: Call<RequestStatusResponse>, response: Response<RequestStatusResponse>) {
                    if (response.isSuccessful) {
                        val status = response.body()?.status ?: "pending"
                        if (status.equals("accepted", ignoreCase = true)) {
                            btnSendRequest.visibility = View.GONE
                        } else {
                            btnSendRequest.visibility = View.VISIBLE
                        }
                    } else {
                        // If response is not successful, you might choose to show the button.
                        btnSendRequest.visibility = View.VISIBLE
                    }
                }

                override fun onFailure(call: Call<RequestStatusResponse>, t: Throwable) {
                    btnSendRequest.visibility = View.VISIBLE
                    Toast.makeText(this@HomeActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }


    override fun onDestroy() {
        super.onDestroy()
        // Stop location updates to avoid leaks.
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}