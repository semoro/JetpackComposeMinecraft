package club.eridani.client


import kotlinx.coroutines.CoroutineDispatcher
import org.lwjgl.glfw.GLFW
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

    fun stop() {
        isStopped = true
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(tasks) {
            tasks.add(block)
        }
    }
}