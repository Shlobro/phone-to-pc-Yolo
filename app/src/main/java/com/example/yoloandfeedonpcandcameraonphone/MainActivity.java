package com.example.yoloandfeedonpcandcameraonphone;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements NetworkClient.NetworkCallback {
    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    
    private PreviewView previewView;
    private DetectionOverlayView overlayView;
    private TextView connectionStatus, fpsCounter, detectionCount, latencyInfo;
    private Button connectButton;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private NetworkClient networkClient;
    private long frameCounter = 0;
    private long lastFpsTime = System.currentTimeMillis();
    private int framesSinceLastFps = 0;
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initNetworking();
        
        uiHandler = new Handler(Looper.getMainLooper());
        
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.detectionOverlay);
        connectionStatus = findViewById(R.id.connectionStatus);
        fpsCounter = findViewById(R.id.fpsCounter);
        detectionCount = findViewById(R.id.detectionCount);
        latencyInfo = findViewById(R.id.latencyInfo);
        connectButton = findViewById(R.id.connectButton);
        
        connectButton.setOnClickListener(v -> {
            if (networkClient != null) {
                connectionStatus.setText("Discovering server...");
                connectButton.setEnabled(false);
                networkClient.discoverServer();
            }
        });
    }

    private void initNetworking() {
        networkClient = new NetworkClient(this);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::processImage);
        
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void processImage(ImageProxy image) {
        frameCounter++;
        framesSinceLastFps++;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFpsTime >= 1000) {
            float fps = framesSinceLastFps / ((currentTime - lastFpsTime) / 1000f);
            uiHandler.post(() -> fpsCounter.setText("FPS: " + String.format("%.1f", fps)));
            
            framesSinceLastFps = 0;
            lastFpsTime = currentTime;
        }
        
        if (networkClient != null && frameCounter % 3 == 0) {
            byte[] jpegData = imageProxyToJpeg(image);
            if (jpegData != null) {
                networkClient.sendFrame(jpegData, frameCounter, image.getWidth(), image.getHeight());
            }
        }
        
        image.close();
    }

    private byte[] imageProxyToJpeg(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, 
                image.getWidth(), image.getHeight(), null);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, 
                image.getWidth(), image.getHeight()), 80, out);
            
            return out.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error converting image", e);
            return null;
        }
    }

    @Override
    public void onServerDiscovered(String serverIP) {
        connectionStatus.setText("Server found: " + serverIP);
        networkClient.connect(serverIP);
    }

    @Override
    public void onConnected() {
        connectionStatus.setText("Connected");
        connectButton.setText("Disconnect");
        connectButton.setEnabled(true);
        connectButton.setOnClickListener(v -> {
            networkClient.disconnect();
            connectButton.setText("Connect to PC");
            connectButton.setOnClickListener(view -> {
                connectionStatus.setText("Discovering server...");
                connectButton.setEnabled(false);
                networkClient.discoverServer();
            });
        });
    }

    @Override
    public void onDisconnected() {
        connectionStatus.setText("Disconnected");
        connectButton.setText("Connect to PC");
        connectButton.setEnabled(true);
    }

    @Override
    public void onDetectionsReceived(YFPMessage.DetectionsData detections) {
        Log.d(TAG, "DETECTION_DEBUG: onDetectionsReceived called");
        List<DetectionOverlayView.DetectionBox> boxes = new ArrayList<>();

        if (detections != null) {
            Log.d(TAG, "DETECTION_DEBUG: DetectionsData is not null, frame_id: " + detections.frameId);
            if (detections.detections != null) {
                Log.d(TAG, "DETECTION_DEBUG: detections array not null, length: " + detections.detections.length);
                for (int i = 0; i < detections.detections.length; i++) {
                    YFPMessage.Detection detection = detections.detections[i];
                    Log.d(TAG, "DETECTION_DEBUG: Processing detection " + i + ": " + detection.className +
                          " at (" + detection.x + "," + detection.y + "," + detection.width + "," + detection.height +
                          ") conf=" + detection.confidence);

                    // Validate coordinates
                    if (detection.x < 0 || detection.x > 1 || detection.y < 0 || detection.y > 1 ||
                        detection.width < 0 || detection.width > 1 || detection.height < 0 || detection.height > 1) {
                        Log.w(TAG, "DETECTION_DEBUG: Invalid coordinates for detection " + i +
                              ": values should be between 0.0 and 1.0");
                    }

                    if (detection.x + detection.width > 1 || detection.y + detection.height > 1) {
                        Log.w(TAG, "DETECTION_DEBUG: Detection " + i + " extends beyond image bounds");
                    }

                    DetectionOverlayView.DetectionBox box = new DetectionOverlayView.DetectionBox(
                        detection.x, detection.y, detection.width, detection.height,
                        detection.className, detection.confidence);
                    boxes.add(box);

                    Log.d(TAG, "DETECTION_DEBUG: Successfully created DetectionBox " + i);
                }
            } else {
                Log.w(TAG, "DETECTION_DEBUG: detections.detections array is null");
            }
        } else {
            Log.w(TAG, "DETECTION_DEBUG: DetectionsData is null");
        }

        Log.d(TAG, "DETECTION_DEBUG: Setting " + boxes.size() + " detection boxes on overlay");
        Log.d(TAG, "DETECTION_DEBUG: About to call overlayView.setDetections()");

        uiHandler.post(() -> {
            overlayView.setDetections(boxes);
            detectionCount.setText("Detections: " + boxes.size());
            Log.d(TAG, "DETECTION_DEBUG: Updated UI with detection count: " + boxes.size());
        });
    }

    @Override
    public void onMetricsReceived(YFPMessage.MetricsData metrics) {
        latencyInfo.setText("Latency: " + metrics.networkLatencyMs + "ms");
    }

    @Override
    public void onError(String error) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        connectionStatus.setText("Error: " + error);
        connectButton.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkClient != null) {
            networkClient.shutdown();
        }
    }
}