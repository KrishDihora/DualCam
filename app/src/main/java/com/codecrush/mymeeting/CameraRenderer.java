package com.codecrush.mymeeting;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

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
    private SurfaceTexturesListener listener;
    private ByteBuffer pixelBuffer;
    private OnFrameListener frameListener;
    private int[] pboIds = new int[2]; // Two PBOs
    private int currentPboIndex = 0;
    private ByteBuffer[] mappedBuffers = new ByteBuffer[2];
    private boolean pbosInitialized = false;

    public CameraRenderer(Context context, SurfaceTexturesListener listener) {
        this.context = context;
        this.listener = listener;
        Matrix.setIdentityM(mvpMatrix, 0);
    }

    public interface SurfaceTexturesListener {

        void onSurfaceTexturesCreated(SurfaceTexture front, SurfaceTexture back);
    }

    public interface OnFrameListener {
        void onFrameAvailable(byte[] rgbaPixels);
    }

    public void setOnFrameListener(OnFrameListener listener) {
        this.frameListener = listener;
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
        shaderProgram = GLES30.glCreateProgram();
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

        // Initialize PBOs
        GLES30.glGenBuffers(2, pboIds, 0);
        pbosInitialized = true; // Mark PBOs as initialized

        GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
    }


    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screenWidth = width;
        screenHeight = height;
        GLES20.glViewport(0, 0, width, height);

        if (pbosInitialized) {
            GLES30.glDeleteBuffers(2, pboIds, 0); // Delete old PBOs
        }
        GLES30.glGenBuffers(2, pboIds, 0);
        for (int i = 0; i < 2; i++) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[i]);
            GLES30.glBufferData(
                    GLES30.GL_PIXEL_PACK_BUFFER,
                    screenWidth * screenHeight * 4, // Use updated dimensions
                    null,
                    GLES30.GL_STREAM_READ
            );
        }
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        pbosInitialized = true;
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
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

        //captureCombinedFrame();

        // Capture using PBOs
        captureWithPBO();

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

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void captureWithPBO() {
        int pboId = pboIds[currentPboIndex];
        int nextPboIndex = (currentPboIndex + 1) % 2;

        // Log PBO IDs and screen dimensions
        Log.d("PBO", "Current PBO ID: " + pboId + ", Next PBO ID: " + pboIds[nextPboIndex]);
        Log.d("PBO", "Screen size: " + screenWidth + "x" + screenHeight);

        // Bind PBO to read pixels into
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboId);
        GLES30.glReadPixels(
                0, 0, screenWidth, screenHeight,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0
        );

        // Map the previous PBO to CPU memory
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[nextPboIndex]);
        ByteBuffer buffer = (ByteBuffer) GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_PACK_BUFFER,
                0,
                screenWidth * screenHeight * 4,
                GLES30.GL_MAP_READ_BIT
        );

        if (buffer == null) {
            Log.e("PBO", "Failed to map PBO buffer!");
            int error = GLES30.glGetError();
            Log.e("PBO", "OpenGL error: " + error); // Check for GL errors
        }

        // Process the buffer
        if (buffer != null && frameListener != null)
        {
            Log.d("PBO", "Buffer size: " + buffer.remaining());
            
            byte[] rgbaBytes = new byte[buffer.remaining()];
            buffer.get(rgbaBytes);
            GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);

            // Offload conversion to a background thread
            new Thread(() -> {
                byte[] nv21Bytes = YUVConverter.rgbaToNV21(rgbaBytes, screenWidth, screenHeight);
                frameListener.onFrameAvailable(nv21Bytes);
            }).start();
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        currentPboIndex = nextPboIndex;
    }

    /*private void captureWithPBO() {
        int pboId = pboIds[currentPboIndex];
        int nextPboIndex = (currentPboIndex + 1) % 2;

        // Bind PBO to read pixels into
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboId);
        GLES30.glReadPixels(
                0, 0, screenWidth, screenHeight,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0
        );

        // Map the previous PBO to CPU memory
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[nextPboIndex]);
        ByteBuffer buffer = (ByteBuffer) GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_PACK_BUFFER,
                0,
                screenWidth * screenHeight * 4,
                GLES30.GL_MAP_READ_BIT
        );

        if (buffer != null && frameListener != null) {
            byte[] rgbaPixels = new byte[buffer.remaining()];
            buffer.get(rgbaPixels);
            frameListener.onFrameAvailable(rgbaPixels); // Pass RGBA bytes
            GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        currentPboIndex = nextPboIndex;
    }*/

    // Add this method
    public void cleanup() {
        if (pbosInitialized) {
            GLES30.glDeleteBuffers(2, pboIds, 0);
            pbosInitialized = false;
        }
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    /*private void captureCombinedFrame() {
        if (frameListener == null || screenWidth == 0 || screenHeight == 0) return;

        if (pixelBuffer == null) {
            int bufferSize = screenWidth * screenHeight * 4; // RGBA
            pixelBuffer = ByteBuffer.allocateDirect(bufferSize);
        }

        // Read pixels from OpenGL framebuffer
        GLES20.glReadPixels(
                0, 0, screenWidth, screenHeight,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer
        );

        // 2. Copy to a byte array (ARGB8888 format)
        byte[] argbBytes = new byte[screenWidth * screenHeight * 4];
        pixelBuffer.get(argbBytes);

        // 3. Convert to NV21
        byte[] nv21Bytes = new byte[screenWidth * screenHeight * 3 / 2]; // NV21 size
        NV21Converter.convertARGBToNV21(argbBytes, screenWidth, screenHeight, nv21Bytes);

        // 4. Pass NV21 bytes to listener
        frameListener.onFrameAvailable(nv21Bytes);

    }

    private Bitmap flipBitmapVertically(Bitmap bitmap) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postScale(1, -1); // Flip vertically
        return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
        );
    }*/

}
