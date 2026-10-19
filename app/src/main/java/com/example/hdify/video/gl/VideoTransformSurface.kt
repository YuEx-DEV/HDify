package com.example.hdify.video.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import com.example.hdify.logging.HdLogger

class VideoTransformSurface(
    private val encoderInputSurface: Surface,
    private val outWidth: Int,
    private val outHeight: Int,
    private val rotationDegrees: Int,
    private val isMirrored: Boolean,
    private val forceHdr: Boolean = false
) : SurfaceTexture.OnFrameAvailableListener {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var surfaceTexture: SurfaceTexture? = null
    var decoderSurface: Surface? = null
        private set

    private val frameSyncObject = Object()
    private var frameAvailable = false

    private var textureId: Int = 0
    private var program: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uTexMatrixHandle: Int = 0
    private var uForceHdrHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var aTextureCoordHandle: Int = 0

    private val mvpMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)

    // Standard quad: [-1, 1] position, [0, 1] texCoord
    private val triangleVertices = GlUtil.createFloatBuffer(
        floatArrayOf(
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 0.0f, 1.0f, 1.0f
        )
    )

    init {
        setupEgl()
        makeCurrent()
        setupGl()
        setupTransformMatrix()
    }

    private fun setupEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val res = EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        if (!res || numConfigs[0] == 0 || configs[0] == null) {
            throw RuntimeException("Unable to find suitable EGLConfig")
        }
        val chosenConfig = configs[0]

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, chosenConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("eglCreateContext failed")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, chosenConfig, encoderInputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed")
        }
    }

    private fun setupGl() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(outWidth, outHeight)
            setOnFrameAvailableListener(this@VideoTransformSurface)
        }
        decoderSurface = Surface(surfaceTexture)

        val vertexShader = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        val fragmentShader = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform int uForceHdr;

            // High-quality pseudo-random hash for spatial dithering
            float hash(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            void main() {
                vec4 src = texture2D(sTexture, vTextureCoord);
                if (uForceHdr == 1) {
                    vec3 rgb = src.rgb;

                    // 1. Rec.709 perceived luminance
                    float lum = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

                    // 2. Adaptive Shadow Recovery (lifts dark areas, prevents "ireng"/crushed face)
                    float shadowFactor = 1.0 - smoothstep(0.0, 0.50, lum);
                    rgb += vec3(shadowFactor * 0.06);

                    // 3. Gentle Midtone Clarity & Contrast (centered at 0.5, preserves bright skin)
                    rgb = mix(vec3(0.5), rgb, 1.08);

                    // 4. Specular Highlight Pop (glowing lights, reflections, eye shine)
                    float highlight = smoothstep(0.65, 1.0, lum);
                    rgb += rgb * highlight * 0.15;

                    // 5. Smart Color Vibrance (enriches anime colors without making skin orange)
                    float maxC = max(rgb.r, max(rgb.g, rgb.b));
                    float minC = min(rgb.r, min(rgb.g, rgb.b));
                    float sat = (maxC - minC) / max(maxC, 0.001);
                    float vibrance = (1.0 - sat) * 0.28 + 0.06;
                    rgb = mix(vec3(lum), rgb, 1.0 + vibrance);

                    // 6. High-frequency triangular dither to destroy any color banding
                    float dither = (hash(gl_FragCoord.xy) - 0.5) * (1.0 / 255.0);
                    rgb += vec3(dither);

                    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), src.a);
                } else {
                    gl_FragColor = src;
                }
            }
        """.trimIndent()

        program = GlUtil.createProgram(vertexShader, fragmentShader)
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uTexMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uForceHdrHandle = GLES20.glGetUniformLocation(program, "uForceHdr")
    }

    private fun setupTransformMatrix() {
        Matrix.setIdentityM(mvpMatrix, 0)
        // In screen coordinates: mirror horizontally if requested, rotate clockwise
        if (isMirrored) {
            Matrix.scaleM(mvpMatrix, 0, -1f, 1f, 1f)
        }
        if (rotationDegrees != 0) {
            Matrix.rotateM(mvpMatrix, 0, -rotationDegrees.toFloat(), 0f, 0f, 1f)
        }
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        synchronized(frameSyncObject) {
            frameAvailable = true
            frameSyncObject.notifyAll()
        }
    }

    fun drawFrame(ptsNs: Long, timeoutMs: Long = 2500) {
        synchronized(frameSyncObject) {
            while (!frameAvailable) {
                try {
                    frameSyncObject.wait(timeoutMs)
                    if (!frameAvailable) {
                        throw RuntimeException("Surface frame wait timed out after ${timeoutMs}ms")
                    }
                } catch (ie: InterruptedException) {
                    throw RuntimeException(ie)
                }
            }
            frameAvailable = false
        }

        makeCurrent()
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(texMatrix)

        GLES20.glViewport(0, 0, outWidth, outHeight)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        triangleVertices.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, triangleVertices)
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        triangleVertices.position(3)
        GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, triangleVertices)
        GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, texMatrix, 0)
        GLES20.glUniform1i(uForceHdrHandle, if (forceHdr) 1 else 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            makeCurrent()
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        decoderSurface?.release()
        surfaceTexture?.release()

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        decoderSurface = null
        surfaceTexture = null
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
