package net.interlecto.geoutils.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** The user-visible file name of a content URI, falling back to its last path segment. */
fun Context.displayName(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: uri.toString()
