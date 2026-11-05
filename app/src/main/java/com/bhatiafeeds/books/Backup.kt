package com.bhatiafeeds.books

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.bhatiafeeds.books.data.AppDatabase
import java.io.File
import java.time.LocalDate

/**
 * Backup = a copy of the whole database file. The user picks where to save it with the system
 * file picker (Google Drive appears there), so no Google sign-in or API keys are needed.
 */
object Backup {
    fun fileName(): String = "BhatiaFeedsBooks-${LocalDate.now()}.db"

    /** Writes the database to [uri]. Call off the main thread. Returns true on success. */
    fun write(ctx: Context, uri: Uri): Boolean = try {
        // Flush the write-ahead log so the single .db file holds everything.
        AppDatabase.get(ctx).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val db = ctx.getDatabasePath(AppDatabase.FILE_NAME)
        val out = ctx.contentResolver.openOutputStream(uri, "w") ?: error("Cannot open destination")
        out.use { o -> db.inputStream().use { it.copyTo(o) } }
        true
    } catch (e: Exception) {
        false
    }

    /** Replaces the live database with the file at [uri]. Returns an error message, or null on success. */
    fun restore(ctx: Context, uri: Uri): String? {
        val temp = File(ctx.cacheDir, "restore_check.db")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return "Could not read that file."
            input.use { i -> temp.outputStream().use { i.copyTo(it) } }

            val header = ByteArray(16)
            temp.inputStream().use { it.read(header) }
            if (String(header, Charsets.US_ASCII).trimEnd('\u0000') != "SQLite format 3") {
                return "That file is not a Bhatia Feeds Books backup."
            }
            val check = SQLiteDatabase.openDatabase(temp.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val tables = mutableSetOf<String>()
                check.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                    while (c.moveToNext()) tables.add(c.getString(0))
                }
                if (!tables.containsAll(listOf("customers", "products", "sales", "payments"))) {
                    return "That file is not a Bhatia Feeds Books backup."
                }
                if (check.version > AppDatabase.VERSION) {
                    return "This backup was made by a newer version of the app. Please update the app first."
                }
            } finally {
                check.close()
            }

            AppDatabase.close()
            val live = ctx.getDatabasePath(AppDatabase.FILE_NAME)
            File(live.path + "-wal").delete()
            File(live.path + "-shm").delete()
            temp.copyTo(live, overwrite = true)
            return null
        } catch (e: Exception) {
            return "Restore failed: ${e.message ?: "unknown error"}"
        } finally {
            temp.delete()
        }
    }

    /** Relaunches the app so it opens the restored database from scratch. */
    fun restartApp(ctx: Context) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }
}
