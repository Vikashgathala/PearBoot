package xyz.pearos.pearboot.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore

val Context.downloadStore by preferencesDataStore("download_state")

object DownloadKeys {
    val VERSION = intPreferencesKey("version")
    val PATH = stringPreferencesKey("path")
    val BYTES = longPreferencesKey("bytes")
    val TOTAL = longPreferencesKey("total")
    val COMPLETED = booleanPreferencesKey("completed")

    // 🔥 ADD THIS
    val VERIFIED_SHA256 = stringPreferencesKey("verified_sha256")
}
