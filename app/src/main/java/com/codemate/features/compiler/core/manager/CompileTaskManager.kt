package com.codemate.features.compiler.core.manager

import android.util.Log
import com.codemate.features.compiler.core.bridge.TermuxBridge
import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.Comparator

/**
 * 编译任务管理器
 * 负责管理编译任务的队列、优先级、并发控制和状态跟踪
 * 支持任务调度、执行监控和结果管理
 */
class CompileTaskManager(
    private val termuxBridge: TermuxBridge,
    private val maxConcurrentTasks: Int = 3
) {
    companion object {
        private const val TAG = "CompileTaskManager"
        private const val TASK_TIMEOUT = 300000L // 5分钟超时
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskCounter = AtomicLong(0)
    
    // 任务队列（按优先级排序）
    private val taskQueue = PriorityBlockingQueue<CompileTask>(11, TaskPriorityComparator())
    
    // 正在执行的任务
    private val runningTasks = mutableMapOf<String, Deferred<ProcessResult>>()
    
    // 任务状态流
    private val _taskStates = MutableStateFlow<List<CompileTask>>(emptyList())
    val taskStates: StateFlow<List<CompileTask>> = _taskStates.asStateFlow()
    
    // 任务事件流
    private val _taskEvents = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 100)
    val taskEvents = _taskEvents.asSharedFlow()
    
    // 任务监听器
    private val taskListeners = mutableListOf<TaskListener>()
    
    // 线程安全的状态引用
    private val currentState = AtomicReference<TaskManagerState>(TaskManagerState.IDLE)

    /**
     * 启动任务管理器
     */
    fun start() {
        if (currentState.get() != TaskManagerState.IDLE) {
            Log.w(TAG, "Task manager is already running")
            return
        }
        
        currentState.set(TaskManagerState.RUNNING)
        scope.launch {
            taskScheduler()
        }
        
        Log.i(TAG, "Compile task manager started")
    }

    /**
     * 停止任务管理器
     */
    suspend fun stop() {
        currentState.set(TaskManagerState.STOPPING)
        
        // 取消所有正在运行的任务
        runningTasks.values.forEach { deferred ->
            deferred.cancel()
        }
        
        // 等待所有任务完成
        runningTasks.clear()
        taskQueue.clear()
        
        currentState.set(TaskManagerState.STOPPED)
        scope.cancel()
        
        Log.i(TAG, "Compile task manager stopped")
    }

    /**
     * 添加编译任务
     */
    suspend fun addTask(task: CompileTask): String {
        val taskWithId = task.copy(
            id = generateTaskId(),
            createdAt = java.util.Date()
        )
        
        taskQueue.offer(taskWithId)
        updateTaskStates()
        
        // 通知监听器
        notifyTaskListeners { onTaskAdded(taskWithId) }
        emitEvent(TaskEvent.TaskAdded(taskWithId))
        
        Log.d(TAG, "Added task: ${taskWithId.id} (${taskWithId.targetLanguage.displayName})")
        
        return taskWithId.id
    }

    /**
     * 取消任务
     */
    suspend fun cancelTask(taskId: String): Boolean {
        // 从队列中移除待执行任务
        val cancelled = taskQueue.removeAll { it.id == taskId }
        
        // 取消正在运行的任务
        val runningTask = runningTasks[taskId]
        if (runningTask != null) {
            runningTask.cancel()
            runningTasks.remove(taskId)
        }
        
        if (cancelled || runningTask != null) {
            updateTaskStates()
            emitEvent(TaskEvent.TaskCancelled(taskId))
            notifyTaskListeners { onTaskCancelled(taskId) }
            
            Log.d(TAG, "Cancelled task: $taskId")
            return true
        }
        
        Log.w(TAG, "Task not found for cancellation: $taskId")
        return false
    }

    /**
     * 暂停任务管理器
     */
    fun pause() {
        if (currentState.get() == TaskManagerState.RUNNING) {
            currentState.set(TaskManagerState.PAUSED)
            emitEvent(TaskEvent.ManagerPaused)
            Log.i(TAG, "Task manager paused")
        }
    }

    /**
     * 恢复任务管理器
     */
    fun resume() {
        if (currentState.get() == TaskManagerState.PAUSED) {
            currentState.set(TaskManagerState.RUNNING)
            emitEvent(TaskEvent.ManagerResumed)
            Log.i(TAG, "Task manager resumed")
        }
    }

    /**
     * 获取任务状态
     */
    suspend fun getTaskState(taskId: String): CompileTask? {
        return _taskStates.value.find { it.id == taskId }
    }

    /**
     * 获取所有任务状态
     */
    suspend fun getAllTasks(): List<CompileTask> = _taskStates.value

    /**
     * 清理已完成的任务
     */
    suspend fun cleanupCompletedTasks() {
        val completedTasks = _taskStates.value.filter { it.isCompleted() }
        completedTasks.forEach { task ->
            emitEvent(TaskEvent.TaskCompleted(task))
        }
        updateTaskStates()
        Log.d(TAG, "Cleaned up ${completedTasks.size} completed tasks")
    }

    /**
     * 添加任务监听器
     */
    fun addTaskListener(listener: TaskListener) {
        taskListeners.add(listener)
    }

    /**
     * 移除任务监听器
     */
    fun removeTaskListener(listener: TaskListener) {
        taskListeners.remove(listener)
    }

    /**
     * 任务调度器
     */
    private suspend fun taskScheduler() {
        while (currentState.get() == TaskManagerState.RUNNING) {
            try {
                // 检查是否可以启动新任务
                if (runningTasks.size < maxConcurrentTasks) {
                    val nextTask = taskQueue.poll()
                    if (nextTask != null) {
                        launchTask(nextTask)
                    }
                }
                
                // 检查正在运行的任务状态
                val completedTasks = mutableListOf<String>()
                runningTasks.forEach { (taskId, deferred) ->
                    if (deferred.isCompleted) {
                        completedTasks.add(taskId)
                    }
                }
                
                // 处理已完成的任务
                completedTasks.forEach { taskId ->
                    handleTaskCompletion(taskId)
                }
                
                // 短暂休眠避免过度轮询
                delay(100)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in task scheduler", e)
                delay(1000) // 错误后稍长等待
            }
        }
    }

    /**
     * 启动任务执行
     */
    private suspend fun launchTask(task: CompileTask) {
        try {
            val updatedTask = task.copy(status = TaskStatus.RUNNING, startTime = java.util.Date())
            updateTaskInState(updatedTask)
            
            // 异步执行任务
            val deferred = scope.async {
                termuxBridge.executeCompileTask(task) { event ->
                    handleOutputEvent(task.id, event)
                }
            }
            
            runningTasks[task.id] = deferred
            emitEvent(TaskEvent.TaskStarted(task.id))
            notifyTaskListeners { onTaskStarted(task) }
            
            Log.d(TAG, "Started task: ${task.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch task: ${task.id}", e)
            val failedTask = task.copy(
                status = TaskStatus.FAILED,
                endTime = java.util.Date(),
                errorOutput = "Failed to start task: ${e.message}"
            )
            updateTaskInState(failedTask)
            emitEvent(TaskEvent.TaskFailed(task.id, e.message ?: "Unknown error"))
        }
    }

    /**
     * 处理任务完成
     */
    private suspend fun handleTaskCompletion(taskId: String) {
        val deferred = runningTasks.remove(taskId) ?: return
        
        try {
            val result = deferred.await()
            val endTime = java.util.Date()
            
            val task = getTaskState(taskId) ?: return
            val completedTask = task.copy(
                status = if (result.success) TaskStatus.SUCCESS else TaskStatus.FAILED,
                endTime = endTime,
                output = result.output,
                errorOutput = result.errorOutput,
                exitCode = result.exitCode
            )
            
            updateTaskInState(completedTask)
            
            if (result.success) {
                emitEvent(TaskEvent.TaskSucceeded(taskId, result))
                notifyTaskListeners { onTaskSucceeded(completedTask, result) }
                Log.d(TAG, "Task completed successfully: $taskId")
            } else {
                emitEvent(TaskEvent.TaskFailed(taskId, result.errorOutput))
                notifyTaskListeners { onTaskFailed(completedTask, result) }
                Log.w(TAG, "Task failed: $taskId, exit code: ${result.exitCode}")
            }
            
        } catch (e: CancellationException) {
            val task = getTaskState(taskId) ?: return
            val cancelledTask = task.copy(
                status = TaskStatus.CANCELLED,
                endTime = java.util.Date()
            )
            updateTaskInState(cancelledTask)
            
            emitEvent(TaskEvent.TaskCancelled(taskId))
            notifyTaskListeners { onTaskCancelled(taskId) }
            Log.d(TAG, "Task cancelled: $taskId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error completing task: $taskId", e)
            val task = getTaskState(taskId) ?: return
            val errorTask = task.copy(
                status = TaskStatus.FAILED,
                endTime = java.util.Date(),
                errorOutput = "Task execution error: ${e.message}"
            )
            updateTaskInState(errorTask)
            
            emitEvent(TaskEvent.TaskFailed(taskId, e.message ?: "Unknown error"))
        }
    }

    /**
     * 处理输出事件
     */
    private fun handleOutputEvent(taskId: String, event: OutputEvent) {
        when (event) {
            is OutputEvent.Progress -> {
                emitEvent(TaskEvent.TaskProgress(taskId, event.progress, event.message))
            }
            is OutputEvent.Error -> {
                emitEvent(TaskEvent.TaskError(taskId, event.message))
            }
            is OutputEvent.Warning -> {
                emitEvent(TaskEvent.TaskWarning(taskId, event.message))
            }
            else -> {
                // 其他事件可以在这里处理或记录
            }
        }
    }

    /**
     * 更新任务状态
     */
    private fun updateTaskStates() {
        val allTasks = mutableListOf<CompileTask>()
        
        // 添加队列中的任务
        allTasks.addAll(taskQueue)
        
        // 添加运行中的任务
        runningTasks.keys.forEach { taskId ->
            getTaskState(taskId)?.let { allTasks.add(it) }
        }
        
        _taskStates.value = allTasks.sortedWith(TaskComparator())
    }

    /**
     * 更新单个任务状态
     */
    private fun updateTaskInState(task: CompileTask) {
        val currentTasks = _taskStates.value.toMutableList()
        val index = currentTasks.indexOfFirst { it.id == task.id }
        
        if (index >= 0) {
            currentTasks[index] = task
        } else {
            currentTasks.add(task)
        }
        
        _taskStates.value = currentTasks.sortedWith(TaskComparator())
    }

    /**
     * 发出任务事件
     */
    private suspend fun emitEvent(event: TaskEvent) {
        _taskEvents.emit(event)
    }

    /**
     * 通知任务监听器
     */
    private fun notifyTaskListeners(action: TaskListener.() -> Unit) {
        taskListeners.forEach { listener ->
            try {
                listener.action()
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying task listener", e)
            }
        }
    }

    /**
     * 生成任务ID
     */
    private fun generateTaskId(): String = "task_${taskCounter.incrementAndGet()}"

    /**
     * 任务优先级比较器
     */
    private class TaskPriorityComparator : Comparator<CompileTask> {
        override fun compare(task1: CompileTask, task2: CompileTask): Int {
            // 按优先级排序（高优先级在前）
            val priorityOrder = mapOf(
                TaskPriority.CRITICAL to 0,
                TaskPriority.HIGH to 1,
                TaskPriority.NORMAL to 2,
                TaskPriority.LOW to 3
            )
            
            val priority1 = priorityOrder[task1.priority] ?: 2
            val priority2 = priorityOrder[task2.priority] ?: 2
            
            if (priority1 != priority2) {
                return priority1.compareTo(priority2)
            }
            
            // 相同优先级按创建时间排序
            return task1.createdAt.compareTo(task2.createdAt)
        }
    }

    /**
     * 任务比较器（用于UI显示）
     */
    private class TaskComparator : Comparator<CompileTask> {
        override fun compare(task1: CompileTask, task2: CompileTask): Int {
            // 运行中的任务优先
            if (task1.isRunning() && !task2.isRunning()) return -1
            if (!task1.isRunning() && task2.isRunning()) return 1
            
            // 然后按优先级
            val priorityOrder = mapOf(
                TaskPriority.CRITICAL to 0,
                TaskPriority.HIGH to 1,
                TaskPriority.NORMAL to 2,
                TaskPriority.LOW to 3
            )
            
            val priority1 = priorityOrder[task1.priority] ?: 2
            val priority2 = priorityOrder[task2.priority] ?: 2
            
            if (priority1 != priority2) {
                return priority1.compareTo(priority2)
            }
            
            // 最后按创建时间
            return task2.createdAt.compareTo(task1.createdAt)
        }
    }
}

