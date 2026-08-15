package com.nova.runtime.storage.search

/** Tracks on-device body extraction lifecycle for indexed documents. */
object ContentExtractStatus {
    const val NOT_TRIED = "NOT_TRIED"
    const val SUCCESS = "SUCCESS"
    const val EMPTY = "EMPTY"
    const val FAILED = "FAILED"

    fun resolve(content: String?, extractionFailed: Boolean): String =
        when {
            extractionFailed -> FAILED
            content.isNullOrBlank() -> EMPTY
            else -> SUCCESS
        }

    fun needsBackgroundExtraction(status: String): Boolean =
        status == NOT_TRIED || status == FAILED || status == EMPTY

    fun needsForceExtraction(status: String, contentText: String?): Boolean =
        status != SUCCESS ||
            contentText.isNullOrBlank() ||
            (contentText.contains('<') && contentText.contains("</"))

    /** Compact debug label: "123 chars extracted" / "0 chars extracted" / "extract failed". */
    fun debugLabel(status: String?, contentCharCount: Int): String? =
        when (status) {
            SUCCESS -> "$contentCharCount chars extracted"
            EMPTY -> "0 chars extracted"
            FAILED -> "extract failed — will retry"
            NOT_TRIED -> if (contentCharCount > 0) {
                "$contentCharCount chars extracted"
            } else {
                "extract pending"
            }
            else -> if (contentCharCount > 0) "$contentCharCount chars extracted" else null
        }
}
