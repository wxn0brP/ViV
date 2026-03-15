package com.wxn0brp.viv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.wxn0brp.viv.ui.theme.ViVTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var fcmToken by mutableStateOf("Pobieranie tokena...")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()
        
        val database = (application as ViVApplication).database
        val notificationDao = database.notificationDao()

        // Obsługa powiadomienia, które otworzyło aplikację
        handleIntent(intent)

        // Usuwanie starych powiadomień przy starcie (starsze niż 20 dni)
        lifecycleScope.launch {
            val twentyDaysAgo = System.currentTimeMillis() - (20L * 24 * 60 * 60 * 1000)
            notificationDao.deleteOldNotifications(twentyDaysAgo)
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                fcmToken = "Błąd pobierania tokena"
                return@OnCompleteListener
            }

            val token = task.result
            fcmToken = token ?: "Brak tokena"
            Log.d(TAG, "FCM Token: $token")
        })

        setContent {
            ViVTheme {
                val notifications by notificationDao.getAllNotifications().collectAsState(initial = emptyList())
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        token = fcmToken,
                        notifications = notifications,
                        onDeleteClick = { notification ->
                            lifecycleScope.launch {
                                notificationDao.delete(notification)
                            }
                        },
                        onDeleteAllClick = {
                            lifecycleScope.launch {
                                notificationDao.deleteAll()
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.extras?.let { extras ->
            // Firebase konsola wysyła dane w tych kluczach, gdy app jest w tle
            val title = extras.getString("gcm.notification.title") ?: extras.getString("title")
            val body = extras.getString("gcm.notification.body") ?: extras.getString("body")

            if (title != null || body != null) {
                val database = (application as ViVApplication).database
                lifecycleScope.launch {
                    database.notificationDao().insert(
                        NotificationEntity(
                            title = title,
                            body = body,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                // Czyścimy extras, żeby nie dodać tego samego powiadomienia przy obracaniu ekranu
                intent.removeExtra("gcm.notification.title")
                intent.removeExtra("gcm.notification.body")
                intent.removeExtra("title")
                intent.removeExtra("body")
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun MainScreen(
    token: String, 
    notifications: List<NotificationEntity>, 
    onDeleteClick: (NotificationEntity) -> Unit,
    onDeleteAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTokenVisible by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = "Usuń wszystkie") },
            text = { Text(text = "Czy na pewno chcesz usunąć wszystkie powiadomienia?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllClick()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Tak, usuń")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Twój FCM Token:",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = token,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text("FCM Token") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                    Icon(imageVector = icon, contentDescription = if (isTokenVisible) "Ukryj" else "Pokaż")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historia powiadomień:",
                style = MaterialTheme.typography.titleLarge
            )
            
            if (notifications.isNotEmpty()) {
                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Usuń wszystko",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (notifications.isEmpty()) {
            Text(
                text = "Brak otrzymanych powiadomień.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationItem(notification, onDeleteClick)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun NotificationItem(notification: NotificationEntity, onDeleteClick: (NotificationEntity) -> Unit) {
    val sdf = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
    val time = sdf.format(Date(notification.timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "[$time] ${notification.title ?: "Brak tytułu"}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body ?: "Brak treści",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            IconButton(onClick = { onDeleteClick(notification) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Usuń",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ViVTheme {
        MainScreen(
            token = "sample-token",
            notifications = listOf(
                NotificationEntity(1, "Tytuł 1", "Treść powiadomienia 1", System.currentTimeMillis()),
                NotificationEntity(2, "Tytuł 2", "Treść powiadomienia 2", System.currentTimeMillis())
            ),
            onDeleteClick = {},
            onDeleteAllClick = {}
        )
    }
}
