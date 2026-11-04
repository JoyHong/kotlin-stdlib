# RefFileStore 接入说明

RefFileStore 随现有 `library` 模块分发，仅使用 Android/JDK，不新增依赖。包名为 `org.justalk.kotlin.stdlib.io`。
契约依据：[RefFileStore v1 技术规格](https://pb9n6xz6e3.feishu.cn/docx/KJDmdF5BqoC34mxx8PMci1MunSh) revision 208；按后续确认集成到现有模块。

## 打开、线程与关闭

所有公开同步方法（包括 `open`、`usageByTag` 和 `close`）都必须在业务自己的后台线程调用，主线程调用抛 `IllegalStateException`。库不创建线程，也不依赖协程。

```kotlin
// 在后台线程打开，应用内共享该实例；root 必须是库独占的新目录。
val store = RefFileStore.open(File(context.filesDir, "ref_media"))
```

只支持单进程，同一 root 只能打开一个实例，重复打开抛 `IllegalStateException`。不要复用已有缓存目录，也不要放在 `cacheDir`；库不承诺系统或外部删改后的自动恢复。正式文件只能读取，不得由业务修改或删除。

关闭前必须先停止并等待所有库调用、下载及临时文件写入结束，再在后台线程调用 `store.close()`。v1 不支持并发关闭与使用。`close` 可重复调用，关闭后其他实例方法抛 `IllegalStateException`；关闭不清理 tmp，下次 `open` 清理遗留临时文件。

## key 与临时文件提交

key 是不可变内容标识，长度 1–128，仅允许 ASCII `[A-Za-z0-9_-]`，不能直接用 URL 或带扩展名的文件名。URL 按 UTF-8 编码计算 MD5，再输出 32 字符小写十六进制，可作为合法 key：

```kotlin
fun keyOf(url: String): String =
    MessageDigest.getInstance("MD5").digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
```

MD5 在此仅用于 URL 映射；同 key 不比较或覆盖内容，内容变化必须换 key。ownerId 必须非空白，按原值精确匹配，不 trim、不转换大小写。

```kotlin
// 整段在后台执行；业务已协调 owner 生命周期，downloadTo 由业务实现。
val key = keyOf(url)
val tmp = store.createTempFile()
var cleanupTemp = true
try {
    downloadTo(url, tmp) // 返回前必须完成写入并关闭所有流。
    val file = store.put(key, tmp, ownerId, "image")
    // file 是只读的正式路径，不得删除。
} catch (e: SQLiteException) {
    cleanupTemp = false // 保留现场；异常向上传递，交由业务处理实例恢复。
    throw e
} finally {
    if (cleanupTemp) tmp.delete() // 只清理原临时路径；写入必须已经停止。
}
```

示例使用 `java.io.File`、`java.security.MessageDigest`、`android.database.sqlite.SQLiteException` 和 `org.justalk.kotlin.stdlib.io.RefFileStore`。下载由业务负责，库不做下载或进行中任务去重。

`put` 只接收当前实例 `createTempFile()` 创建、写完整且流已关闭的文件；直接改名，不复制。首次成功提交确定内容、大小快照和 tag（默认 `""`）。后续同 key 的 `put` 只确保 owner 引用存在，即使传入不同内容或 tag，也不会覆盖首次值；引用提交后尝试删除本次临时文件，删除失败仍返回已有正式文件。失败后原临时路径可能已不存在，重试前可能需要重新准备内容。临时文件漏删或删除失败由下次 `open` 清理。

## owner 生命周期与读取

已登记 key 可用 `store.addRef(key, ownerId)` 共享，无需再写文件。成功或重复登记均返回 `true`，key 不存在返回 `false`；同一 `(key, ownerId)` 重复 `put` / `addRef` 只算一次引用。

`store.get(key)` 只有 Entry 和正式文件都存在时才返回路径，否则返回 `null`。它不增加引用，也不提供读取保活：拿到路径后仍可能被最后一次释放删除。业务应维持有效 owner 引用，并协调读取与释放。

删除 owner 前，业务必须停止并协调其下载与提交，保留其关联的全部 key，然后逐个调用 `store.removeRef(key, ownerId)`。仅在提交前查询一次 owner 是否存在仍有竞争窗口，必须防止删除后再 `put` / `addRef`。释放幂等；释放一个共享 owner 不删文件，最后一个引用与 Entry 在同一事务删除，提交后再删磁盘文件。磁盘删除失败不让释放报错，残留为孤儿。

每次释放是独立事务，批量释放不保证原子性。v1 不要求持久化释放补偿；业务数据删除后、释放完成前进程被杀，可能残留引用并长期占用空间。库不知道业务 owner 是否已删除，`reconcile()` 不能清理这类多余引用。

## 按 tag 查询占用

```kotlin
// 后台同步查询，业务再将结果交给 UI。
val usage = store.usageByTag() // Map<String, Usage>
val imageCount = usage["image"]?.fileCount ?: 0
val imageBytes = usage["image"]?.totalBytes ?: 0L
```

`Usage(fileCount: Int, totalBytes: Long)` 按数据库 Entry 聚合，不扫描目录。多个 owner 和重复登记不增加文件数或字节数。大小始终使用首次提交的快照；后续提交不能改变 tag。空库返回空 Map，默认分类的键是 `""`，没有 Entry 的分类不返回。

释放一个共享 owner 后占用不变；最后一个引用及 Entry 删除提交后统计同步减少，即使磁盘删除失败留下孤儿也不再计入。临时文件和孤儿文件都不计入，因此该结果表示已登记占用，不是目录实际磁盘占用；外部删改不会自动修正大小快照。

## 旧文件迁移

库不提供专用迁移接口。业务按对象取得 URL 和 ownerId，协调其生命周期后，在后台执行：

```kotlin
val key = keyOf(url)
if (!store.addRef(key, ownerId)) {
    val tmp = store.createTempFile()
    var cleanupTemp = true
    try {
        oldFile.copyTo(tmp, overwrite = true) // tmp 已存在；复制完成并关闭流后才能提交。
        store.put(key, tmp, ownerId, "image")
    } catch (e: SQLiteException) {
        cleanupTemp = false
        throw e
    } finally {
        if (cleanupTemp) tmp.delete()
    }
}
```

即使旧文件与 root 在同一文件系统，也不能直接把旧路径传给 `put`。迁移可分批重跑，重复登记幂等；旧文件保持原样，业务确认所有旧路径使用方都已迁移后再决定清理。

## 异常与恢复边界

| 情况 | 契约 |
| --- | --- |
| key、ownerId、sourceFile 违反契约 | `IllegalArgumentException` |
| 主线程调用、关闭或失效实例、重复打开 root | `IllegalStateException` |
| 文件 I/O 失败 | `IOException`；引用提交后删除文件失败按上述约定保留残留 |
| 数据库失败（包括统计查询） | 原生 `SQLiteException` 向上传递，不能当作空结果 |

改名成功后确认数据库回滚：原临时路径可能消失，正式文件成为孤儿。数据库提交结果无法确认时，库保留文件并停用实例，只允许 `close`。业务不得自动重试或继续使用该实例；先停止并等待在途调用和写入结束，再后台关闭重开，以实际数据库状态为准。

业务可安排后台 `reconcile()` 清理无 Entry 的正式文件；它不清理 tmp、悬空 Entry 或残留业务引用。单文件删除失败计入结果后继续；目录或数据库查询失败直接抛异常。v1 不承诺外部删改自动恢复，也未承诺或验证断电持久性。
