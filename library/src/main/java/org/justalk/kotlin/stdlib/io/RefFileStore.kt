package org.justalk.kotlin.stdlib.io

import android.content.ContentValues
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.Looper
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.UUID

/**
 * 独占 root，持久化保存不可变文件及 owner 引用；最后一个引用释放后删除文件，支持孤儿文件对账。
 * 所有同步方法必须在后台线程调用。正式文件只能读取，不得由调用方修改或删除。
 * 关闭前，调用方必须停止并等待所有库调用及临时文件写入结束；不支持并发关闭与使用。
 * 提交结果不确定时保留文件；下次调用先恢复内部数据库连接，以实际状态为准，不重放失败操作。
 */
class RefFileStore private constructor(
    private val root: File,
    private val tmp: File,
    private val helper: StoreDatabase,
    private var database: SQLiteDatabase,
    private val lockFile: RandomAccessFile,
    private val fileLock: FileLock
) : Closeable {
    private val files = File(root, "files")
    // 沿用实例级锁串行化操作，覆盖引用事务及提交后的文件删除，防止误删同 key 的新文件。
    private val monitor = Any()
    private val tempFiles = mutableSetOf<File>()
    private var closed = false
    private var needsRecovery = false

    companion object {
        private val openRoots = mutableSetOf<File>()

        /** 打开调用方指定的独占目录，并清理上次遗留的临时文件。初始化失败会释放数据库和锁。 */
        @JvmStatic
        @Throws(IOException::class, SQLiteException::class)
        fun open(root: File): RefFileStore {
            checkBackgroundThread()
            val directory = root.canonicalFile
            ensureDirectory(directory)
            val files = File(directory, "files")
            val tmp = File(directory, "tmp")
            // 同进程先拒绝重复打开，避免关闭第二个 channel 时释放首实例的系统锁。
            synchronized(openRoots) {
                check(openRoots.add(directory)) { "Store is already open: $directory" }
            }
            var lockFile: RandomAccessFile? = null
            var fileLock: FileLock? = null
            var helper: StoreDatabase? = null
            try {
                lockFile = RandomAccessFile(File(directory, ".lock"), "rw")
                fileLock = try {
                    lockFile.channel.tryLock()
                } catch (e: OverlappingFileLockException) {
                    throw IllegalStateException("Store is already open: $directory", e)
                } ?: throw IllegalStateException("Store is already open: $directory")
                ensureDirectory(files)
                ensureDirectory(tmp)
                helper = StoreDatabase(File(directory, "store.db"))
                val database = helper.writableDatabase
                val leftovers = tmp.listFiles() ?: throw IOException("Cannot list $tmp")
                for (file in leftovers) {
                    if (!file.delete()) throw IOException("Cannot delete temporary file: $file")
                }
                return RefFileStore(directory, tmp, helper, database, lockFile, fileLock)
            } catch (failure: Throwable) {
                try { helper?.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                try { fileLock?.release() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                try { lockFile?.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                synchronized(openRoots) { openRoots.remove(directory) }
                throw failure
            }
        }

        private fun checkBackgroundThread() {
            check(Looper.myLooper() != Looper.getMainLooper()) { "RefFileStore requires a background thread" }
        }

        private fun ensureDirectory(directory: File) {
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Cannot create directory: $directory")
            }
        }

        private fun validateKey(key: String) {
            require(key.length in 1..128 && key.all {
                it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-'
            }) { "key must contain 1–128 ASCII letters, digits, underscores or hyphens" }
        }
    }

    /** 返回 Entry 和正式文件均存在的路径；不新增引用，也不提供读取保活。 */
    @Throws(SQLiteException::class)
    fun get(key: String): File? {
        checkBackgroundThread()
        return synchronized(monitor) {
            checkUsable()
            validateKey(key)
            File(files, key).takeIf { hasEntry(key) && it.isFile }
        }
    }

    /**
     * 按 Entry 聚合已登记占用，不扫描目录；共享引用只计一次，大小为首次提交时的快照。
     * 空库返回空 Map，无剩余 Entry 的 tag 不返回；不含临时文件及无 Entry 的孤儿文件。
     */
    @Throws(SQLiteException::class)
    fun usageByTag(): Map<String, Usage> {
        checkBackgroundThread()
        return synchronized(monitor) {
            checkUsable()
            database.rawQuery("SELECT tag, COUNT(*), SUM(size) FROM entry GROUP BY tag", null).use { cursor ->
                val usage = mutableMapOf<String, Usage>()
                while (cursor.moveToNext()) {
                    usage[cursor.getString(0)] = Usage(cursor.getInt(1), cursor.getLong(2))
                }
                usage
            }
        }
    }

    /** 创建本实例认可的临时文件。写入失败或取消时，调用方停止写入后自行清理；内部数据库恢复失败仍抛错。 */
    @Throws(IOException::class)
    fun createTempFile(): File {
        checkBackgroundThread()
        return synchronized(monitor) {
            checkUsable()
            var file: File
            do {
                file = File(tmp, UUID.randomUUID().toString())
            } while (!file.createNewFile())
            tempFiles.add(file)
            file
        }
    }

    /**
     * sourceFile 必须由本实例创建，内容完整且写入流已关闭；只改名，不复制。
     * 同 key 保留首次内容和 tag，仅确保 ownerId 引用存在（按原值精确匹配）。
     * 无 Entry 时，以本次完整临时文件直接改名替换同 key 孤儿，不复用孤儿内容。
     * 改名失败抛 IOException，回滚成功时源临时文件仍在；改名后确认回滚则抛原数据库异常，
     * 不恢复源路径，正式文件成为孤儿。提交结果不确定时保留文件，下次调用先恢复数据库连接。
     * 已有 key 的引用提交后，临时文件删除失败仍返回已有正式文件。
     */
    @JvmOverloads
    @Throws(IOException::class, SQLiteException::class)
    fun put(key: String, sourceFile: File, ownerId: String, tag: String = ""): File {
        checkBackgroundThread()
        return synchronized(monitor) {
            checkUsable()
            validateKey(key)
            require(ownerId.isNotBlank()) { "ownerId must not be blank" }
            val source = sourceFile.absoluteFile
            require(source in tempFiles && source.isFile && source.canonicalFile == source) {
                "sourceFile must be a temporary file created by this store"
            }
            val destination = File(files, key)
            var existing = false
            var failure: Throwable? = null
            database.beginTransaction()
            try {
                existing = hasEntry(key)
                if (!existing) {
                    database.insertOrThrow("entry", null, ContentValues().apply {
                        put("key", key)
                        put("size", source.length())
                        put("tag", tag)
                    })
                }
                database.execSQL("INSERT OR IGNORE INTO ref (key, owner) VALUES (?, ?)", arrayOf(key, ownerId))
                if (!existing) {
                    // monitor 覆盖无 Entry 的确认、孤儿替换及提交，替换只做这一次改名。
                    val renamed = try {
                        source.renameTo(destination)
                    } catch (e: SecurityException) {
                        throw IOException("Cannot rename $source to $destination", e)
                    }
                    if (!renamed) throw IOException("Cannot rename $source to $destination")
                    tempFiles.remove(source)
                }
                database.setTransactionSuccessful()
            } catch (e: Throwable) {
                failure = e
                throw e
            } finally {
                endTransaction(failure)
            }
            if (existing) {
                try {
                    source.delete()
                } catch (_: SecurityException) {
                    // 引用已经提交，临时文件留给下次 open 清理。
                }
                tempFiles.remove(source)
            }
            destination
        }
    }

    /** 确保已提交 key 的 ownerId 引用存在；重复登记返回 true，key 不存在返回 false。 */
    @Throws(SQLiteException::class)
    fun addRef(key: String, ownerId: String): Boolean {
        checkBackgroundThread()
        return synchronized(monitor) {
            checkUsable()
            validateKey(key)
            require(ownerId.isNotBlank()) { "ownerId must not be blank" }
            if (!hasEntry(key)) return@synchronized false
            var failure: Throwable? = null
            database.beginTransaction()
            try {
                database.execSQL("INSERT OR IGNORE INTO ref (key, owner) VALUES (?, ?)", arrayOf(key, ownerId))
                database.setTransactionSuccessful()
            } catch (e: Throwable) {
                failure = e
                throw e
            } finally {
                endTransaction(failure)
            }
            true
        }
    }

    /**
     * 幂等释放指定引用；最后一个引用与 Entry 在同一事务删除，提交后再尝试删除文件。
     * 磁盘删除失败不报错，残留为孤儿；调用方负责避免已删除 owner 再次提交引用。
     */
    @Throws(SQLiteException::class)
    fun removeRef(key: String, ownerId: String) {
        checkBackgroundThread()
        synchronized(monitor) {
            checkUsable()
            validateKey(key)
            require(ownerId.isNotBlank()) { "ownerId must not be blank" }
            var removeFile = false
            var failure: Throwable? = null
            database.beginTransaction()
            try {
                val removed = database.delete("ref", "key = ? AND owner = ?", arrayOf(key, ownerId))
                if (removed > 0) {
                    val hasRefs = database.rawQuery(
                        "SELECT 1 FROM ref WHERE key = ? LIMIT 1", arrayOf(key)
                    ).use { it.moveToFirst() }
                    if (!hasRefs) {
                        removeFile = database.delete("entry", "key = ?", arrayOf(key)) > 0
                    }
                }
                database.setTransactionSuccessful()
            } catch (e: Throwable) {
                failure = e
                throw e
            } finally {
                endTransaction(failure)
            }
            if (removeFile) {
                try {
                    File(files, key).delete()
                } catch (_: SecurityException) {
                    // 数据库释放已提交，正式文件留作孤儿。
                }
            }
        }
    }

    /**
     * 先扫描正式文件，再读取 Entry；疑似孤儿在 monitor 内复查并删除，与同 key 的操作互斥。
     * 仅清理无 Entry 的正式文件，不清理 tmp、悬空 Entry 或残留 ref。
     * 单个文件删除失败计数后继续；目录或数据库查询失败直接抛出，不返回部分结果。
     */
    @Throws(IOException::class, SQLiteException::class)
    fun reconcile(): ReconcileResult {
        checkBackgroundThread()
        synchronized(monitor) { checkUsable() }
        val candidates = try {
            files.listFiles() ?: throw IOException("Cannot list $files")
        } catch (e: SecurityException) {
            throw IOException("Cannot list $files", e)
        }
        val entries = synchronized(monitor) {
            checkUsable()
            database.rawQuery("SELECT key FROM entry", null).use { cursor ->
                val keys = mutableSetOf<String>()
                while (cursor.moveToNext()) keys.add(cursor.getString(0))
                keys
            }
        }
        var deleted = 0
        var failed = 0
        for (file in candidates) {
            if (file.name in entries) continue
            synchronized(monitor) {
                checkUsable()
                // 与 put 的改名和事务提交共用锁，不能把在途文件当成孤儿。
                if (!hasEntry(file.name)) {
                    val removed = try {
                        file.delete()
                    } catch (_: SecurityException) {
                        false
                    }
                    if (removed) deleted++ else failed++
                }
            }
        }
        return ReconcileResult(deleted, failed)
    }

    /** 仅关闭数据库并释放锁，不清理 tmp；可重复关闭，但仍禁止主线程调用。 */
    @Throws(IOException::class, SQLiteException::class)
    override fun close() {
        checkBackgroundThread()
        synchronized(monitor) {
            if (closed) return@synchronized
            closed = true
            try {
                helper.close()
            } finally {
                try {
                    fileLock.release()
                } finally {
                    try {
                        lockFile.close()
                    } finally {
                        synchronized(openRoots) { openRoots.remove(root) }
                    }
                }
            }
        }
    }

    private fun checkUsable() {
        check(!closed) { "Store is closed" }
        if (needsRecovery || !database.isOpen) {
            needsRecovery = true
            // 仅重建数据库连接，保留目录独占锁、在途临时文件及其登记。
            helper.close()
            database = helper.writableDatabase
            needsRecovery = false
        }
    }

    private fun endTransaction(operationFailure: Throwable?) {
        try {
            database.endTransaction()
        } catch (failure: SQLiteException) {
            // 不重试或删除文件：事务结束失败时无法确认最终提交状态。
            needsRecovery = true
            if (operationFailure is SQLiteException) {
                if (operationFailure !== failure) operationFailure.addSuppressed(failure)
                throw operationFailure
            }
            if (operationFailure != null) failure.addSuppressed(operationFailure)
            throw failure
        }
    }

    private fun hasEntry(key: String): Boolean = database.rawQuery(
        "SELECT 1 FROM entry WHERE key = ?", arrayOf(key)
    ).use { it.moveToFirst() }

    private class StoreDatabase(path: File) : SQLiteOpenHelper(
        object : ContextWrapper(null) {
            override fun getDatabasePath(name: String): File = path

            // Android 23–26 的 SQLiteOpenHelper 通过 Context 打开数据库。
            override fun openOrCreateDatabase(
                name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?
            ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(path.path, factory, errorHandler)
        }, path.path, null, 1
    ) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE entry (key TEXT PRIMARY KEY, size INTEGER NOT NULL, tag TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE ref (key TEXT NOT NULL REFERENCES entry(key) ON DELETE CASCADE, owner TEXT NOT NULL, PRIMARY KEY (key, owner))")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("Unsupported schema upgrade: $oldVersion to $newVersion")
        }
    }
}

data class Usage(val fileCount: Int, val totalBytes: Long)

data class ReconcileResult(
    val orphanFilesDeleted: Int,
    val orphanFilesDeleteFailed: Int
)
