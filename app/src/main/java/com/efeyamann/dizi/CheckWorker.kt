package com.efeyamann.dizi

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = Store(applicationContext)
        if (store.token.isBlank()) return@withContext Result.success()
        val today = LocalDate.now().toString()
        var failed = false
        val checked = store.shows.map { old ->
            try {
                val (msg, show) = checkNews(old, Tmdb.details(store.token, old.id), today)
                msg?.let { notify(show, it) }
                show
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failed = true
                old
            }
        }.associateBy { it.id }
        // Re-read so shows added or deleted in the app during this run are kept.
        store.shows = store.shows.map { checked[it.id] ?: it }
        if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }

    private fun notify(show: Show, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("seasons", "Yeni sezonlar", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            show.id,
            Notification.Builder(ctx, "seasons").setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(show.name).setContentText(text).setContentIntent(open).setAutoCancel(true).build(),
        )
    }
}
