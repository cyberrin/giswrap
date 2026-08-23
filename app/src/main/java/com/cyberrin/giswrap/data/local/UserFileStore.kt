package com.cyberrin.giswrap.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun persistRead(uri: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
