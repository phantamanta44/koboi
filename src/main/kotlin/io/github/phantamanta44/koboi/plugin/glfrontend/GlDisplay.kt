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
import kotlin.properties.Delegates

const val GL_POSITION: Int = 0
const val GL_COLOUR: Int = 3

const val GL_VERTEX_SHADER: Int = 35633
const val GL_FRAGMENT_SHADER: Int = 35632

const val VERTEX_COUNT = 160 * 144 * 5

class GlDisplay : GLEventListener, IDisplay {

    private val vertexBuffer: FloatBuffer = GLBuffers.newDirectFloatBuffer(VERTEX_COUNT)

    private var window: GLWindow by Delegates.notNull()
    private val animator: FPSAnimator = FPSAnimator(60, true)

    private val vboHandle: IntBuffer = GLBuffers.newDirectIntBuffer(1)
    private val vaoHandle: IntBuffer = GLBuffers.newDirectIntBuffer(1)

    private var shaderProgram: Int by Delegates.notNull()

    init {
        for (y in 0..143) {
            for (x in 0..159) {
                vertexBuffer.put((160 * y + x) * 5, x.toFloat())
                vertexBuffer.put((160 * y + x) * 5 + 1, y.toFloat())
            }
        }
    }

    override fun init(drawable: GLAutoDrawable) {
        with(window.gl.gL3) {
            // init vertex vbo
            glGenBuffers(1, vboHandle)
            glBindBuffer(GL.GL_ARRAY_BUFFER, vboHandle[0])

            // init vao
            glGenVertexArrays(1, vaoHandle)
            glBindVertexArray(vaoHandle[0])
            glEnableVertexAttribArray(GL_POSITION)
            glVertexAttribPointer(GL_POSITION, 2, GL.GL_FLOAT, false, 5, 0)
            glEnableVertexAttribArray(GL_COLOUR)
            glVertexAttribPointer(GL_COLOUR, 3, GL.GL_FLOAT, false, 5, 2)
//            glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
//            glBindVertexArray(0)

            // init shaders
            val vertShader = glCreateShader(GL_VERTEX_SHADER)
            val vertShaderSrc = javaClass.getResource("/shader/shader.vert").readText()
            glShaderSource(vertShader, 1, arrayOf(vertShaderSrc), intArrayOf(vertShaderSrc.length), 0)
            glCompileShader(vertShader)
            val fragShader = glCreateShader(GL_FRAGMENT_SHADER)
            val fragShaderSrc = javaClass.getResource("/shader/shader.frag").readText()
            glShaderSource(fragShader, 1, arrayOf(fragShaderSrc), intArrayOf(fragShaderSrc.length), 0)
            glCompileShader(fragShader)
            shaderProgram = glCreateProgram()
            glAttachShader(shaderProgram, vertShader)
            glAttachShader(shaderProgram, fragShader)
            glLinkProgram(shaderProgram)

            // set up gl state
            glUseProgram(shaderProgram)
            glEnable(GL.GL_DEPTH_TEST)
        }
    }

    override fun dispose(drawable: GLAutoDrawable) {
        with(drawable.gl.gL3) {
            glUseProgram(0)
            glDeleteProgram(shaderProgram)
            glBindVertexArray(0)
            glDeleteVertexArrays(1, vaoHandle)
            glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
            glDeleteBuffers(1, vboHandle)
            // TODO dispose of direct-allocated buffers
        }
    }

    override fun kill() {
        window.destroy()
    }

    override fun show(deathCallback: () -> Unit) {
        window = GLWindow.create(GLCapabilities(GLProfile.get(GLProfile.GL3)))
        window.title = "Koboi"
        window.setSize(160, 144)
        window.addWindowListener(object : WindowAdapter() {
            override fun windowDestroyNotify(e: WindowEvent) {
                if (animator.isStarted) animator.stop()
                deathCallback()
            }
        })
        window.addGLEventListener(this)
        window.isVisible = true
        window.setPosition(window.screen.width / 2 - window.width / 2, window.screen.height / 2 - window.height / 2)
        animator.add(window)
        animator.start()
    }

    override fun display(drawable: GLAutoDrawable) {
        with(drawable.gl.gL3) {
            glClear(GL.GL_COLOR_BUFFER_BIT)
            glBufferData(GL.GL_ARRAY_BUFFER, VERTEX_COUNT * 4L, vertexBuffer, GL.GL_DYNAMIC_DRAW)
            glDrawArrays(GL.GL_POINTS, 0, VERTEX_COUNT)
        }
    }

    override fun writePixel(x: Int, y: Int, colour: Int) {
        vertexBuffer.put((160 * y + x) * 5 + 2, ((colour and 0xFF0000) ushr 16) / 255F)
        vertexBuffer.put((160 * y + x) * 5 + 3, ((colour and 0x00FF00) ushr 8) / 255F)
        vertexBuffer.put((160 * y + x) * 5 + 4, (colour and 0x0000FF) / 255F)
    }

    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) {
        drawable.gl.gL3.glViewport(0, 0, width, height)
    }

}