package club.eridani.client


import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext


class GlfwCoroutineDispatcher : CoroutineDispatcher() {
    val tasks = mutableListOf<Runnable>()
    var isStopped = false

    fun runLoop() {
        while (tasks.isNotEmpty()) {
            val task = tasks.removeFirst()
            task.run()
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(tasks) {
            tasks.add(block)
        }
    }
}