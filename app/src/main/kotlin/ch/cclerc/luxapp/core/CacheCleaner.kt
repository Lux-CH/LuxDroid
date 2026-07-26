package ch.cclerc.luxapp.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CacheCleaner {
    suspend fun performCleanup(context: Context) {
        withContext(Dispatchers.IO) {
            cleanDirectoryContents(context.cacheDir)
            context.externalCacheDir?.let { cleanDirectoryContents(it) }
        }
    }

    private fun cleanDirectoryContents(directory: java.io.File) {
        if (!directory.exists()) return
        directory.listFiles()?.forEach { item ->
            runCatching { item.deleteRecursively() }
        }
    }
}
