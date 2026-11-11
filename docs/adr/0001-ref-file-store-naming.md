---
status: accepted
---

# 保留正式文件的原始相对路径或源文件后缀

第三方文件工具会读取实际文件名，因此仅在消息中保存展示名称不能满足打开文件的需求；正式文件按调用方提供的原始相对路径命名，未提供时保留源文件后缀。不同原始路径使用不同 key，由调用方决定文件身份，库不增加按消息区分名称或打开前复制文件的机制。

## 接口约定

在现有 `put` 的 `tag` 参数之后增加可选参数 `relativePath: String? = null`，保留现有 Kotlin、Java 调用方式。为 `createTempFile` 增加可选的扩展名参数，使业务能够创建如 `<UUID>.jpg` 的临时文件；无参数调用仍创建无后缀 UUID 文件，`put` 仍只接受当前实例创建并登记的临时文件。

```kotlin
val imageTemp = store.createTempFile("jpg")
val image = store.put(imageKey, imageTemp, ownerId, "image")

val attachmentTemp = store.createTempFile()
val attachment = store.put(
    attachmentKey,
    attachmentTemp,
    ownerId,
    tag = "attachment",
    relativePath = "项目资料/报价单.xlsx"
)
```

临时文件内容应在提交前写入完整并关闭所有流，以上示例省略写入过程。

## 正式文件命名

| 首次提交条件 | 正式路径 | 后缀来源 |
| --- | --- | --- |
| 提供 `relativePath` | `root/files/<key>/<relativePath>` | 使用相对路径自身的文件名及后缀，不追加源文件后缀 |
| 未提供 `relativePath`，源文件带后缀 | `root/files/<key>.<extension>` | 从 `sourceFile.extension` 提取 |
| 未提供 `relativePath`，源文件无后缀 | `root/files/<key>` | 不添加后缀 |

`relativePath` 优先于源文件后缀。例如相对路径为 `资料/报价单.xlsx`，即使临时文件无后缀，正式名称仍为 `报价单.xlsx`。相对路径不带后缀时也不自动追加源文件后缀；库不检测内容格式，也不保证声明的后缀与实际内容一致。

相对路径必须包含有效文件名，不得使用绝对路径或越出当前 key 目录的路径。扩展名参数只能表达后缀，不能用于传入目录；实现时应校验路径边界。

## 引用和路径稳定性

同 key 保留首次成功提交的内容、tag、路径和后缀。后续 `put` 即使传入不同路径或后缀，也只确保 owner 引用存在并返回已有文件，沿用当前同 key 保留首次值的语义。

`get(key)` 返回正式文件的实际路径。路径需要持久化，重新打开后仍按首次提交的路径读取；引用添加、最后引用释放、占用统计和孤儿对账均需适配新的文件布局。原始相对目录位于 key 目录下，第三方直接读取文件名时获得原始名称，无需打开前创建命名副本。

同一内容需要以不同原始路径保留时，调用方传入不同 key，允许保存多份；库不额外维护每条消息的名称。用户确认没有旧版存储数据，无需增加旧数据迁移逻辑；现有不传新参数且源文件无后缀的调用仍保持原行为。
