package club.eridani.client

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontLoader
import androidx.compose.ui.text.platform.FontLoader
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import club.eridani.compose.Context
import club.eridani.compose.MSAAFramebuffer
import club.eridani.util.mc
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import org.jetbrains.skia.ByteBuffer
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Surface
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING
import org.lwjgl.opengl.GL33.glBindSampler
import java.nio.ByteOrder

@OptIn(ExperimentalAnimationApi::class)
class TestGui : Screen(Text.of("Eridani")) {
    val glfwDispatcher = GlfwCoroutineDispatcher() // a custom coroutine dispatcher, in which Compose will run

    var closed = false
    val buf = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())

    val mcContext = Context()
    val jetpackContext = Context()


    lateinit var surface: Surface
    lateinit var context: DirectContext

    lateinit var composeFrameBuffer: MSAAFramebuffer

    lateinit var targetFbo: Framebuffer

    lateinit var density: Density
    lateinit var composeScene: ComposeScene



    override fun init() {
//        RenderSystem.assertThread { RenderSystem.isOnRenderThreadOrInit() }

//        targetFbo = Framebuffer(mc.window.width, mc.window.height, false, false)

        targetFbo = object : Framebuffer(false) {
            init {
                clear(false)
                initFbo(mc.window.width, mc.window.height, false)
            }
        }


        targetFbo.clear(true)
        glEnable(GL_MULTISAMPLE)
        composeFrameBuffer = MSAAFramebuffer(mc.window.width, mc.window.height, false)
        composeFrameBuffer.beginWrite(true)


        context = DirectContext.makeGL()
        surface =
            createSurface(mc.window.width, mc.window.height, context) // Skia Surface, bound to the OpenGL framebuffer

        glDisable(GL_MULTISAMPLE)

        println("target fbo at: ${targetFbo.fbo}")
        println("compose fbo at: ${composeFrameBuffer.fbo}")

        density = Density(glfwGetWindowContentScale(mc.window.handle))
        composeScene = ComposeScene(glfwDispatcher, density) {
            invalidated = true
        }

        val loader = FontLoader()
        composeScene.setContent {
            CompositionLocalProvider(
                LocalFontLoader provides loader
            ) {
                MaterialTheme {
                    TestContent()
                }
            }
        }

        composeScene.constraints = Constraints(maxWidth = mc.window.width, maxHeight = mc.window.height)

        composeFrameBuffer.endWrite()

    }


    override fun onClose() {
        super.onClose()
        closed = true
        composeScene.dispose()
    }

    var invalidated = false


    @Composable
    fun TestContent() {
        Box(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.TopCenter) {
            Text("Render text 中文渲染", color = Color.White, fontSize = 50.sp)
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun render(matrixStack: MatrixStack?, i: Int, j: Int, f: Float) {
        super.render(matrixStack, i, j, f)

        RenderSystem.assertThread { RenderSystem.isOnRenderThreadOrInit() }
        val outputFb = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)

        glDisable(GL_DEPTH_TEST)
        glDisable(GL_ALPHA_TEST)
        glDisable(GL_CULL_FACE)

        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

        composeFrameBuffer.clear(true)
        swapGlContextState(mcContext, jetpackContext)
        composeFrameBuffer.beginWrite(true)

        glEnable(GL_MULTISAMPLE)
        glEnable(GL_BLEND)


        glfwDispatcher.runLoop()
        if (invalidated) {
//            surface.canvas.clear(org.jetbrains.skia.Color.TRANSPARENT)
            composeScene.render(surface.canvas, System.nanoTime())
        }
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
        targetFbo.draw(mc.window.width, mc.window.height, false)

        glEnable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)

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

    override fun isPauseScreen(): Boolean {
        return false
    }
}