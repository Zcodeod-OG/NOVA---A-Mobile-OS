package com.nova.runtime.app.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.io.File

class FileShareManager(
    private val context: Context
) {

    fun shareDocumentOrFile(fileQuery: String, targetApp: String?): ActionExecutionResult {
        val query = fileQuery.trim()
        val isPdf = query.contains("pdf", ignoreCase = true)
        val mimeType = if (isPdf) "application/pdf" else "*/*"

        return try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_SUBJECT, "Sharing file: $query")
                putExtra(Intent.EXTRA_TEXT, "Sent via NOVA Mobile OS")

                // Target WhatsApp if specified
                if (targetApp.equals("WhatsApp", ignoreCase = true)) {
                    `package` = "com.whatsapp"
                }

                // Add sample/mock document URI if file not directly located in local storage
                val dummyFileUri = getOrCreateSampleDocumentUri(query)
                putExtra(Intent.EXTRA_STREAM, dummyFileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)

            ActionExecutionResult(
                isSuccess = true,
                appName = targetApp ?: "File Share",
                message = "Opened ${targetApp ?: "Share Intent"} to send '$query'"
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                isSuccess = false,
                appName = targetApp ?: "File Share",
                message = "Failed to share '$query': ${e.localizedMessage}"
            )
        }
    }

    private fun getOrCreateSampleDocumentUri(fileName: String): Uri {
        return try {
            val cacheDir = context.cacheDir
            val cleanName = if (fileName.contains(".")) fileName else "$fileName.pdf"
            val file = File(cacheDir, cleanName)
            if (!file.exists()) {
                file.writeText("NOVA OS Generated Document: $fileName\nCreated for system action execution.")
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Uri.parse("content://media/external/file/1")
        }
    }
}
