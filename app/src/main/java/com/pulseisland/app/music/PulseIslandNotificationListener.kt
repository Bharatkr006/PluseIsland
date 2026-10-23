package com.pulseisland.app.music

import android.service.notification.NotificationListenerService

/**
 * A minimal NotificationListenerService stub required for MediaSessionManager access.
 *
 * Android requires an enabled NotificationListenerService to call
 * MediaSessionManager.getActiveSessions() and addOnActiveSessionsChangedListener().
 * This service does NOT read or intercept any notifications; it is only used as
 * an authentication token by the MediaSessionManager API.
 *
 * The user must grant "Notification access" in Android Settings → Apps → Special app access.
 */
class PulseIslandNotificationListener : NotificationListenerService() {
    // No-op: we do not consume notifications.
    // MediaSessionManager uses our ComponentName as a trusted token.
}
