# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a dual-platform YOLO object detection system that streams real-time camera feed from an Android phone to a PC for processing. The Android app captures camera frames and sends them to a Python server running on PC, which performs YOLOv8 object detection and returns detection results back to the phone for overlay display.

## Development Commands

### Android Development
- **Build project**: `./gradlew build` (Linux/Mac) or `.\gradlew.bat build` (Windows)
- **Build debug APK**: `./gradlew assembleDebug`
- **Build release APK**: `./gradlew assembleRelease`
- **Install to device**: `./gradlew installDebug`
- **Run tests**: `./gradlew test`
- **Run instrumented tests**: `./gradlew connectedAndroidTest`

### Python Server Development
- **Install dependencies**: `pip install -r requirements.txt`
- **Run server**: `python pc_server.py`
- **Run with verbose logging**: `python pc_server.py --verbose`

## Architecture

### Communication Protocol (YFP - YOLO Feed Protocol)
The system uses a custom protocol with these message types:
- **DISCOVER**: UDP broadcast for server discovery (port 8889)
- **CONNECT**: Establish TCP connection
- **FRAME**: Send JPEG-compressed camera frames
- **DETECTIONS**: Return detection results with coordinates
- **METRICS**: Performance data exchange
- **PING/PONG**: Keep-alive mechanism

### Android Components
- **MainActivity.java**: Main activity handling camera, UI, and network coordination
- **NetworkClient.java**: Manages UDP/TCP connections and YFP protocol
- **DetectionOverlayView.java**: Custom view for drawing detection rectangles
- **YFPMessage.java**: JSON message structure for protocol communication

### Python Server Components
- **YFPServer class**: Main server handling UDP discovery and TCP frame processing
- **YOLO model**: Uses YOLOv8n for object detection (model file: yolov8n.pt)
- **GUI**: Tkinter interface for server control and monitoring
- **WiFi hotspot creation**: Automated hotspot setup for phone connection

### Data Flow
1. Android app broadcasts DISCOVER message
2. PC server responds with server info
3. Phone establishes TCP connection
4. Camera frames are JPEG-compressed and sent via TCP
5. Server processes frames with YOLO and returns detection coordinates
6. Detection overlays are rendered on both devices

### Performance Optimizations
- Frame throttling: Only processes every 3rd frame
- JPEG compression at 80% quality
- Relative coordinate system for cross-resolution compatibility
- Separate threads for network I/O and detection processing

## Key Dependencies

### Android
- CameraX for camera operations
- Gson for JSON serialization
- Material Design components

### Python
- ultralytics (YOLOv8)
- opencv-python for image processing
- tkinter for GUI
- torch/torchvision for ML backend

## Network Configuration
- **Main server port**: 8888 (TCP for images, UDP for control)
- **Discovery port**: 8889 (UDP broadcast)
- **WiFi network**: YOLO_Feed_Server (password: 12345678)

## Development Notes
- Android app requires camera permissions
- Python server needs YOLO model file (yolov8n.pt) in project root
- Both devices must be on same network for communication
- Frame processing is CPU-intensive; consider GPU acceleration for better performance