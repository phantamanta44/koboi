package io.github.phantamanta44.koboi.plugin.glfrontend

import com.jogamp.newt.event.WindowAdapter
import com.jogamp.newt.event.WindowEvent
import com.jogamp.newt.opengl.GLWindow
import com.jogamp.opengl.*
import com.jogamp.opengl.util.FPSAnimator
import com.jogamp.opengl.util.GLBuffers
import io.github.phantamanta44.koboi.graphics.IDisplay
import io.github.phantamanta44.koboi.util.toShortHex
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.properties.Delegates

class GlDisplay : GLEventListener, IDisplay {
    
    private var window: GLWindow by Delegates.notNull()
    private val animator: FPSAnimator = FPSAnimator(60, true)

    private val vaoHandles: IntBuffer = GLBuffers.newDirectIntBuffer(1)
    private val boHandles: IntBuffer = GLBuffers.newDirectIntBuffer(2)
    private var shaderProgram: Int by Delegates.notNull()
    private val texHandles: IntBuffer = GLBuffers.newDirectIntBuffer(2)

    private val displayEnabled: AtomicBoolean = AtomicBoolean(false)
    private var activeTexture: Int = 0

    private val pixels: List<FloatBuffer> = List(2, { GLBuffers.newDirectFloatBuffer(FloatArray(160 * 144 * 3, { 0F })) })
    private var writablePixelBuffer: Int = 0
    private val dirty: AtomicBoolean = AtomicBoolean(false)
    private val pixelBufferLock: Lock = ReentrantLock()
    private val pixelBufferCondition: Condition = pixelBufferLock.newCondition()
    private val readyForFrame: AtomicBoolean = AtomicBoolean(true)

    override fun init(drawable: GLAutoDrawable) {
        with(window.gl.gL3) {
            // init vao
            glGenVertexArrays(1, vaoHandles)
            glBindVertexArray(vaoHandles[0])
            glErrorCheck()

            // init vbo and ebo
            glGenBuffers(2, boHandles)
            glBindBuffer(GL.GL_ARRAY_BUFFER, boHandles[0])
            val vertexData = GLBuffers.newDirectFloatBuffer(floatArrayOf(
                    -1F, -1F, 0F, 1F,
                    -1F, 1F, 0F, 0F,
                    1F, -1F, 1F, 1F,
                    1F, 1F, 1F, 0F))
            glBufferData(GL.GL_ARRAY_BUFFER, 64L, vertexData, GL.GL_STATIC_DRAW)
            vertexData.destroy()
            glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, boHandles[1])
            val indexData = GLBuffers.newDirectIntBuffer(intArrayOf(0, 1, 2, 1, 2, 3))
            glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, 24L, indexData, GL.GL_STATIC_DRAW)
            indexData.destroy()
            glErrorCheck()

            // init shaders
            val vert = glCreateShader(GL2ES2.GL_VERTEX_SHADER)
            val vertSrc = javaClass.getResource("/shader/shader.vert").readText()
            glShaderSource(vert, 1, arrayOf(vertSrc), intArrayOf(vertSrc.length), 0)
            glCompileShader(vert)
            val frag = glCreateShader(GL2ES2.GL_FRAGMENT_SHADER)
            val fragSrc = javaClass.getResource("/shader/shader.frag").readText()
            glShaderSource(frag, 1, arrayOf(fragSrc), intArrayOf(fragSrc.length), 0)
            glCompileShader(frag)
            shaderProgram = glCreateProgram()
            glAttachShader(shaderProgram, vert)
            glAttachShader(shaderProgram, frag)
            glLinkProgram(shaderProgram)
            glUseProgram(shaderProgram)
            glDeleteShader(vert)
            glDeleteShader(frag)
            glErrorCheck()

