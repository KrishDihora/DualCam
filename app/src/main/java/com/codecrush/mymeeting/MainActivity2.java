package com.codecrush.mymeeting;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;

import io.agora.base.NV21Buffer;
import io.agora.base.VideoFrame;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;

public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextureView frontCameraTexture;
    private TextureView backCameraTexture;
    private CameraDevice frontCameraDevice;
    private CameraDevice backCameraDevice;
    private Handler handler = new Handler();
    private RtcEngine agoraEngine;
    private int trackId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        frontCameraTexture = findViewById(R.id.frontCameraTexture);
        backCameraTexture = findViewById(R.id.backCameraTexture);

        initializeAgoraEngine();
        setupFrontCamera();
        /*handler.postDelayed(new Runnable() {
            @Override
            public void run()
            {
                setupBackCamera();
            }
        },5000);*/

        startStreaming();
    }

    private void initializeAgoraEngine() {
        try {
            agoraEngine = RtcEngine.create(getApplicationContext(), "8f0a7c3a1dc94719848272d461c4d78d", new IRtcEngineEventHandler()
            {
                @Override
                public void onJoinChannelSuccess(String channel, int uid, int elapsed)
                {
                    super.onJoinChannelSuccess(channel, uid, elapsed);
                    Toast.makeText(MainActivity2.this, "User Joined to Channel", Toast.LENGTH_SHORT).show();

                }

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    super.onUserJoined(uid, elapsed);
                    Toast.makeText(MainActivity2.this, "User Joined", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    super.onUserOffline(uid, reason);
                    Toast.makeText(MainActivity2.this, "User Offline", Toast.LENGTH_SHORT).show();
                }
            });

            trackId = agoraEngine.createCustomVideoTrack();

            ChannelMediaOptions option = new ChannelMediaOptions();
            option.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            option.autoSubscribeAudio = true;
            option.autoSubscribeVideo = true;
            option.publishCustomVideoTrack = true;
            option.customVideoTrackId = trackId;

            agoraEngine.setExternalVideoSource(true, true, Constants.ExternalVideoSourceType.VIDEO_FRAME);
            agoraEngine.joinChannel("007eJxTYDitHzSlvP3gJvGzIj4P/VSurvSwnHk6+m3w6a0CJ74+VPuqwGCRZpBonmycaJiSbGlibmhpYWJhZG6UYmJmmGySYm6Rsl50UnpDICNDbHE1MyMDBIL4LAwlqcUlDAwAowogVg==", "test", 0, option);

        } catch (Exception e) {
            Log.e(TAG, "Agora initialization failed", e);
        }
    }

    private void setupFrontCamera() {
        openCamera(CameraCharacteristics.LENS_FACING_FRONT, frontCameraTexture, camera -> frontCameraDevice = camera);
    }

    private void setupBackCamera() {
        openCamera(CameraCharacteristics.LENS_FACING_BACK, backCameraTexture, camera -> backCameraDevice = camera);
    }

    private void openCamera(int lensFacing, TextureView textureView, CameraCallback callback) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            String cameraId = getCameraId(cameraManager, lensFacing);
            if (cameraId != null) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 1);
                    return;
                }
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback(){
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        callback.onOpened(camera);

                        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                        if (surfaceTexture != null) {
                            surfaceTexture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
                            Surface surface = new Surface(surfaceTexture);

                            try {
                                CaptureRequest.Builder captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                captureRequestBuilder.addTarget(surface);

                                camera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession session) {
                                        try {
                                            session.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                                        }
                                        catch (CameraAccessException e)
                                        {
                                            Toast.makeText(MainActivity2.this, "Failed to start preview", Toast.LENGTH_SHORT).show();
                                            Log.e(TAG, "Failed to start preview", e);
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session)
                                    {
                                        Toast.makeText(MainActivity2.this, "Camera session configuration failed.", Toast.LENGTH_SHORT).show();
                                        Log.e(TAG, "Camera session configuration failed.");
                                    }
                                }, null);
                            } catch (CameraAccessException e)
                            {
                                Toast.makeText(MainActivity2.this, "Failed to configure camera", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Failed to configure camera", e);
                            }
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error)
                    {
                        Toast.makeText(MainActivity2.this, "Camera error: " + error, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Camera error: " + error);
                    }
                }, null);
            }
        } catch (CameraAccessException e)
        {
            Toast.makeText(MainActivity2.this, "Failed to access camera", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to access camera", e);
        }
    }

    private String getCameraId(CameraManager cameraManager, int lensFacing) throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == lensFacing) {
                return cameraId;
            }
        }
        return null;
    }

    private void startStreaming() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                byte[] frontFrame = getNV21FromTexture(frontCameraTexture);
                byte[] backFrame = getNV21FromTexture(backCameraTexture);

                if (frontFrame != null && backFrame != null) {
                    byte[] combinedFrame = combineFrames(frontFrame, backFrame, frontCameraTexture.getWidth(), frontCameraTexture.getHeight()*2);
                    pushFrameToAgora(combinedFrame);
                }

                handler.postDelayed(this, 33); // Run at ~30 FPS
            }
        }, 33);
    }

    private byte[] getNV21FromTexture(TextureView textureView) {
        Bitmap bitmap = textureView.getBitmap();
        if (bitmap == null) return null;

        // Convert Bitmap to NV21
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);
        return convertToNV21(argb, width, height);
    }

    private byte[] convertToNV21(int[] argb, int width, int height) {
        int frameSize = width * height;
        byte[] nv21 = new byte[frameSize * 3 / 2];

        int yIndex = 0;      // Index for Y values
        int uvIndex = frameSize; // Start of UV plane

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int argbPixel = argb[j * width + i];

                // Extract ARGB components
                int r = (argbPixel >> 16) & 0xFF; // Red
                int g = (argbPixel >> 8) & 0xFF;  // Green
                int b = argbPixel & 0xFF;         // Blue

                // Calculate Y component
                int y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                y = y < 0 ? 0 : Math.min(y, 255);

                // Calculate U and V components
                int u = (int) (-0.14713 * r - 0.28886 * g + 0.436 * b + 128);
                int v = (int) (0.615 * r - 0.51498 * g - 0.10001 * b + 128);
                u = u < 0 ? 0 : Math.min(u, 255);
                v = v < 0 ? 0 : Math.min(v, 255);

                // Assign Y value
                nv21[yIndex++] = (byte) y;

                // Assign UV values (4:2:0 subsampling)
                // UV values are written only for every 2x2 block
                if (j % 2 == 0 && i % 2 == 0 && uvIndex < nv21.length - 1) {
                    nv21[uvIndex++] = (byte) v; // V plane
                    nv21[uvIndex++] = (byte) u; // U plane
                }
            }
        }

        return nv21;
    }



    private byte[] combineFrames(byte[] frontFrame, byte[] backFrame, int width, int height) {
        int frameSizePerCamera = width * height / 2; // Each camera provides half the height
        int totalFrameSize = width * height;        // Combined frame size for Y plane
        byte[] combined = new byte[totalFrameSize * 3 / 2];

        // Combine Y plane
        System.arraycopy(frontFrame, 0, combined, 0, frameSizePerCamera); // Top half from front camera
        System.arraycopy(backFrame, 0, combined, frameSizePerCamera, frameSizePerCamera); // Bottom half from back camera

        // Combine UV planes
        int uvSizePerCamera = frameSizePerCamera / 2;
        System.arraycopy(frontFrame, frameSizePerCamera, combined, totalFrameSize, uvSizePerCamera);
        System.arraycopy(backFrame, frameSizePerCamera, combined, totalFrameSize + uvSizePerCamera, uvSizePerCamera);

        return combined;
    }


    private void pushFrameToAgora(byte[] combinedFrame) {
        NV21Buffer buffer = new NV21Buffer(combinedFrame, frontCameraTexture.getWidth(), frontCameraTexture.getHeight(),null);
        long timestamp = agoraEngine.getCurrentMonotonicTimeInMs();
        VideoFrame videoFrame = new VideoFrame(buffer, 0, timestamp * 1000000);
        agoraEngine.pushExternalVideoFrameById(videoFrame, trackId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (frontCameraDevice != null)
            frontCameraDevice.close();
        if (backCameraDevice != null)
            backCameraDevice.close();
        if (agoraEngine != null)
        {
            agoraEngine.destroyCustomAudioTrack(trackId);
            agoraEngine.leaveChannel();
        }
    }

    private interface CameraCallback {
        void onOpened(CameraDevice camera);
    }
}
