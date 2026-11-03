package com.evcockpit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.evcockpit.domain.model.ObdPhase
import com.evcockpit.domain.repository.ObdConnectionController

/**
 * User-initiated connected-device foreground service.
 *
 * Keeps the same singleton read-only ELM polling coroutine alive when the UI
 * is no longer visible. Does not reconnect without a new explicit user action.
 * Stop notification action closes the Bluetooth socket and clears live values.
 */
@AndroidEntryPoint
class ObdForegroundService : Service() {
    @Inject lateinit var obd: ObdConnectionController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectionMonitor: Job? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "OBD canlı teknik ölçümler", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            obd.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ObdForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_eya_car)
            .setContentTitle("Elektrikli Yol Arkadaşım")
            .setContentText("OBD teknik ölçümleri ve hesaplamaları çalışıyor")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Bağlantıyı kes", stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(NOTIFICATION_ID, notification)

        connectionMonitor?.cancel()
        connectionMonitor = scope.launch {
            var sessionStarted = false
            obd.connection.collect { state ->
                if (state.phase == ObdPhase.CONNECTING || state.phase == ObdPhase.CONNECTED)
                    sessionStarted = true
                if (sessionStarted &&
                    (state.phase == ObdPhase.ERROR || state.phase == ObdPhase.DISCONNECTED))
                    stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        connectionMonitor?.cancel()
        scope.cancel()
        obd.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.evcockpit.action.STOP_OBD"
        private const val CHANNEL = "eya_obd_engineering"
        private const val NOTIFICATION_ID = 2460
    }
}
