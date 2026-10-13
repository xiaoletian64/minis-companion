package site.zhangyue.miniscompanion

/**
 * Reader for OpenMinis official backup packages (BackupFormat, JSONL envelopes).
 *
 * WIP skeleton — the concrete envelope models follow the *documented* format
 * (see docs/DATA-FORMAT.md). This module intentionally contains no OpenMinis
 * source code; it only decodes the package layout that the app itself exports
 * for cross-platform migration.
 */
data class BackupPackageInfo(
    val categories: List<String>,
    val encrypted: Boolean,
    val generatedAt: Long?
)

interface BackupPackageReader {
    /** Peek manifest without decoding records. */
    fun peek(): BackupPackageInfo

    /** Stream decoded chat records as normalized session JSON lines. */
    fun streamChats(onRecord: (String) -> Unit)
}
