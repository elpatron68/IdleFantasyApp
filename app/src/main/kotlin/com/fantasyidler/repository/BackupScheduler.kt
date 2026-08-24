package com.fantasyidler.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.net.Uri
import android.util.Log
import com.fantasyidler.data.model.toExport
import com.fantasyidler.receiver.BackupAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(frequency: String) {
        cancel()
        if (frequency.isEmpty()) return
        // Build the PendingIntent with the frequency baked into the Intent extras so
        // BackupAlarmReceiver can reschedule the next occurrence after each firing.
        val intent = Intent(context, BackupAlarmReceiver::class.java)
            .putExtra(EXTRA_FREQUENCY, frequency)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val firstFire = if (frequency == "hourly") System.currentTimeMillis() + intervalMs(frequency)
                        else nextFiveAm(frequency)
        // setInexactRepeating only honours Android's own built-in interval constants
        // (INTERVAL_HOUR, INTERVAL_DAY, etc.). Passing 7*INTERVAL_DAY is silently
        // ignored and the alarm fires once then stops. Use setExactAndAllowWhileIdle
        // instead and reschedule manually inside performBackup after each firing.
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, firstFire, pi)
    }

    /** Reschedule the next alarm occurrence after a successful backup firing. */
    fun reschedule(frequency: String) {
        if (frequency.isEmpty()) return
        val intent = Intent(context, BackupAlarmReceiver::class.java)
            .putExtra(EXTRA_FREQUENCY, frequency)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextFire = System.currentTimeMillis() + intervalMs(frequency)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFire, pi)
    }

    private fun intervalMs(frequency: String): Long = when (frequency) {
        "hourly" -> AlarmManager.INTERVAL_HOUR
        "daily"  -> AlarmManager.INTERVAL_DAY
        "weekly" -> 7L * AlarmManager.INTERVAL_DAY
        else     -> AlarmManager.INTERVAL_DAY
    }

    fun cancel() {
        // FLAG_NO_CREATE returns null if no matching alarm is registered, so the
        // cancel() call is safely skipped when there is nothing to cancel.
        val intent = Intent(context, BackupAlarmReceiver::class.java)
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { alarmManager.cancel(it) }
    }

    suspend fun performBackup(playerRepo: PlayerRepository, frequency: String = ""): Boolean {
        val flags = playerRepo.getFlags()
        if (flags.backupFolderUri.isEmpty()) return false
        var tempUri: Uri? = null
        var oldDocsDeleted = false
        var failureMsg = ""
        val ok = try {
            val sessions = buildList {
                sessionRepo.getActiveSession()?.let { add(it.toExport()) }
                addAll(sessionRepo.getAllCompletedSessions().map { it.toExport() })
                for (slot in 1..2) {
                    sessionRepo.getActiveWorkerSession(slot)?.let { add(it.toExport()) }
                    addAll(sessionRepo.getAllCompletedWorkerSessions(slot).map { it.toExport() })
                }
            }
            val jsonBytes = playerRepo.exportSave(sessions).toByteArray()
            val treeUri   = Uri.parse(flags.backupFolderUri)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val cr        = context.contentResolver

            val created = DocumentsContract.createDocument(
                cr,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
                "application/json",
                TEMP_DISPLAY_NAME,
            ) ?: throw IllegalStateException("backup provider refused to create temp document")
            tempUri = created

            cr.openOutputStream(created, "w")?.use { it.write(jsonBytes) }
                ?: throw IllegalStateException("backup provider would not open output stream")

            val verified = cr.openInputStream(created)?.use { input ->
                val cap = ByteArray(jsonBytes.size + 1)
                var filled = 0
                while (filled < cap.size) {
                    val read = input.read(cap, filled, cap.size - filled)
                    if (read < 0) break
                    filled += read
                }
                cap.copyOf(filled)
            } ?: throw IllegalStateException("backup provider would not reopen temp document for verification")
            if (!verified.contentEquals(jsonBytes)) {
                throw IllegalStateException("temp document bytes differ from exported save")
            }

            val currentTempId = DocumentsContract.getDocumentId(created)
            val doomedIds = childDocuments(cr, treeUri, treeDocId)
                .filter { (docId, name) ->
                    docId != currentTempId &&
                        (name.startsWith(TEMP_DISPLAY_NAME) || name.startsWith(FINAL_DISPLAY_NAME))
                }
                .map { it.first }
            oldDocsDeleted = true
            doomedIds.forEach { docId ->
                DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
            }

            DocumentsContract.renameDocument(cr, created, FINAL_DISPLAY_NAME)
                ?: throw IllegalStateException("backup provider failed to swap temp document to final name")
            tempUri = null

            val swappedIn = childDocuments(cr, treeUri, treeDocId)
                .any { it.second.startsWith(FINAL_DISPLAY_NAME) && !it.second.startsWith(TEMP_DISPLAY_NAME) }
            if (!swappedIn) {
                throw IllegalStateException("renamed backup document not found after swap")
            }
            try {
                val effectiveFreq = frequency.ifEmpty { flags.backupFrequency }
                if (effectiveFreq.isNotEmpty()) reschedule(effectiveFreq)
            } catch (e: Exception) {
                Log.w(TAG, "Post-swap backup steps failed", e)
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Auto-backup failed", e)
            failureMsg = e.message ?: e.javaClass.simpleName
            false
        }

        if (!ok && !oldDocsDeleted) {
            tempUri?.let { temp ->
                try { DocumentsContract.deleteDocument(context.contentResolver, temp) } catch (_: Exception) {}
            }
        }

        try {
            playerRepo.updateFlagsAtomically { f ->
                f.copy(
                    lastBackupAt    = System.currentTimeMillis(),
                    lastBackupOk    = ok,
                    lastBackupError = failureMsg,
                )
            }
        } catch (_: Exception) {}

        return ok
    }

    private fun childDocuments(cr: android.content.ContentResolver, treeUri: Uri, treeDocId: String): List<Pair<String, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val docs = mutableListOf<Pair<String, String>>()
        cr.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val name  = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                docs += docId to name
            }
        }
        return docs
    }

    /**
     * Returns the next 5am for daily/weekly (tomorrow if 5am today has already passed),
     * or for weekly, the next Sunday 5am.
     */
    private fun nextFiveAm(frequency: String): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 5)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        // For weekly, advance to the next Sunday
        if (frequency == "weekly") {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis

    companion object {
        private const val TAG = "BackupScheduler"
        private const val TEMP_DISPLAY_NAME = "fantasyidler_auto.tmp"
        private const val FINAL_DISPLAY_NAME = "fantasyidler_auto"
        private const val REQUEST_CODE = 9001
        const val EXTRA_FREQUENCY = "backup_frequency"
    }
}
