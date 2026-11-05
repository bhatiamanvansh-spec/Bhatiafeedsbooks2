package com.bhatiafeeds.books

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Share {
    /** A file in the app's cache "shared" folder (cleaned up by Android when space is needed). */
    fun cacheFile(ctx: Context, name: String): File {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        return File(dir, name)
    }

    fun safeName(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "file" }

    fun file(ctx: Context, f: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    /** Sends a file straight to WhatsApp (or WhatsApp Business); falls back to the normal share sheet if neither is installed. */
    fun whatsapp(ctx: Context, f: File, mime: String, message: String = "") {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
                setPackage(pkg)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(send)
                return
            } catch (e: android.content.ActivityNotFoundException) {
                // try the next one
            }
        }
        file(ctx, f, mime)
    }

    fun text(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(send, "Send reminder").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
