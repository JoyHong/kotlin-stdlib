package org.justalk.kotlin.stdlib.concurrent

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * 全局协程作用域管理
 * 用于替代 GlobalScope，提供可控的、生命周期随 App 的协程环境
 */
object AppCoroutineScope {

    private const val TAG = "AppCoroutineScope"

    // 1. 定义一个变量来持有外部传入的处理逻辑
    // (Throwable) -> Unit 表示接收一个异常，没有返回值
    private val errorHandlerRef = AtomicReference<(Throwable) -> Unit>()

    /**
     * 提供一个初始化方法，供 App 层调用
     * 建议在 Application.onCreate 中调用
     */
    @JvmStatic
    fun setErrorHandler(handler: (Throwable) -> Unit) {
        if (!errorHandlerRef.compareAndSet(null, handler)) {
            Log.w(TAG, "Warning: Error handler already initialized!")
        }
    }

    // 全局异常处理器
    private val globalExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        Log.e(TAG, "Global Background Error: ${throwable.message}", throwable)
        errorHandlerRef.get()?.invoke(throwable)
    }

    // SupervisorJob：保证一个任务崩了，不会把整个 Scope 搞挂，其它模块还能跑
    private val appJob = SupervisorJob()

    /**
     * IO 作用域
     * 适用于：数据库读写、文件操作、网络请求
     * 特点：线程池调度，并发高
     */
    val ioScope = CoroutineScope(Dispatchers.IO + appJob + globalExceptionHandler)

    /**
     * 串行 IO 作用域
     * 基于 Dispatchers.IO.limitedParallelism(1)，复用 IO 线程池，限制并发为 1
     * 适用于：需要保证顺序执行的 IO 操作，例如顺序写入、串行网络请求
     */
    val ioSerialScope: CoroutineScope = CoroutineScope(
        Dispatchers.IO.limitedParallelism(1) + appJob + globalExceptionHandler
    )

    /**
     * 主线程作用域 (慎用，一般建议用 LifecycleScope/ViewModelScope)
     * 但在某些纯静态工具类需要回调 UI 时可能会用到
     */
    val mainScope = CoroutineScope(Dispatchers.Main.immediate + appJob + globalExceptionHandler)

    /**
     * 公共的数据库串行线程池 (ExecutorService)
     * 适用于：
     * 1. 需要传 Executor 的第三方 SDK
     * 2. 纯 Java 代码的后台任务
     * 3. 协程 Dispatcher 的底层实现
     */
    @JvmStatic
    val dbExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "Serial-DB-Thread")
        }
    }

    /**
     * 对应的协程调度器 (Dispatcher)
     * 如果你只是想在协程里切换到这个线程，也可以直接用这个 Dispatcher
     */
    val dbDispatcher: CoroutineDispatcher by lazy {
        dbExecutor.asCoroutineDispatcher()
    }

    /**
     * 4. (可选) 串行数据库作用域
     * 适用于：IM 消息写入。如果你担心多线程并发写入导致消息乱序，
     * 可以用这个由单线程线程池支持的 Scope。
     * 注意: 里面不可以再出现 suspend 函数, 因为它会让出线程, 让下一个立马执行, 做不到串行效果
     */
    val dbScope by lazy {
        CoroutineScope(dbDispatcher + appJob + globalExceptionHandler)
    }

    /**
     * 文件操作专用线程池
     */
    @JvmStatic
    val fileExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "Serial-File-Thread")
        }
    }

    /**
     * 对应的协程调度器 (Dispatcher)
     * 如果你只是想在协程里切换到这个线程，也可以直接用这个 Dispatcher
     */
    val fileDispatcher: CoroutineDispatcher by lazy {
        fileExecutor.asCoroutineDispatcher()
    }

    /**
     * 文件操作专用协程 Scope
     * 适用于：解压资源、移动大文件、批量删除日志、写入大段文本
     */
    val fileScope: CoroutineScope by lazy {
        CoroutineScope(fileDispatcher + appJob + globalExceptionHandler)
    }

    /**
     * 这会取消所有通过 ioScope、serialDbScope 启动的正在运行的任务
     */
    @JvmStatic
    fun cancelAll() {
        appJob.cancelChildren()
    }

}