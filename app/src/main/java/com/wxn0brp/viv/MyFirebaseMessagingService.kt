package com.wxn0brp.viv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCM_VIV", "Odebrano wiadomość! Data: ${remoteMessage.data}")

        // Pobieramy dane z sekcji 'data' (tak jak teraz wysyła Twój serwer JS)
        val title = remoteMessage.data["title"]
        val body = remoteMessage.data["body"]

        if (title != null || body != null) {
            // Zapisujemy do bazy NATYCHMIAST po odebraniu (nawet jeśli użytkownik usunie powiadomienie z paska)
            val database = (application as ViVApplication).database
            runBlocking {
                database.notificationDao().insert(
                    NotificationEntity(
                        title = title,
                        body = body,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            Log.d("FCM_VIV", "Zapisano do bazy Room: $title")

            // Ręcznie pokazujemy powiadomienie na pasku
            showNotification(title ?: "ViV", body ?: "")
        } else {
            Log.w("FCM_VIV", "Otrzymano pustą wiadomość (brak title i body w sekcji data)")
        }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "fcm_default_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Powiadomienia",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