/**
 * 任务管理器状态枚举
 */
enum class TaskManagerState {
    IDLE, RUNNING, PAUSED, STOPPING, STOPPED
}

/**
 * 任务事件
 */
sealed class TaskEvent {
    data class TaskAdded(val task: CompileTask) : TaskEvent()
    data class TaskStarted(val taskId: String) : TaskEvent()
    data class TaskProgress(val taskId: String, val progress: Int, val message: String) : TaskEvent()
    data class TaskSucceeded(val taskId: String, val result: ProcessResult) : TaskEvent()
    data class TaskFailed(val taskId: String, val error: String) : TaskEvent()
    data class TaskCompleted(val task: CompileTask) : TaskEvent()
    data class TaskCancelled(val taskId: String) : TaskEvent()
    data class TaskError(val taskId: String, val error: String) : TaskEvent()
    data class TaskWarning(val taskId: String, val warning: String) : TaskEvent()
    object ManagerPaused : TaskEvent()
    object ManagerResumed : TaskEvent()
}

/**
 * 任务监听器接口
 */
interface TaskListener {
    suspend fun onTaskAdded(task: CompileTask) {}
    suspend fun onTaskStarted(task: CompileTask) {}
    suspend fun onTaskSucceeded(task: CompileTask, result: ProcessResult) {}
    suspend fun onTaskFailed(task: CompileTask, result: ProcessResult) {}
    suspend fun onTaskCancelled(taskId: String) {}
    suspend fun onTaskProgress(taskId: String, progress: Int, message: String) {}
}