package com.hmd.vr_firefighter_app;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.HandlerThread;
import android.util.Log;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * Based on implemention made by kjw0723
 */
public class GvrRenderer implements GvrView.StereoRenderer {

    private static final String TAG = "GvrRenderer";

    private static final String VERTEX_SHADER =
            "attribute vec4 position;\n" +
            "attribute vec4 in_tex\n;" +
            "varying vec2 out_tex\n;" +
            "uniform mat4 mvpMatrix;\n" +
            "uniform mat4 texMatrix;\n" +
            "void main() {\n" +
            "	gl_Position = mvpMatrix * position;\n" +
            "	out_tex = (texMatrix * in_tex).xy;\n" +
            "}\n";
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 out_tex;\n" +
            "\n" +
            "uniform sampler2D oes_tex;\n" +
            "\n" +
            "void main() {\n" +
            "	gl_FragColor = texture2D(oes_tex, out_tex);\n" +
            "}\n";

    private final GvrView surface;
    private GlShader shader = null;
    private SurfaceTexture surfaceTexture = null;
    private final HandlerThread drawThread = null;

    // Image to be rendered
    public Bitmap bitmapImage = null;
    private int textureId = 0;

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 1000.0f;

    // Position the eye in front of the origin.
    final float eyeX = 0.0f;
    final float eyeY = 0.0f;
    final float eyeZ = 0.0f;

    // We are looking toward the distance
    final float lookX = 0.0f;
    final float lookY = 0.0f;
    final float lookZ = -100.0f;

    // Where head points when holding the camera
    final float upX = 0.0f;
    final float upY = 1.0f;
    final float upZ = 0.0f;

    private final float[] cameraViewMatrix;
    private final float[] viewMatrix;

    private final float[] model;
    private final float[] modelView;
    private final float[] modelViewProjection;

    private final float[] transformMatrix;


    private static final FloatBuffer RECT_VERTICES = GlUtil.createFloatBuffer(new float[] {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    });

    private static final FloatBuffer RECT_TEX_COORDS = GlUtil.createFloatBuffer(new float[] {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    });

    public interface GvrRendererEvents {
        void onSurfaceTextureCreated(SurfaceTexture surfaceTexture);
    }

    GvrRendererEvents events;

    public GvrRenderer(GvrView surface, GvrRendererEvents event) {
        this.surface = surface;
        this.surface.setRenderer(this);

        cameraViewMatrix = new float[16];
        viewMatrix = new float[16];

        model = new float[16];
        modelView = new float[16];
        modelViewProjection = new float[16];
        transformMatrix = new float[16];
        events = event;

        counter = new FPSCounter();
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        surfaceTexture.getTransformMatrix(transformMatrix);
        Matrix.rotateM(transformMatrix, 0, 180, 0, 0, 1);
        Matrix.translateM(transformMatrix, 0, -1, -1, 0);

        counter.logFrame();
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Matrix.multiplyMM(viewMatrix, 0, eye.getEyeView(), 0, cameraViewMatrix, 0);
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

        Matrix.multiplyMM(modelView, 0, viewMatrix, 0, model, 0);
        Matrix.setIdentityM(modelView, 0);
        Matrix.translateM(modelView, 0, 0, 0, -2.0f);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);

        shader.useProgram();
        GLES20.glUniformMatrix4fv(shader.getUniformLocation("mvpMatrix"), 1, false, modelViewProjection, 0);
        GLES20.glUniform1i(shader.getUniformLocation("oes_tex"), 0);
        GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.");

        shader.setVertexAttribArray("position", 2, RECT_VERTICES);
        shader.setVertexAttribArray("in_tex", 2, RECT_TEX_COORDS);

        // Texture transformation
        GLES20.glUniformMatrix4fv(shader.getUniformLocation("texMatrix"), 1, false, transformMatrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11.GL_TEXTURE_2D, textureId);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmapImage, 0);

    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(TAG, "GvrRenderer.onSurfaceChanged");
        surfaceTexture.setDefaultBufferSize(width, height);
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {

        Log.d(TAG, "GvrRenderer.onSurfaceCreated");

        // Create texture
        textureId = GlUtil.createTexture(GLES11.GL_TEXTURE_2D);

        surfaceTexture = new SurfaceTexture(textureId);

        events.onSurfaceTextureCreated(surfaceTexture);

        // Create Shader
        shader = new GlShader(VERTEX_SHADER, FRAGMENT_SHADER);
        shader.useProgram();


        Matrix.setLookAtM(cameraViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0, 0, -2.0f);
    }

    @Override
    public void onRendererShutdown() {
        Log.d(TAG, "GvrRenderer.onRendererShutdown");
        shader.release();
    }

    private FPSCounter counter = null;
    public static class FPSCounter {
        long startTime = System.nanoTime();
        int frames = 0;

        void logFrame() {
            frames++;
            if(System.nanoTime() - startTime >= 1000000000) {
                //Log.d("OPNGL", "fps: " + frames);
                frames = 0;
                startTime = System.nanoTime();
            }
        }
    }
}

