package com.wakeupbuddy.photo

import android.content.Context
import java.io.File

/** Proof photos live in app-private internal storage. Never leaves the device. */
object PhotoStore {
    const val DIR = "proof_photos"
    const val MAX_AGE_MS = 48L * 60L * 60L * 1000L // 48 hours

    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun newPhotoFile(ctx: Context): File =
        File(dir(ctx), "proof_${System.currentTimeMillis()}.jpg")

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".jpg") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Delete photos older than 48h. Returns number deleted. */
    fun deleteExpired(ctx: Context): Int {
        val now = System.currentTimeMillis()
        var count = 0
        dir(ctx).listFiles()?.forEach { f ->
            if (f.isFile && now - f.lastModified() > MAX_AGE_MS && f.delete()) count++
        }
        return count
    }

    /** Manually delete one photo. */
    fun delete(file: File): Boolean = try {
        file.delete()
    } catch (e: Exception) {
        false
    }

    /** Manually delete every proof photo. Returns number deleted. */
    fun deleteAll(ctx: Context): Int {
        var count = 0
        dir(ctx).listFiles()?.forEach { if (it.isFile && it.delete()) count++ }
        return count
    }

    fun expiresInMs(file: File): Long =
        (MAX_AGE_MS - (System.currentTimeMillis() - file.lastModified())).coerceAtLeast(0)
}
