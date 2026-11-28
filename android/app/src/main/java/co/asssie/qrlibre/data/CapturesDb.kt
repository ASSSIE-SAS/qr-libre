package co.asssie.qrlibre.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CapturesDb private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE captures(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                `package` TEXT NOT NULL,
                app TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                epoch_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_captures_epoch_ms ON captures(epoch_ms DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema. A future migration belongs here.
    }

    /** Returns the new row id, or -1 when the insert failed. */
    fun insertCapture(
        packageName: String,
        app: String,
        title: String,
        body: String,
        epochMs: Long,
    ): Long {
        val values = ContentValues().apply {
            put("`package`", packageName)
            put("app", app)
            put("title", title)
            put("body", body)
            put("epoch_ms", epochMs)
        }
        return writableDatabase.insert("captures", null, values)
    }

    /** Newest first. The history screen keeps a bounded list. */
    fun recentCaptures(limit: Int = DEFAULT_LIMIT): List<Capture> {
        val captures = mutableListOf<Capture>()
        readableDatabase.query(
            "captures",
            arrayOf("id", "`package`", "app", "title", "body", "epoch_ms"),
            null,
            null,
            null,
            null,
            "epoch_ms DESC, id DESC",
            limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                captures += Capture(
                    id = cursor.getLong(0),
                    packageName = cursor.getString(1),
                    app = cursor.getString(2),
                    title = cursor.getString(3),
                    body = cursor.getString(4),
                    epochMs = cursor.getLong(5),
                )
            }
        }
        return captures
    }

    /**
     * Counts every row in [startMsInclusive, endMsExclusive), independent of
     * the newest-500 display limit. Returns [Long], so a large table cannot
     * overflow through an Int cast.
     */
    fun countCapturesBetween(startMsInclusive: Long, endMsExclusive: Long): Long =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM captures WHERE epoch_ms >= ? AND epoch_ms < ?",
            arrayOf(startMsInclusive.toString(), endMsExclusive.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    fun clear() {
        writableDatabase.delete("captures", null, null)
    }

    companion object {
        private const val DB_NAME = "qr_libre.db"
        private const val DB_VERSION = 1
        private const val DEFAULT_LIMIT = 500

        @Volatile
        private var instance: CapturesDb? = null

        fun get(context: Context): CapturesDb =
            instance ?: synchronized(this) {
                instance ?: CapturesDb(context.applicationContext).also { instance = it }
            }
    }
}
