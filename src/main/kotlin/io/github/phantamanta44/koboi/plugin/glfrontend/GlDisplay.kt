package io.github.phantamanta44.koboi.plugin.glfrontend

import com.jogamp.newt.event.WindowAdapter
import com.jogamp.newt.event.WindowEvent
import com.jogamp.newt.opengl.GLWindow
import com.jogamp.opengl.*
import com.jogamp.opengl.util.FPSAnimator
import com.jogamp.opengl.util.GLBuffers
import io.github.phantamanta44.koboi.graphics.IDisplay
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

class GlDisplay : GLEventListener, IDisplay {
    
    private var window: GLWindow by Delegates.notNull()
    private val animator: FPSAnimator = FPSAnimator(60, true)

    private var fbHandles: IntBuffer = GLBuffers.newDirectIntBuffer(1)
    private var texHandles: IntBuffer = GLBuffers.newDirectIntBuffer(1)

    private var viewportX0: Int = 0
    private var viewportY0: Int = 0
    private var viewportX1: Int = 160
    private var viewportY1: Int = 144

    private val pixels: Array<FloatBuffer> = Array(144, { GLBuffers.newDirectFloatBuffer(FloatArray(160 * 3, { 0F })) })
    private val dirty: BooleanArray = BooleanArray(144, { true })
    private val displayEnabled: AtomicBoolean = AtomicBoolean(false)

    override fun init(drawable: GLAutoDrawable) {
        with(window.gl.gL3) {
            // gen stuff
            glGenFramebuffers(1, fbHandles)
            glGenTextures(1, texHandles)

            // init texture
            val texBuf = GLBuffers.newDirectByteBuffer(ByteArray(160 * 144 * 3, { 0 }))
            glActiveTexture(GL.GL_TEXTURE0)
            glBindTexture(GL.GL_TEXTURE_2D, texHandles[0])
            glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGB, 160, 144, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, texBuf)
            texBuf.destroy()

            // bind stuff
            glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fbHandles[0])
            glFramebufferTexture(GL.GL_READ_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, texHandles[0], 0)
            glClearColor(0F, 0F, 0F, 1F)
        }
    }

    override fun dispose(drawable: GLAutoDrawable) {
        with(drawable.gl.gL3) {
            glDeleteFramebuffers(1, fbHandles)
            fbHandles.destroy()
            glDeleteTextures(1, texHandles)
            texHandles.destroy()
            pixels.forEach { it.destroy() }
        }
    }

    override fun kill() {
        window.destroy()
    }

    override fun show(deathCallback: () -> Unit) {
        window = GLWindow.create(GLCapabilities(GLProfile.get(GLProfile.GL3)))
        window.addGLEventListener(this)
        with(window) {
            title = "Koboi"
            setSize(160, 144) // TODO command line flag
            addWindowListener(object : WindowAdapter() {
                override fun windowDestroyNotify(e: WindowEvent) {
                    if (animator.isStarted) animator.stop()
                    deathCallback()
                }
            })
            isResizable = true
            isVisible = true
            setPosition(window.screen.width / 2 - window.width / 2, window.screen.height / 2 - window.height / 2)
        }
        animator.add(window)
        animator.start()
    }

    override fun display(drawable: GLAutoDrawable) {
        with(drawable.gl.gL3) {
            glClear(GL.GL_COLOR_BUFFER_BIT)
            for (y in dirty.indices) {
                if (dirty[y]) {
                    dirty[y] = false
                    glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 143 - y, 160, 1, GL.GL_RGB, GL.GL_FLOAT, pixels[y])
                }
            }
            if (displayEnabled.get()) {
                glBlitFramebuffer(0, 0, 160, 144, viewportX0, viewportY0, viewportX1, viewportY1, GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST)
            }
        }
    }

    override fun writePixel(x: Int, y: Int, r: Int, g: Int, b: Int) {
        pixels[y].put(x * 3, r / 0x1F.toFloat())
        pixels[y].put(x * 3 + 1, g / 0x1F.toFloat())
        pixels[y].put(x * 3 + 2, b / 0x1F.toFloat())
    }

    override fun redrawScanLine(y: Int) {
        dirty[y] = true
    }

    override fun setDisplayEnabled(enabled: Boolean) {
        displayEnabled.set(enabled)
    }

    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) {
        val idealWinWidth = height * 160F / 144F
        if (width > idealWinWidth) { // ratio too horizontal
            viewport((width - idealWinWidth) / 2F, 0, idealWinWidth, height)
        } else { // ratio too vertical
            val idealWinHeight = width * 144F / 160F
            viewport(0, (height - idealWinHeight) / 2F, width, idealWinHeight)
        }
    }

    private fun viewport(x: Number, y: Number, width: Number, height: Number) {
        viewportX0 = x.toInt()
        viewportY0 = y.toInt()
        viewportX1 = viewportX0 + width.toInt()
        viewportY1 = viewportY0 + height.toInt()
    }

}