package com.bhatiafeeds.books

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bhatiafeeds.books.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Once a day at about 10 AM: tells you how many customers have old unpaid dues. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.remindersOn) return Result.success()

        val customers = AppDatabase.get(applicationContext).customers().balances().first()
        val today = LocalDate.now().toEpochDay()
        val old = customers.filter { c ->
            val last = c.lastActivity ?: 0L
            c.balance > 0.5 && (last <= 0L || today - last >= prefs.reminderDays)
        }
        if (old.isEmpty()) return Result.success()

        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Payment reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                applicationContext, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val total = old.sumOf { it.balance }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Payment reminder")
            .setContentText("${old.size} customer${if (old.size == 1) "" else "s"} owe ${Fmt.money(total)} with no activity for ${prefs.reminderDays}+ days")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1, n)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "dues"

        fun schedule(ctx: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(10, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("dues-reminder", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
