package com.wxn0brp.viv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReceivedNotification(
    val title: String?,
    val body: String?,
    val timestamp: Long = System.currentTimeMillis()
)

object NotificationStorage {
    private val _notifications = MutableStateFlow<List<ReceivedNotification>>(emptyList())
    val notifications: StateFlow<List<ReceivedNotification>> = _notifications.asStateFlow()

    fun addNotification(notification: ReceivedNotification) {
        _notifications.value = listOf(notification) + _notifications.value
    }

    fun removeNotification(notification: ReceivedNotification) {
        _notifications.value = _notifications.value.filter { it != notification }
    }

    fun clearOldNotifications() {
        val twentyDaysAgo = System.currentTimeMillis() - (20L * 24 * 60 * 60 * 1000)
        _notifications.value = _notifications.value.filter { it.timestamp >= twentyDaysAgo }
    }
}
