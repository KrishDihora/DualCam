package com.codecrush.mymeeting;

import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import io.agora.base.NV21Buffer;
import io.agora.base.VideoFrame;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;

public class MainActivity extends AppCompatActivity
        implements CameraRenderer.SurfaceTexturesListener,CameraRenderer.OnFrameListener {

    private GLSurfaceView glSurfaceView;
    private CameraDevice cameraDeviceBack, cameraDeviceFront;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private SurfaceTexture frontSurfaceTexture, backSurfaceTexture;
    private CameraRenderer renderer;
    private RtcEngine agoraEngine;
    private int trackId;
    private Handler handler = new Handler();
    private Integer height,width;
    private LinkedBlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>();
    private volatile boolean isStreaming = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.glSurfaceView);

        renderer = new CameraRenderer(this, this);
        glSurfaceView.setEGLContextClientVersion(3);
        renderer.setOnFrameListener(this); // Set listener
        glSurfaceView.setRenderer(renderer);
        width = renderer.getScreenWidth();
        height = renderer.getScreenHeight();


        initializeAgoraEngine();

        handler.postDelayed(new Runnable() {
            @Override
            public void run()
            {
                startStreaming();
            }
        },5000);

        
    }



    @Override
    public void onFrameAvailable(byte[] nv21Bytes)
    {
            if (frameQueue.size() >= 5)
            {
                frameQueue.poll();
            }
            // Add the frame to the queue (drop old frames if the queue is full)
            frameQueue.add(nv21Bytes);
    }

    @Override
    public void onSurfaceTexturesCreated(SurfaceTexture front, SurfaceTexture back) {
        this.frontSurfaceTexture = front;
        this.backSurfaceTexture = back;
        startBackgroundThread();
        openCameras();
    }

    private void openCameras() {
        openCamera(CameraCharacteristics.LENS_FACING_BACK, backSurfaceTexture);
        openCamera(CameraCharacteristics.LENS_FACING_FRONT, frontSurfaceTexture);
    }

    private void openCamera(int lensFacing, SurfaceTexture surfaceTexture) {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = getCameraId(manager, lensFacing);
            if (cameraId == null) return;

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }

            surfaceTexture.setDefaultBufferSize(640, 480); // Match camera resolution
            Surface surface = new Surface(surfaceTexture);

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraDeviceBack = camera;
                    } else {
                        cameraDeviceFront = camera;
                    }
                    createPreviewSession(camera, surface);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private String getCameraId(CameraManager manager, int lensFacing) throws CameraAccessException
    {
        for (String cameraId : manager.getCameraIdList())
        {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing)
            {
                return cameraId;
            }
        }
        return null;
    }

    private void createPreviewSession(CameraDevice camera, Surface surface) {
        try {
            CaptureRequest.Builder requestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(surface);

            camera.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                session.setRepeatingRequest(
                                        requestBuilder.build(), null, backgroundHandler
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initializeAgoraEngine() {
        try {
            agoraEngine = RtcEngine.create(getApplicationContext(), "8f0a7c3a1dc94719848272d461c4d78d", new IRtcEngineEventHandler()
            {
                @Override
                public void onJoinChannelSuccess(String channel, int uid, int elapsed)
                {
                    super.onJoinChannelSuccess(channel, uid, elapsed);
                    runOnUiThread(()->{Toast.makeText(MainActivity.this, "User Joined to Channel", Toast.LENGTH_SHORT).show();});

                }

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    super.onUserJoined(uid, elapsed);
                    runOnUiThread(()->{Toast.makeText(MainActivity.this, "User Joined", Toast.LENGTH_SHORT).show();});
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    super.onUserOffline(uid, reason);
                    runOnUiThread(()->{Toast.makeText(MainActivity.this, "User Offline", Toast.LENGTH_SHORT).show();});
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
            agoraEngine.joinChannel("007eJxTYLh8jSODaZdc3zE1xpsa0pwCU4rTjxU1f57+QPfBaaOPzVsUGCzSDBLNk40TDVOSLU3MDS0tTCyMzI1STMwMk01SzC1SlBgXpzcEMjKwL//LyMgAgSA+C0NJanEJAwMA/xkeXQ==", "test", 0, option);

        } catch (Exception e) {
            Log.e("TAG", "Agora initialization failed", e);
            Toast.makeText(MainActivity.this,"initializeAgoraEngine Catch",Toast.LENGTH_SHORT).show();
        }
    }

    private void pushFrameToAgora(byte[] combinedFrame, int width, int height) {
        NV21Buffer buffer = new NV21Buffer(combinedFrame, width, height,null);
        long timestamp = agoraEngine.getCurrentMonotonicTimeInMs();
        VideoFrame videoFrame = new VideoFrame(buffer, 0, timestamp * 1000000);
        agoraEngine.pushExternalVideoFrameById(videoFrame, trackId);
    }

    private void startStreaming()
    {
        isStreaming = true;
        new Thread(() -> {
            while (isStreaming)
            {

                    // Wait for the next frame (blocks if queue is empty)
                    byte[] nv21Frame = frameQueue.poll();

                    if (nv21Frame != null)
                    {
                        // Process the frame (e.g., send over network)
                        pushFrameToAgora(nv21Frame,width,height);
                    }
                    else
                    {
                        // No frame available (optional: add a small delay)
                        Thread.yield();
                    }


            }
        }).start();
    }

    public void stopStreaming() {
        isStreaming = false;
    }


    // Include remaining code from previous steps (getCameraId, background thread, etc.)
    @Override
    protected void onResume()
    {
        super.onResume();
        startBackgroundThread();
        startStreaming();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        closeCamera();
        stopBackgroundThread();
        glSurfaceView.onPause(); // Stop the rendering thread
        cleanupOpenGLResources();
        stopStreaming();

    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        closeCamera();
        stopBackgroundThread();
        cleanupOpenGLResources();
        startStreaming();

        if (agoraEngine != null)
        {
            agoraEngine.destroyCustomAudioTrack(trackId);
            agoraEngine.leaveChannel();
        }
    }

    private void startBackgroundThread()
    {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread()
    {
        if (backgroundThread != null)
        {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void closeCamera()
    {
        if (cameraDeviceBack != null)
        {
            cameraDeviceBack.close();
            cameraDeviceBack = null;
        }
        if (cameraDeviceFront != null)
        {
            cameraDeviceFront.close();
            cameraDeviceFront = null;
        }
    }

    private void cleanupOpenGLResources() {
        // Queue the cleanup on the OpenGL thread
        glSurfaceView.queueEvent(() -> {
            if (renderer != null) {
                renderer.cleanup();
            }
        });
    }

}