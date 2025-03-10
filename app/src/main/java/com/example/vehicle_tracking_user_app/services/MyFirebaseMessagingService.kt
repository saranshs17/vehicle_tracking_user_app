package com.example.vehicle_tracking_user_app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.vehicle_tracking_user_app.R
import com.example.vehicle_tracking_user_app.activities.HomeActivity
import com.example.vehicle_tracking_user_app.models.TokenUpdateRequest
import com.example.vehicle_tracking_user_app.network.ApiService
import com.example.vehicle_tracking_user_app.network.RetrofitClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MyFirebaseMessagingService : FirebaseMessagingService() {

    /**
     * Called when a new message is received.
     * Handles both notification and data payloads.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Log the source of the message.
        Log.d("FCM", "Message received from: ${remoteMessage.from}")

        // If message contains a notification payload, extract and display it.
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "Notification"
            val body = notification.body ?: ""
            sendNotification(title, body)
        }

        // If message contains data payload, log or process it as needed.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
            // You can parse and handle the data payload here if needed.
        }
    }

    /**
     * Called when a new FCM token is generated.
     * Updates the token on your backend for push notifications.
     */
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        Log.d("FCM", "New token generated: $newToken")
        updateTokenOnBackend(newToken)
    }

    /**
     * Sends the new FCM token to your backend server.
     */
    private fun updateTokenOnBackend(newToken: String) {
        // Retrieve the stored JWT token from SharedPreferences.
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val userAuthToken = sharedPref.getString("token", "") ?: ""
        if (userAuthToken.isNotEmpty()) {
            val apiService = RetrofitClient.instance.create(ApiService::class.java)
            val tokenUpdateRequest = TokenUpdateRequest(newToken)
            apiService.updateToken("Bearer $userAuthToken", tokenUpdateRequest)
                .enqueue(object : Callback<com.example.vehicle_tracking_user_app.models.GenericResponse> {
                    override fun onResponse(
                        call: Call<com.example.vehicle_tracking_user_app.models.GenericResponse>,
                        response: Response<com.example.vehicle_tracking_user_app.models.GenericResponse>
                    ) {
                        if (response.isSuccessful) {
                            Log.d("FCM", "Token updated on backend successfully.")
                        } else {
                            Log.e("FCM", "Failed to update token: ${response.errorBody()?.string()}")
                        }
                    }
                    override fun onFailure(
                        call: Call<com.example.vehicle_tracking_user_app.models.GenericResponse>,
                        t: Throwable
                    ) {
                        Log.e("FCM", "Error updating token: ${t.message}")
                    }
                })
        } else {
            Log.d("FCM", "No user token found; skipping backend token update.")
        }
    }

    /**
     * Builds and displays a local notification based on the title and message body.
     */
    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "default_channel_id"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications) // Ensure this icon exists in your drawable folder.
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // For Android Oreo and above, create a notification channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