            // init vertex attrib pointers
            glEnableVertexAttribArray(0)
            glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 16, 0)
            glEnableVertexAttribArray(1)
            glVertexAttribPointer(1, 2, GL.GL_FLOAT, false, 16, 8)
            glErrorCheck()

            // bind texture units to samplers
            glUniform1i(glGetUniformLocation(shaderProgram, "texture0"), 0)
            glUniform1i(glGetUniformLocation(shaderProgram, "texture1"), 1)
            glErrorCheck()

            // init textures
            glGenTextures(2, texHandles)
            val texBuf = GLBuffers.newDirectByteBuffer(ByteArray(160 * 144 * 3, { 0 }))
            glActiveTexture(GL.GL_TEXTURE0)
            glBindTexture(GL.GL_TEXTURE_2D, texHandles[0])
            glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGB, 160, 144, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, texBuf)
            glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST)
            glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST)
            glActiveTexture(GL.GL_TEXTURE1)
            glBindTexture(GL.GL_TEXTURE_2D, texHandles[1])
            glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGB, 160, 144, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, texBuf)
            glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST)
            glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST)
            texBuf.destroy()
            glErrorCheck()

            // init gl state
            glViewport(0, 0, 160, 144)
            glClearColor(0F, 0F, 0F, 1F)
            glErrorCheck()
        }
    }

    override fun dispose(drawable: GLAutoDrawable) {
        with(drawable.gl.gL3) {
            // destroy textures
            glActiveTexture(GL.GL_TEXTURE0)
            glBindTexture(GL.GL_TEXTURE_2D, 0)
            glActiveTexture(GL.GL_TEXTURE1)
            glBindTexture(GL.GL_TEXTURE_2D, 0)
            glDeleteTextures(2, texHandles)
            texHandles.destroy()
            pixels.forEach(FloatBuffer::destroy)

            // destroy shaders
            glUseProgram(0)
            glDeleteProgram(shaderProgram)

            // destroy vbos
            glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
            glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
            glDeleteBuffers(2, boHandles)
            boHandles.destroy()

            // destroy vao
            glBindVertexArray(0)
            glDeleteVertexArrays(1, vaoHandles)
            vaoHandles.destroy()
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
            if (displayEnabled.get()) {
                if (dirty.get()) {
                    pixelBufferLock.lockInterruptibly()
                    try {
                        dirty.set(false)
                        glActiveTexture(when (activeTexture) {
                            0 -> GL.GL_TEXTURE0
                            1 -> GL.GL_TEXTURE1
                            else -> throw IllegalStateException()
                        })
                        glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGB, 160, 144, 0, GL.GL_RGB, GL.GL_FLOAT, pixels[writablePixelBuffer xor 1])
                        activeTexture = activeTexture xor 1
                        readyForFrame.set(true)
                        pixelBufferCondition.signalAll()
                    } finally {
                        pixelBufferLock.unlock()
                    }
                }
                glDrawElements(GL.GL_TRIANGLES, 6, GL.GL_UNSIGNED_INT, 0)
            }
        }
    }

    override fun writePixel(x: Int, y: Int, r: Int, g: Int, b: Int) {
        val index = (y * 160 + x) * 3
        val buf = pixels[writablePixelBuffer]
        buf.put(index, r / 0x1F.toFloat())
        buf.put(index + 1, g / 0x1F.toFloat())
        buf.put(index + 2, b / 0x1F.toFloat())
    }

    override fun vBlank() {
        pixelBufferLock.lockInterruptibly()
        try {
            while (!readyForFrame.get()) pixelBufferCondition.await()
            dirty.set(true)
            writablePixelBuffer = writablePixelBuffer xor 1
        } finally {
            pixelBufferLock.unlock()
        }
    }

    override fun setDisplayEnabled(enabled: Boolean) {
        displayEnabled.set(enabled)
    }

    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) {
        val idealWinWidth = height * 160F / 144F
        if (width > idealWinWidth) { // ratio too horizontal
            drawable.gl.gL3.viewport((width - idealWinWidth) / 2F, 0, idealWinWidth, height)
        } else { // ratio too vertical
            val idealWinHeight = width * 144F / 160F
            drawable.gl.gL3.viewport(0, (height - idealWinHeight) / 2F, width, idealWinHeight)
        }
    }

}

fun GL3.viewport(x: Number, y: Number, width: Number, height: Number) {
    glViewport(x.toInt(), y.toInt(), width.toInt(), height.toInt())
}

fun GL3.glErrorCheck() {
    val error = glGetError()
    if (error != GL.GL_NO_ERROR) throw IllegalStateException(when (error) {
        0x0500 -> "GL_INVALID_ENUM"
        0x0501 -> "GL_INVALID_VALUE"
        0x0502 -> "GL_INVALID_OPERATION"
        0x0503 -> "GL_STACK_OVERFLOW"
        0x0504 -> "GL_STACK_UNDERFLOW"
        0x0505 -> "GL_OUT_OF_MEMORY"
        0x0506 -> "GL_INVALID_FRAMEBUFFER_OPERATION"
        0x0507 -> "GL_CONTEXT_LOST"
        else -> "GL error ${error.toShortHex()}"
    })
}