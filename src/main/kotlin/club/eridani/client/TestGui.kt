package club.eridani.client

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import club.eridani.compose.Context
import club.eridani.compose.MSAAFramebuffer
import club.eridani.util.mc
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import org.jetbrains.skia.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING
import org.lwjgl.opengl.GL33.glBindSampler
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

@OptIn(ExperimentalAnimationApi::class)
class TestGui : Screen(Text.of("Eridani")) {
    val glfwDispatcher = GlfwCoroutineDispatcher() // a custom coroutine dispatcher, in which Compose will run

    var closed = false
    val buf = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())

    val mcContext = Context()
    val jetpackContext = Context()


    lateinit var surface: Surface
    lateinit var context: DirectContext

    lateinit var mcSurface: Surface

    lateinit var composeFrameBuffer: MSAAFramebuffer

    lateinit var targetFbo: Framebuffer

    lateinit var density: Density
    lateinit var composeScene: ComposeScene

    lateinit var mcImage: Image
    var frames by mutableStateOf(0)

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun init() {
        super.init()

        // Avoid race-conditions
        Class.forName("androidx.compose.ui.platform.GlobalSnapshotManager").kotlin.apply {
            this.declaredMemberProperties.find { it.name == "started" }!!.apply {
                isAccessible = true
                (this.getter.call() as AtomicBoolean).set(true)
            }
        }

        RenderSystem.assertThread { RenderSystem.isOnRenderThreadOrInit() }

        context = DirectContext.makeGL()
        mc.framebuffer.beginWrite(false)
        val mcRenderTarget = BackendRenderTarget.makeGL(mc.window.width, mc.window.height, 0, 0, mc.framebuffer.fbo, FramebufferFormat.GR_GL_RGBA8)
        mcSurface = Surface.makeFromBackendRenderTarget(
            context, mcRenderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB
        )
        mc.framebuffer.endWrite()
        mcImage = mcSurface.makeImageSnapshot()

        targetFbo = SimpleFramebuffer(mc.window.width, mc.window.height, false, false)
        glEnable(GL_MULTISAMPLE)

        composeFrameBuffer = MSAAFramebuffer(mc.window.width, mc.window.height, false)

        swapGlContextState(mcContext, jetpackContext)
        composeFrameBuffer.beginWrite(true)


        surface = createSurface(mc.window.width, mc.window.height, context) // Skia Surface, bound to the OpenGL framebuffer

        glDisable(GL_MULTISAMPLE)

        println("target fbo at: ${targetFbo.fbo}")
        println("compose fbo at: ${composeFrameBuffer.fbo}")

        density = Density(glfwGetWindowContentScale(mc.window.handle))
        composeScene = ComposeScene(glfwDispatcher, density) {
            invalidated = true
        }

        composeScene.setContent {
            Box(Modifier.fillMaxSize().blur(2.dp).drawWithContent {
                frames.let {
                    this.drawContext.canvas.nativeCanvas.drawImage(mcImage, 0f, 0f)
                }
            })
            MaterialTheme {
                TestContent()
            }
        }

        composeScene.constraints = Constraints(maxWidth = mc.window.width, maxHeight = mc.window.height)

        composeFrameBuffer.endWrite()

        swapGlContextState(jetpackContext, mcContext)
    }

    override fun onClose() {
        super.onClose()
        closed = true

        glfwDispatcher.stop()
        composeScene.dispose()
        this.mcSurface.close()
        this.surface.close()
        this.context.close()
        Runtime.getRuntime().runFinalization()
        this.targetFbo.delete()
        this.composeFrameBuffer.delete()
    }

    private var invalidated = false


    @Composable
    fun TestContent() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp, 150.dp).background(Color.Green)) {
                Column {
                    Row(Modifier.height(16.dp).background(Color.Gray)) {
                        Text("Test dialog")
                        Spacer(Modifier.weight(1f, fill = true))
                        IconButton({ println("Close!") }) {
                            Icon(Icons.Default.Close, "close")
                        }
                    }
                    Text("Text Renderer 中文render")
                    CircularProgressIndicator()
                }
            }
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun render(matrixStack: MatrixStack?, i: Int, j: Int, f: Float) {
        super.render(matrixStack, i, j, f)
        RenderSystem.assertThread { RenderSystem.isOnRenderThreadOrInit() }

        val outputFb = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)

        composeFrameBuffer.clear(true)
        swapGlContextState(mcContext, jetpackContext)


        mcSurface.notifyContentWillChange(ContentChangeMode.DISCARD)
        mcImage.close()
        mcImage = mcSurface.makeImageSnapshot()
        frames++


        composeFrameBuffer.beginWrite(true)

        glEnable(GL_MULTISAMPLE)

        glfwDispatcher.runLoop()
        if (invalidated) {
           composeScene.render(surface.canvas, System.nanoTime())
        }
        context.resetGLAll()
        context.flush()
        composeFrameBuffer.endWrite()

        swapGlContextState(jetpackContext, mcContext)

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFbo.fbo)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, composeFrameBuffer.fbo)
        glBlitFramebuffer(0,
            0,
            mc.window.width,
            mc.window.height,
            0,
            0,
            mc.window.width,
            mc.window.height,
            GL_COLOR_BUFFER_BIT,
            GL_NEAREST)

        glDisable(GL_MULTISAMPLE)

        glBindFramebuffer(GL_FRAMEBUFFER, outputFb)
        glEnable(GL_BLEND)

        targetFbo.draw(mc.window.width, mc.window.height, false)

        glEnable(GL_CULL_FACE)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFunc(770, 771)

        glBlendColor(0f, 0f, 0f, 1f)
        glDisable(GL_BLEND)
    }


    override fun shouldCloseOnEsc(): Boolean {
        return true
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


        glBindBuffer(GL_ARRAY_BUFFER, load.arrayBuffer)

        glUseProgram(load.shader)

        if (load.activeTexture != 0) glActiveTexture(load.activeTexture)

        glBindTexture(GL_TEXTURE_2D, load.bindTexture)


        if (load.enableScissor) {
            glEnable(GL_SCISSOR_TEST)
            glScissor(load.scissorBox[0], load.scissorBox[1], load.scissorBox[2], load.scissorBox[3])
        } else {
            glDisable(GL_SCISSOR_TEST)
        }


        if (load.activeTexture != 0) glBindSampler(load.activeTexture - GL_TEXTURE0, load.bindSampler)
    }

    fun swapGlContextState(store: Context, load: Context) {
        saveGlContext(store)
        restoreGlContext(load)
    }
}