package club.eridani.client


import kotlinx.coroutines.*
import org.lwjgl.glfw.GLFW
import java.lang.Runnable
import java.util.*
import kotlin.coroutines.CoroutineContext

private data class TimedRequest(val timeMillis: Long, val continuation: CancellableContinuation<Unit>)

@OptIn(InternalCoroutinesApi::class)
class GlfwCoroutineDispatcher : CoroutineDispatcher(), Delay {

    private val tasks = ArrayDeque<Runnable>()
    private val timedQueue = PriorityQueue<TimedRequest>(compareBy { it.timeMillis })

    var isStopped = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun runLoop() {
        synchronized(tasks) {
            while (tasks.isNotEmpty()) {
                val task = tasks.removeFirst()
                task.run()
            }
        }

        val currentTime = System.currentTimeMillis()
        while (timedQueue.isNotEmpty()) {
            if (timedQueue.peek().timeMillis <= currentTime) {
                timedQueue.poll().continuation.resume(Unit, null)
            } else {
                break
            }
        }
    }

    fun stop() {
        isStopped = true
        timedQueue.clear()
        tasks.clear()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(tasks) {
            tasks.addLast(block)
        }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        timedQueue += TimedRequest(timeMillis, continuation)
    }
}