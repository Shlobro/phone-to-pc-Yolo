# YOLO Feed: Real-time Object Detection Between Android & PC

This project enables real-time camera feed streaming from an Android phone to a PC, where YOLO object detection is performed. Detection results are sent back to the phone and displayed as overlay rectangles on both devices.

## Features

- **Real-time camera streaming** from Android to PC
- **YOLO object detection** on PC using YOLOv8
- **Dual display** with detection overlays on both Android and PC
- **Performance monitoring** (FPS, latency, detection time)
- **Easy WiFi connection** via PC hotspot
- **Custom YFP Protocol** for efficient communication
- **Automatic server discovery** from Android app

## System Architecture

### Android App (Java)
- Camera capture and preview
- Real-time JPEG compression and streaming
- Network client with UDP/TCP support
- Detection overlay rendering
- Performance metrics display

### PC Server (Python)
- UDP/TCP server for receiving data
- YOLOv8 object detection
- GUI for feed display and controls
- WiFi hotspot creation
- Performance monitoring

### YFP Communication Protocol
- **DISCOVER**: Server discovery via broadcast
- **CONNECT**: Establish connection
- **FRAME**: Send compressed image data
- **DETECTIONS**: Return detection results
- **METRICS**: Performance data exchange
- **PING/PONG**: Keep-alive mechanism

## Setup Instructions

### PC Setup (Python Server)

1. **Install Python dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

2. **Run the server:**
   ```bash
   python pc_server.py
   ```

3. **Create WiFi Hotspot:**
   - Click "Create Hotspot" in the GUI
   - Network: `YOLO_Feed_Server`
   - Password: `12345678`

4. **Start Server:**
   - Click "Start Server" in the GUI
   - Server will listen on port 8888

### Android Setup

1. **Open in Android Studio:**
   - Import the project
   - Sync Gradle dependencies

2. **Build and install:**
   - Connect your Android device
   - Build and install the app

3. **Connect to PC:**
   - Connect phone to `YOLO_Feed_Server` WiFi
   - Open the app
   - Tap "Connect to PC"
   - App will automatically discover and connect to server

## Usage

1. **Start PC server** and create hotspot
2. **Connect phone** to the hotspot WiFi
3. **Open Android app** and tap "Connect to PC"
4. **Grant camera permission** when prompted
5. **Start detection** - camera feed will appear on both devices with detection overlays

## Performance Optimization

### Network Optimization
- Uses UDP for control messages (low latency)
- Uses TCP for image data (reliability)
- JPEG compression at 80% quality
- Frame rate throttling (sends every 3rd frame)

### Detection Optimization
- YOLOv8n model (fastest, use yolov8s/m/l for better accuracy)
- Processes frames in separate thread
- Relative coordinate system for cross-resolution compatibility

### UI Optimization
- Real-time FPS monitoring
- Network latency measurement
- Detection processing time tracking
- Connection status indicators

## YFP Protocol Specification

### Message Format
```json
{
  "type": "MESSAGE_TYPE",
  "timestamp": 1234567890,
  "data": { ... }
}
```

### Message Types

**DISCOVER**
```json
{
  "type": "DISCOVER",
  "data": {
    "device_name": "Samsung Galaxy S21",
    "app_version": "1.0"
  }
}
```

**FRAME**
```json
{
  "type": "FRAME",
  "data": {
    "frame_id": 123,
    "width": 1920,
    "height": 1080,
    "format": "JPEG",
    "quality": 80
  }
}
```

**DETECTIONS**
```json
{
  "type": "DETECTIONS",
  "data": {
    "frame_id": 123,
    "processing_time_ms": 25,
    "detections": [
      {
        "x": 0.1,
        "y": 0.2,
        "width": 0.3,
        "height": 0.4,
        "class_name": "person",
        "confidence": 0.85
      }
    ]
  }
}
```

## Network Ports

- **8888**: Main server port (UDP control + TCP images)
- **8889**: Discovery service port (UDP broadcast)

## Performance Metrics

The system tracks and displays:
- **FPS**: Frames per second on both devices
- **Network Latency**: Round-trip time for messages
- **Detection Time**: YOLO processing time per frame
- **Total Detections**: Cumulative detection count
- **Frames Processed**: Total frames analyzed

## Troubleshooting

### Connection Issues
- Ensure both devices are on same WiFi network
- Check firewall settings on PC
- Verify Android app has network permissions

### Detection Issues
- Check YOLO model installation
- Verify OpenCV installation
- Monitor detection time in GUI

### Performance Issues
- Reduce image quality in Android app
- Use faster YOLO model (yolov8n)
- Check network bandwidth

## File Structure

```
├── app/                          # Android app source
│   ├── src/main/java/.../
│   │   ├── MainActivity.java     # Main Android activity
│   │   ├── NetworkClient.java    # Network client
│   │   ├── YFPMessage.java      # Protocol messages
│   │   └── DetectionOverlayView.java # Overlay rendering
│   └── src/main/res/
│       └── layout/
│           └── activity_main.xml # Android layout
├── pc_server.py                 # Python PC server
├── requirements.txt             # Python dependencies
└── README.md                   # This file
```

## Technology Stack

### Android
- **Language**: Java
- **Camera**: CameraX
- **Networking**: Java Socket API
- **JSON**: Gson
- **UI**: Android Views

### PC
- **Language**: Python 3.8+
- **ML**: YOLOv8 (Ultralytics)
- **Vision**: OpenCV
- **GUI**: Tkinter
- **Networking**: Python socket

## License

This project is open source and available under the MIT License.