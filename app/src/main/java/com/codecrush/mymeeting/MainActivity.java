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
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity
        implements CameraRenderer.SurfaceTexturesListener {

    private GLSurfaceView glSurfaceView;
    private CameraDevice cameraDeviceBack, cameraDeviceFront;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private SurfaceTexture frontSurfaceTexture, backSurfaceTexture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.glSurfaceView);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(new CameraRenderer(this, this));
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

    // Include remaining code from previous steps (getCameraId, background thread, etc.)
    @Override
    protected void onResume()
    {
        super.onResume();
        startBackgroundThread();
    }

    @Override
    protected void onPause()
    {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
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
        /*if (cameraCaptureSessionBack != null)
        {
            cameraCaptureSessionBack.close();
            cameraCaptureSessionBack = null;
        }
        if (cameraCaptureSessionFront != null)
        {
            cameraCaptureSessionFront.close();
            cameraCaptureSessionFront = null;
        }*/
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
}