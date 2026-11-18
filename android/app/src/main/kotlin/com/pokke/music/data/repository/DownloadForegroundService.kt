package com.pokke.music.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** Keeps the queue's workers alive after the user leaves Pokke and reports their progress. */
class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: DownloadQueueManager
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        engine = DownloadQueueManager.get(applicationContext)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Descargas de Pokke", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            engine.tasks.value.values.filter { it.status == DownloadQueueManager.Status.QUEUED || it.status == DownloadQueueManager.Status.RUNNING || it.status == DownloadQueueManager.Status.PAUSED }
                .forEach { engine.cancel(it.id) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        promote()
        if (observer == null) observer = scope.launch {
            engine.tasks.collect { tasks ->
                val active = tasks.values.filter {
                    it.status == DownloadQueueManager.Status.QUEUED || it.status == DownloadQueueManager.Status.RUNNING
                }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else updateNotification(active)
            }
        }
        return START_STICKY
    }

    private fun promote() {
        val notification = notification("Preparando descargas", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(active: List<DownloadQueueManager.Task>) {
        val running = active.filter { it.status == DownloadQueueManager.Status.RUNNING }
        val waiting = active.count { it.status == DownloadQueueManager.Status.QUEUED }
        val measured = running.filter { it.totalBytes > 0 }
        val progress = if (measured.isNotEmpty()) (measured.map { it.progress }.average() * 100).toInt() else 0
        val title = when {
            running.size > 1 -> "Descargando ${running.size} canciones"
            running.isNotEmpty() -> running.first().song.title
            else -> "Descargas en cola"
        }
        val text = when {
            waiting > 0 -> "$waiting canciones pendientes"
            running.isNotEmpty() -> running.first().song.artist
            else -> "Procesando descargas"
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification(title, progress, measured.isEmpty(), text))
    }

    private fun notification(title: String, progress: Int, indeterminate: Boolean, text: String = "Pokke") =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                0,
                "Cancelar todo",
                PendingIntent.getService(
                    this,
                    2,
                    Intent(this, DownloadForegroundService::class.java).setAction(ACTION_CANCEL_ALL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pokke_downloads"
        const val NOTIFICATION_ID = 7402
        const val ACTION_CANCEL_ALL = "com.pokke.music.action.CANCEL_DOWNLOADS"
    }
}
