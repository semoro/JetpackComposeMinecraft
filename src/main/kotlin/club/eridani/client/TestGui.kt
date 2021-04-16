package club.eridani.client

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.mouse.*
import club.eridani.compose.*
import club.eridani.compose.ComposeLayer
import club.eridani.mixin.gl.MixinFramebuffer
import club.eridani.util.mc
import kotlinx.coroutines.*
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
//import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE
import org.lwjgl.opengl.GL32.glTexImage2DMultisample
import org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING
import org.lwjgl.opengl.GL33.glBindSampler
import org.lwjgl.opengl.GL43.GL_SAMPLER
import java.nio.*
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.coroutines.*

@OptIn(ExperimentalAnimationApi::class) class TestGui : Screen(Text.of("Eridani")) {
    val queue = ArrayDeque<Runnable>()
    val timedQueue = PriorityQueue<TimedRequest>(compareBy { it.timeMillis })

    @OptIn(InternalCoroutinesApi::class)
    private val composeLayer = ComposeLayer(object : CoroutineDispatcher(), Delay {
        val creator = Thread.currentThread()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (creator != Thread.currentThread()) {
                error("!")
            }
            queue.addLast(block)
        }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean {
            return true
        }

        override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
            timedQueue += TimedRequest(System.currentTimeMillis() + timeMillis, continuation)
        }
    })

    lateinit var msaaFbo: Framebuffer
    lateinit var targetFbo: Framebuffer

    val buf = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())

    val mcContext = Context()
    val jetpackContext = Context()
    val renderContext = Context()

    override fun resize(minecraftClient: MinecraftClient?, i: Int, j: Int) {
        msaaFbo.delete()
        targetFbo.delete()
//        composeLayer.reinit()
        super.resize(minecraftClient, i, j)
    }

    fun checkGL() {
        glGetError().takeIf { it != 0 }?.let {
            error("BUG $it")
        }
    }

    init {
        composeLayer.setContent {
            Column(Modifier.fillMaxSize().background(Color(255, 255, 255, 100))) {

                var show by remember { mutableStateOf(false) }

                Button(onClick = {show = true}) {
                    Text("Jetpack Compose in Minecraft!")
                }

                AnimatedVisibility(show) {
                    Text("Jetpack Compose is here!")
                }

            }
        }
    }

    fun saveGlContext(store: Context) {
        store.shader = glGetInteger(GL_CURRENT_PROGRAM)
        store.arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        store.activeTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        store.bindTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        store.enableScissor = glGetBoolean(GL_SCISSOR_TEST)
        if (store.enableScissor) {
            glGetIntegerv(GL_SCISSOR_BOX, store.scissorBox.apply { clear() })
        }
        store.bindSampler = glGetInteger(GL_SAMPLER_BINDING)

        checkGL()

        for (index in store.enableVertexAttribArray.indices) {
            glGetVertexAttribIiv(index, GL_VERTEX_ATTRIB_ARRAY_ENABLED, buf.apply { clear() }.asIntBuffer())
            store.enableVertexAttribArray[index] = buf.int
        }
    }

    fun restoreGlContext(load: Context) {
        for (index in load.enableVertexAttribArray.indices) {
            val v = load.enableVertexAttribArray[index]
            if (v == GL_FALSE)
                glDisableVertexAttribArray(index)
            else
                glEnableVertexAttribArray(index)
        }

        checkGL()


        glBindBuffer(GL_ARRAY_BUFFER, load.arrayBuffer)
        checkGL()
        glUseProgram(load.shader)
        checkGL()
        if (load.activeTexture != 0) glActiveTexture(load.activeTexture)
        checkGL()
        glBindTexture(GL_TEXTURE_2D, load.bindTexture)
        checkGL()

        if (load.enableScissor) {
            glEnable(GL_SCISSOR_TEST)
            glScissor(load.scissorBox[0], load.scissorBox[1], load.scissorBox[2], load.scissorBox[3])
        } else {
            glDisable(GL_SCISSOR_TEST)
        }
        checkGL()

        if (load.activeTexture != 0) glBindSampler(load.activeTexture - GL_TEXTURE0, load.bindSampler)

        checkGL()
    }


    fun schedule(block: suspend CoroutineScope.() -> Unit) {
        GlobalScope.launch(composeLayer.dispatcher, block = block)
    }


    override fun mouseClicked(d: Double, e: Double, i: Int): Boolean {
        schedule { composeLayer.owners?.onMousePressed(d.toInt(), e.toInt()) }
        return true
    }

    override fun mouseDragged(d: Double, e: Double, i: Int, f: Double, g: Double): Boolean {
        schedule { composeLayer.owners?.onMouseDragged(d.toInt(), e.toInt()) }
        return true
    }

    override fun mouseReleased(d: Double, e: Double, i: Int): Boolean {
        schedule { composeLayer.owners?.onMouseReleased(d.toInt(), e.toInt()) }
        return true
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double): Boolean {
        schedule {
            composeLayer.owners?.onMouseScroll(
                d.toInt(),
                e.toInt(),
                MouseScrollEvent(MouseScrollUnit.Page((f * -1f / mc.window.height).toFloat()), Orientation.Vertical)
            )
        }
        return true
    }

    override fun mouseMoved(d: Double, e: Double) {
        schedule { composeLayer.owners?.onMouseMoved(d.toInt(), e.toInt()) }
    }

    fun swapGlContextState(store: Context, load: Context) {
        saveGlContext(store)
        restoreGlContext(load)
    }

    override fun onClose() {
        composeLayer.dispose()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun render(matrixStack: MatrixStack?, i: Int, j: Int, f: Float) {
        super.render(matrixStack, i, j, f)

        val outputFb = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)

        checkGL()
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_ALPHA_TEST)
        glDisable(GL_CULL_FACE)

        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)


        msaaFbo.clear(false)

        swapGlContextState(mcContext, jetpackContext)

        (msaaFbo as MixinFramebuffer).eridaniBind(true)


        glEnable(GL_MULTISAMPLE)
        glEnable(GL_BLEND)


        checkGL()



        while (queue.isNotEmpty()) {
            val task = queue.removeFirst()
            task.run()
            checkGL()
        }

        val currentTime = System.currentTimeMillis()
        while (timedQueue.isNotEmpty()) {
            if (timedQueue.peek().timeMillis <= currentTime) {
                timedQueue.poll().continuation.resume(Unit)
            } else {
                break
            }
        }



        composeLayer.wrapped.draw()
        msaaFbo.endRead()

        checkGL()


        swapGlContextState(jetpackContext, mcContext)

        checkGL()

//        glDisable(GL_BLEND)

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFbo.fbo)   // Make sure no FBO is set as the draw framebuffer
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo.fbo) // Make sure your multisampled FBO is the read framebuffer
        glBlitFramebuffer(
            0,
            0,
            mc.window.width,
            mc.window.height,
            0,
            0,
            mc.window.width,
            mc.window.height,
            GL_COLOR_BUFFER_BIT,
            GL_NEAREST
        )

        glDisable(GL_MULTISAMPLE)



        glBindFramebuffer(GL_FRAMEBUFFER, outputFb)
        targetFbo.draw(mc.window.width, mc.window.height, false)

        checkGL()

        glEnable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
//        glEnable(GL_ALPHA_TEST)
        glEnable(GL_CULL_FACE)


    }

    override fun init() {
        glEnable(GL_MULTISAMPLE)
        msaaFbo = MSAAFramebuffer(mc.window.width, mc.window.height, false)
        glDisable(GL_MULTISAMPLE)

        targetFbo = Framebuffer(mc.window.width, mc.window.height, false, false)

        composeLayer.wrapped.setSize(mc.window.width, mc.window.height)
        composeLayer.needRedrawLayer()
        composeLayer.reinit()
    }

}