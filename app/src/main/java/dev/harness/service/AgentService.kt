package dev.harness.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.harness.HarnessApp
import dev.harness.MainActivity
import dev.harness.R

/**
 * Keeps the agent runtime alive while the app is backgrounded.
 *
 * Android 12+ Phantom Process Killer can reap child processes; running the
 * PRoot/Node tree under a `specialUse` foreground service is the documented
 * mitigation (PROMPT §5.3). The notification is deliberately quiet and offers
 * a single "Parar" action.
 */
class AgentService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                container().session.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PROVISION -> {
                // Keep the process alive while the environment is prepared
                // (download + apk/npm), which can take a while and must not be
                // killed when the screen turns off.
                startForegroundCompat(provisioning = true)
                acquireWakeLock()
            }
            else -> {
                // Keeps the runtime alive; the UI owns starting the session so
                // there is a single place that launches the Pi process.
                startForegroundCompat(provisioning = false)
                acquireWakeLock()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun container() = (application as HarnessApp).container

    private fun startForegroundCompat(provisioning: Boolean = false) {
        val notification = buildNotification(provisioning)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, android.app.Service.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(provisioning: Boolean = false): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (provisioning) "Preparando o ambiente" else "Harness ativo"
        val text = if (provisioning) "Instalando ferramentas…" else "O agente está pronto para continuar."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Parar", stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agente em execução",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantém o ambiente de codificação ativo em segundo plano"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "harness:agent").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "harness_agent"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "dev.harness.action.STOP"
        const val ACTION_PROVISION = "dev.harness.action.PROVISION"

        fun start(context: Context) {
            startWithAction(context, null)
        }

        /** Keeps the process alive during environment provisioning. */
        fun startProvisioning(context: Context) {
            startWithAction(context, ACTION_PROVISION)
        }

        private fun startWithAction(context: Context, action: String?) {
            val intent = Intent(context, AgentService::class.java).apply {
                if (action != null) setAction(action)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AgentService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
