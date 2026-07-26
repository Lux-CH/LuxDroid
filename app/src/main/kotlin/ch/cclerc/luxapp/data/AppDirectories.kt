package ch.cclerc.luxapp.data

import android.content.Context
import java.io.File

object AppDirectories {
    private lateinit var filesDir: File

    fun init(context: Context) {
        filesDir = context.filesDir
    }

    fun base(): File = File(filesDir, "Lux").apply { mkdirs() }

    fun dir(name: String): File = File(base(), name).apply { mkdirs() }
}
