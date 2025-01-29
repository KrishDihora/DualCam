package com.codecrush.mymeeting;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;

public class DualCamHostActivity extends AppCompatActivity {

    private TextureView textureViewBack, textureViewFront;
    private CameraDevice cameraDeviceBack, cameraDeviceFront;
    private CameraCaptureSession cameraCaptureSessionBack, cameraCaptureSessionFront;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        textureViewBack = findViewById(R.id.textureViewBack);
        textureViewFront = findViewById(R.id.textureViewFront);

        textureViewBack.setSurfaceTextureListener(surfaceTextureListenerBack);
        textureViewFront.setSurfaceTextureListener(surfaceTextureListenerFront);
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListenerBack = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height)
        {
            openCamera(CameraCharacteristics.LENS_FACING_BACK, surfaceTexture);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height)
        {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture)
        {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture)
        {

        }
    };

    private final TextureView.SurfaceTextureListener surfaceTextureListenerFront = new TextureView.SurfaceTextureListener()
    {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height)
        {
            openCamera(CameraCharacteristics.LENS_FACING_FRONT, surfaceTexture);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private void openCamera(int lensFacing, SurfaceTexture surfaceTexture)
    {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
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
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice)
                {
                    if (lensFacing == CameraCharacteristics.LENS_FACING_BACK)
                    {
                        cameraDeviceBack = cameraDevice;
                        createCameraPreviewSession(cameraDeviceBack, surfaceTexture);
                    }
                    else
                    {
                        cameraDeviceFront = cameraDevice;
                        createCameraPreviewSession(cameraDeviceFront, surfaceTexture);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice)
                {
                    cameraDevice.close();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error)
                {
                    cameraDevice.close();
                }
            }, backgroundHandler);
        }
        catch (CameraAccessException e)
        {
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

    private void createCameraPreviewSession(CameraDevice cameraDevice, SurfaceTexture surfaceTexture)
    {
        try {
            SurfaceTexture texture = surfaceTexture;
            texture.setDefaultBufferSize(640, 480); // Set preview size
            Surface surface = new Surface(texture);

            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session)
                {
                    if (cameraDevice == null) return;

                    if (cameraDevice == cameraDeviceBack)
                    {
                        cameraCaptureSessionBack = session;
                    }
                    else
                    {
                        cameraCaptureSessionFront = session;
                    }

                    try
                    {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    }
                    catch (CameraAccessException e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session)
                {

                }

            }, backgroundHandler);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

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
        if (cameraCaptureSessionBack != null)
        {
            cameraCaptureSessionBack.close();
            cameraCaptureSessionBack = null;
        }
        if (cameraCaptureSessionFront != null)
        {
            cameraCaptureSessionFront.close();
            cameraCaptureSessionFront = null;
        }
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