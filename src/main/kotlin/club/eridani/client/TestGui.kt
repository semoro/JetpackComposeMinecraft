package club.eridani.client

//import org.lwjgl.input.Mouse
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import club.eridani.compose.Context
import club.eridani.compose.MSAAFramebuffer
import club.eridani.util.mc
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import org.jetbrains.skia.ByteBuffer
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING
import org.lwjgl.opengl.GL33.glBindSampler
import java.nio.ByteOrder

@OptIn(ExperimentalAnimationApi::class)
class TestGui : Screen(Text.of("Eridani")) {

    val context = DirectContext.makeGL()
    var surface = createSurface(width, height, context) // Skia Surface, bound to the OpenGL framebuffer
    val glfwDispatcher = GlfwCoroutineDispatcher() // a custom coroutine dispatcher, in which Compose will run

    var closed = false
    val buf = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())

    val mcContext = Context()
    val jetpackContext = Context()

    init {
        glEnable(GL_MULTISAMPLE)
    }

    val composeFrameBuffer: MSAAFramebuffer

    init {

        glEnable(GL_MULTISAMPLE)
        composeFrameBuffer = MSAAFramebuffer(mc.window.width, mc.window.height, false)
        glDisable(GL_MULTISAMPLE)
    }

    val targetFbo = Framebuffer(mc.window.width, mc.window.height, false, false)

    override fun onClose() {
        super.onClose()
        closed = true
        composeScene.close()
    }


    val density = Density(glfwGetWindowContentScale(mc.window.handle))
    val composeScene: ComposeScene = ComposeScene(glfwDispatcher, density)

    init {

        composeScene.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(1000.dp, 500.dp).background(Color.White)) {
                    Text("Text Renderer 中文render")
                }
            }
        }

        composeScene.constraints = Constraints(maxWidth = width, maxHeight = height)

    }


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun render(matrixStack: MatrixStack?, i: Int, j: Int, f: Float) {
        super.render(matrixStack, i, j, f)

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
        surface.canvas.clear(org.jetbrains.skia.Color.WHITE)
        composeScene.render(surface.canvas, System.nanoTime())
        context.flush()
        composeFrameBuffer.endWrite()

        swapGlContextState(jetpackContext, mcContext)

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFbo.fbo)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, composeFrameBuffer.fbo)
        glBlitFramebuffer(0, 0, mc.window.width, mc.window.height, 0, 0, mc.window.width, mc.window.height, GL_COLOR_BUFFER_BIT, GL_NEAREST)

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
}