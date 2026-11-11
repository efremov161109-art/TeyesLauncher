package com.example.bluedrivelauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        var currentTrackInfo: String = ""
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == "ru.yandex.music") {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val artist = extras.getString(Notification.EXTRA_TEXT) ?: ""
            if (title.isNotEmpty()) {
                currentTrackInfo = if (artist.isNotEmpty()) "$artist — $title" else title
            }
        }
    }
}
