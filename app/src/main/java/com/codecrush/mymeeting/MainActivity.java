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

public class MainActivity extends AppCompatActivity {
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
        setContentView(R.layout.activity_main);

        frontCameraTexture = findViewById(R.id.frontCameraTexture);
        backCameraTexture = findViewById(R.id.backCameraTexture);

        initializeAgoraEngine();
        setupFrontCamera();
        setupBackCamera();
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
                    Toast.makeText(MainActivity.this, "User Joined to Channel", Toast.LENGTH_SHORT).show();

                }

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    super.onUserJoined(uid, elapsed);
                    Toast.makeText(MainActivity.this, "User Joined", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    super.onUserOffline(uid, reason);
                    Toast.makeText(MainActivity.this, "User Offline", Toast.LENGTH_SHORT).show();
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
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
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
                                        } catch (CameraAccessException e) {
                                            Log.e(TAG, "Failed to start preview", e);
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                        Log.e(TAG, "Camera session configuration failed.");
                                    }
                                }, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Failed to configure camera", e);
                            }
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.e(TAG, "Camera error: " + error);
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
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
                    byte[] combinedFrame = combineFrames(frontFrame, backFrame, frontCameraTexture.getWidth(), frontCameraTexture.getHeight());
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
        byte[] nv21 = new byte[width * height * 3 / 2];
        // Conversion logic (ARGB to NV21)
        // Add your implementation here
        return nv21;
    }

    private byte[] combineFrames(byte[] frontFrame, byte[] backFrame, int width, int height) {
        int frameSize = width * height;
        byte[] combined = new byte[frameSize * 3 / 2];

        // Combine Y plane
        System.arraycopy(frontFrame, 0, combined, 0, frameSize / 2);
        System.arraycopy(backFrame, 0, combined, frameSize / 2, frameSize / 2);

        // Combine UV planes
        System.arraycopy(frontFrame, frameSize, combined, frameSize, frameSize / 4);
        System.arraycopy(backFrame, frameSize, combined, frameSize + frameSize / 4, frameSize / 4);

        return combined;
    }

    private void pushFrameToAgora(byte[] combinedFrame) {
        NV21Buffer buffer = new NV21Buffer(combinedFrame, frontCameraTexture.getWidth(), frontCameraTexture.getHeight() * 2, null);
        long timestamp = agoraEngine.getCurrentMonotonicTimeInMs() * 1_000_000L;
        VideoFrame videoFrame = new VideoFrame(buffer, 0, timestamp);
        agoraEngine.pushExternalVideoFrameById(videoFrame, trackId);
        buffer.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (frontCameraDevice != null) frontCameraDevice.close();
        if (backCameraDevice != null) backCameraDevice.close();
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
