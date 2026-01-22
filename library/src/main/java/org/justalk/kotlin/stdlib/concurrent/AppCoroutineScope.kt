package org.justalk.kotlin.stdlib.concurrent

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * 全局协程作用域管理
 * 用于替代 GlobalScope，提供可控的、生命周期随 App 的协程环境
 */
object AppCoroutineScope {

    private const val TAG = "AppCoroutineScope"

    // 1. 定义一个变量来持有外部传入的处理逻辑
    // (Throwable) -> Unit 表示接收一个异常，没有返回值
    private var externalErrorHandler: ((Throwable) -> Unit)? = null

    /**
     * 2. 提供一个初始化方法，供 App 层调用
     * 建议在 Application.onCreate 中调用
     */
    fun setErrorHandler(handler: (Throwable) -> Unit) {
        externalErrorHandler = handler
    }

    // 1. 全局异常处理器：这里可以接入你们的 Bugly/Firebase Crashlytics
    private val globalExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        Log.e(TAG, "Global Background Error: ${throwable.message}", throwable)
        externalErrorHandler?.invoke(throwable)
    }

    // 2. SupervisorJob：保证一个任务崩了，不会把整个 Scope 搞挂，其它模块还能跑
    private val appJob = SupervisorJob()

    /**
     * 3. IO 作用域
     * 适用于：数据库读写、文件操作、网络请求
     * 特点：线程池调度，并发高
     */
    val ioScope = CoroutineScope(Dispatchers.IO + appJob + globalExceptionHandler)

    /**
     * 4. (可选) 串行数据库作用域
     * 适用于：IM 消息写入。如果你担心多线程并发写入导致消息乱序，
     * 可以用这个由单线程线程池支持的 Scope。
     */
    val serialDbScope by lazy {
        val singleThreadDispatcher = Executors.newSingleThreadExecutor { r ->
            Thread(r, "Serial-DB-Thread")
        }.asCoroutineDispatcher()
        CoroutineScope(singleThreadDispatcher + appJob + globalExceptionHandler)
    }

    /**
     * 5. 主线程作用域 (慎用，一般建议用 LifecycleScope/ViewModelScope)
     * 但在某些纯静态工具类需要回调 UI 时可能会用到
     */
    val mainScope = CoroutineScope(Dispatchers.Main.immediate + appJob + globalExceptionHandler)

    /**
     * 销毁方法
     * 仅在 APP 彻底退出或进程被杀前调用，一般情况不需要调用
     */
    fun cancelAll() {
        appJob.cancel()
    }

}