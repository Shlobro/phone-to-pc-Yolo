package com.example.yoloandfeedonpcandcameraonphone;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class YFPMessage {
    public enum MessageType {
        DISCOVER, CONNECT, FRAME, DETECTIONS, METRICS, PING, PONG, ERROR
    }

    @SerializedName("type")
    public MessageType type;
    
    @SerializedName("timestamp")
    public long timestamp;
    
    @SerializedName("data")
    public Object data;

    public YFPMessage(MessageType type, Object data) {
        this.type = type;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static YFPMessage fromJson(String json) {
        return new Gson().fromJson(json, YFPMessage.class);
    }

    public static class DiscoverData {
        @SerializedName("device_name")
        public String deviceName;
        
        @SerializedName("app_version")
        public String appVersion;

        public DiscoverData(String deviceName, String appVersion) {
            this.deviceName = deviceName;
            this.appVersion = appVersion;
        }
    }

    public static class ConnectData {
        @SerializedName("device_id")
        public String deviceId;
        
        @SerializedName("resolution_width")
        public int resolutionWidth;
        
        @SerializedName("resolution_height")
        public int resolutionHeight;

        public ConnectData(String deviceId, int width, int height) {
            this.deviceId = deviceId;
            this.resolutionWidth = width;
            this.resolutionHeight = height;
        }
    }

    public static class FrameData {
        @SerializedName("frame_id")
        public long frameId;
        
        @SerializedName("width")
        public int width;
        
        @SerializedName("height")
        public int height;
        
        @SerializedName("format")
        public String format;
        
        @SerializedName("quality")
        public int quality;

        public FrameData(long frameId, int width, int height, String format, int quality) {
            this.frameId = frameId;
            this.width = width;
            this.height = height;
            this.format = format;
            this.quality = quality;
        }
    }

    public static class DetectionsData {
        @SerializedName("frame_id")
        public long frameId;
        
        @SerializedName("detections")
        public Detection[] detections;
        
        @SerializedName("processing_time_ms")
        public long processingTimeMs;

        public DetectionsData(long frameId, Detection[] detections, long processingTimeMs) {
            this.frameId = frameId;
            this.detections = detections;
            this.processingTimeMs = processingTimeMs;
        }
    }

    public static class Detection {
        @SerializedName("x")
        public float x;
        
        @SerializedName("y")
        public float y;
        
        @SerializedName("width")
        public float width;
        
        @SerializedName("height")
        public float height;
        
        @SerializedName("class_name")
        public String className;
        
        @SerializedName("confidence")
        public float confidence;

        public Detection(float x, float y, float width, float height, String className, float confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.className = className;
            this.confidence = confidence;
        }
    }

    public static class MetricsData {
        @SerializedName("fps")
        public float fps;
        
        @SerializedName("network_latency_ms")
        public long networkLatencyMs;
        
        @SerializedName("detection_time_ms")
        public long detectionTimeMs;
        
        @SerializedName("total_frames_sent")
        public long totalFramesSent;

        public MetricsData(float fps, long networkLatencyMs, long detectionTimeMs, long totalFramesSent) {
            this.fps = fps;
            this.networkLatencyMs = networkLatencyMs;
            this.detectionTimeMs = detectionTimeMs;
            this.totalFramesSent = totalFramesSent;
        }
    }
}