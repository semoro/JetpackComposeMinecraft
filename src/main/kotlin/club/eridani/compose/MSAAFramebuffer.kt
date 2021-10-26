package club.eridani.compose

import club.eridani.mixin.gl.MixinFramebuffer
import kotlinx.coroutines.CancellableContinuation
import net.minecraft.client.gl.Framebuffer
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE
import org.lwjgl.opengl.GL32.glTexImage2DMultisample
import java.nio.*

class MSAAFramebuffer(w: Int, h: Int, depth: Boolean) : Framebuffer(false) {
    init {
        initFbo(w, h, false)
        clear(false)
    }


    // read
    override fun method_35610() {
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, colorAttachment)
    }

    override fun endRead() {
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, 0)
    }

    override fun initFbo(width: Int, height: Int, bl: Boolean) {
        textureWidth = width
        textureHeight = height
        viewportWidth = width
        viewportHeight = height

        fbo = glGenFramebuffers()
        (this as MixinFramebuffer).`access$setColorAttachment`(glGenTextures())

//        beginRead()
        method_35610()
        glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, 4, GL_RGBA8, viewportWidth, viewportHeight, false)
        endRead()

        (this as MixinFramebuffer).`access$bind`(false)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D_MULTISAMPLE, colorAttachment, 0)

        clear(false)
        endRead()


    }

}

data class TimedRequest(val timeMillis: Long, val continuation: CancellableContinuation<Unit>)

class Context {
    var shader = 0
    var arrayBuffer = 0
    var activeTexture = 0
    var bindTexture = 0
    var bindSampler = 0
    var enableScissor = false


    val scissorBox = ByteBuffer.allocateDirect(4 * 16).order(ByteOrder.nativeOrder()).asIntBuffer()
    val enableVertexAttribArray: IntArray by lazy { IntArray(glGetInteger(GL_MAX_VERTEX_ATTRIBS)) }

}