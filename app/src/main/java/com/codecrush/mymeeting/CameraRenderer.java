package com.codecrush.mymeeting;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraRenderer implements GLSurfaceView.Renderer {
    private final Context context;
    private SurfaceTexture frontSurfaceTexture, backSurfaceTexture;
    private int frontTextureId = -1, backTextureId = -1;
    private final float[] mvpMatrix = new float[16];
    private int screenWidth, screenHeight;
    private int shaderProgram;
    private int aPositionHandle, aTexCoordHandle, uTextureHandle;
    private final float[] vertices = {
            // Positions (x, y)      // Texture coordinates (s, t)
            -1.0f,  1.0f,            0.0f, 1.0f,  // Top-left
            1.0f,  1.0f,            1.0f, 1.0f,  // Top-right
            -1.0f, -1.0f,            0.0f, 0.0f,  // Bottom-left
            1.0f, -1.0f,            1.0f, 0.0f   // Bottom-right
    };
    private FloatBuffer vertexBuffer;

    private float[] frontTransformMatrix = new float[16];
    private float[] backTransformMatrix = new float[16];
    private int uTextureMatrixHandle;

    public interface SurfaceTexturesListener {
        void onSurfaceTexturesCreated(SurfaceTexture front, SurfaceTexture back);
    }

    private SurfaceTexturesListener listener;

    public CameraRenderer(Context context, SurfaceTexturesListener listener) {
        this.context = context;
        this.listener = listener;
        Matrix.setIdentityM(mvpMatrix, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Generate OpenGL texture IDs (existing code)
        int[] textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);
        frontTextureId = textures[0];
        backTextureId = textures[1];

        // Load and compile shaders
        String vertexShaderCode = loadShader(context, R.raw.vertex);
        String fragmentShaderCode = loadShader(context, R.raw.fragment);

        // Compile shaders
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        // Link shaders into a program
        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShader);
        GLES20.glAttachShader(shaderProgram, fragmentShader);
        GLES20.glLinkProgram(shaderProgram);

        // Get attribute/uniform locations
        aPositionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        aTexCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord");
        uTextureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture");

        // Prepare vertex buffer
        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        // Initialize SurfaceTextures (existing code)
        frontSurfaceTexture = new SurfaceTexture(frontTextureId);
        backSurfaceTexture = new SurfaceTexture(backTextureId);
        listener.onSurfaceTexturesCreated(frontSurfaceTexture, backSurfaceTexture);

        uTextureMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uTextureMatrix");
    }

    private int compileShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private String loadShader(Context context, int resourceId) {
        try (InputStream is = context.getResources().openRawResource(resourceId)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Could not load shader: " + resourceId, e);
        }
    }
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screenWidth = width;
        screenHeight = height;
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Update texture images and get transformation matrices
        if (frontSurfaceTexture != null) {
            frontSurfaceTexture.updateTexImage();
            frontSurfaceTexture.getTransformMatrix(frontTransformMatrix);
        }
        if (backSurfaceTexture != null) {
            backSurfaceTexture.updateTexImage();
            backSurfaceTexture.getTransformMatrix(backTransformMatrix);
        }

        // Draw front camera (top half) with its matrix
        GLES20.glViewport(0, screenHeight / 2, screenWidth, screenHeight / 2);
        drawTexture(frontTextureId, frontTransformMatrix);

        // Draw back camera (bottom half) with its matrix
        GLES20.glViewport(0, 0, screenWidth, screenHeight / 2);
        drawTexture(backTextureId, backTransformMatrix);
    }

    private void drawTexture(int textureId, float[] transformMatrix) {
        GLES20.glUseProgram(shaderProgram);

        // Pass the transformation matrix to the shader
        GLES20.glUniformMatrix4fv(uTextureMatrixHandle, 1, false, transformMatrix, 0);

        // Bind texture and draw (existing code)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(uTextureHandle, 0);

        // Render the quad
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aPositionHandle);

        vertexBuffer.position(2);
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aTexCoordHandle);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPositionHandle);
        GLES20.glDisableVertexAttribArray(aTexCoordHandle);
    }
}
