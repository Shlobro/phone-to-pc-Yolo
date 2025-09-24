#!/usr/bin/env python3
"""
YOLO Feed PC Server
Receives camera feed from Android phone and performs object detection
"""

import socket
import threading
import time
import json
import struct
import cv2
import numpy as np
from ultralytics import YOLO
import tkinter as tk
from tkinter import ttk, filedialog
from PIL import Image, ImageTk
import subprocess
import platform
import argparse
import os

class YFPServer:
    def __init__(self, model_path=None):
        self.SERVER_PORT = 8888
        self.DISCOVERY_PORT = 8889
        self.running = False
        self.clients = {}

        # YOLO model
        self.model = None
        self.model_path = model_path or 'yolov8n.pt'  # Default to yolov8n.pt
        self.load_yolo_model()
        
        # Performance metrics
        self.metrics = {
            'frames_processed': 0,
            'total_detections': 0,
            'avg_detection_time': 0,
            'fps': 0,
            'last_fps_time': time.time(),
            'frames_since_fps': 0
        }
        
        # GUI
        self.root = None
        self.setup_gui()
        
        # Network
        self.udp_socket = None
        self.tcp_socket = None
        self.discovery_socket = None
        
    def load_yolo_model(self, model_path=None):
        """Load YOLO model for object detection"""
        if model_path:
            self.model_path = model_path

        try:
            print(f"Loading YOLO model: {self.model_path}")
            if not os.path.exists(self.model_path):
                raise FileNotFoundError(f"Model file not found: {self.model_path}")

            self.model = YOLO(self.model_path)
            print(f"YOLO model loaded successfully: {self.model_path}")
            if hasattr(self, 'root') and self.root:
                self.log_message(f"YOLO model loaded: {os.path.basename(self.model_path)}")
        except Exception as e:
            error_msg = f"Error loading YOLO model ({self.model_path}): {e}"
            print(error_msg)
            if hasattr(self, 'root') and self.root:
                self.log_message(error_msg)
            print("Please install ultralytics: pip install ultralytics")
            self.model = None
    
    def setup_gui(self):
        """Setup the GUI for displaying feed and controls"""
        self.root = tk.Tk()
        self.root.title("YOLO Feed PC Server")
        self.root.geometry("1200x800")
        
        # Main frame
        main_frame = ttk.Frame(self.root)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        # Control frame
        control_frame = ttk.Frame(main_frame)
        control_frame.pack(fill=tk.X, pady=(0, 10))
        
        # Server controls
        self.start_btn = ttk.Button(control_frame, text="Start Server", command=self.start_server)
        self.start_btn.pack(side=tk.LEFT, padx=(0, 5))
        
        self.stop_btn = ttk.Button(control_frame, text="Stop Server", command=self.stop_server, state=tk.DISABLED)
        self.stop_btn.pack(side=tk.LEFT, padx=(0, 5))
        
        self.hotspot_btn = ttk.Button(control_frame, text="Create Hotspot", command=self.create_hotspot)
        self.hotspot_btn.pack(side=tk.LEFT, padx=(0, 10))

        # Model selection controls
        self.model_btn = ttk.Button(control_frame, text="Load Model", command=self.select_model)
        self.model_btn.pack(side=tk.LEFT, padx=(0, 5))

        self.current_model_label = ttk.Label(control_frame, text=f"Model: {os.path.basename(self.model_path)}")
        self.current_model_label.pack(side=tk.LEFT, padx=(0, 10))

        # Status label
        self.status_label = ttk.Label(control_frame, text="Server stopped", foreground="red")
        self.status_label.pack(side=tk.LEFT)
        
        # Metrics frame
        metrics_frame = ttk.LabelFrame(main_frame, text="Performance Metrics", padding=10)
        metrics_frame.pack(fill=tk.X, pady=(0, 10))
        
        # Metrics labels
        metrics_grid = ttk.Frame(metrics_frame)
        metrics_grid.pack(fill=tk.X)
        
        self.fps_label = ttk.Label(metrics_grid, text="FPS: 0.0")
        self.fps_label.grid(row=0, column=0, sticky=tk.W, padx=(0, 20))
        
        self.detection_time_label = ttk.Label(metrics_grid, text="Detection Time: 0ms")
        self.detection_time_label.grid(row=0, column=1, sticky=tk.W, padx=(0, 20))
        
        self.total_detections_label = ttk.Label(metrics_grid, text="Total Detections: 0")
        self.total_detections_label.grid(row=0, column=2, sticky=tk.W, padx=(0, 20))
        
        self.frames_processed_label = ttk.Label(metrics_grid, text="Frames Processed: 0")
        self.frames_processed_label.grid(row=1, column=0, sticky=tk.W, padx=(0, 20))
        
        self.clients_label = ttk.Label(metrics_grid, text="Connected Clients: 0")
        self.clients_label.grid(row=1, column=1, sticky=tk.W, padx=(0, 20))
        
        # Video display frame
        video_frame = ttk.LabelFrame(main_frame, text="Camera Feed", padding=10)
        video_frame.pack(fill=tk.BOTH, expand=True)
        
        self.video_label = ttk.Label(video_frame, text="No feed", anchor=tk.CENTER)
        self.video_label.pack(fill=tk.BOTH, expand=True)
        
        # Log frame
        log_frame = ttk.LabelFrame(main_frame, text="Server Log", padding=10)
        log_frame.pack(fill=tk.X, pady=(10, 0))
        
        self.log_text = tk.Text(log_frame, height=6, wrap=tk.WORD)
        log_scrollbar = ttk.Scrollbar(log_frame, orient=tk.VERTICAL, command=self.log_text.yview)
        self.log_text.configure(yscrollcommand=log_scrollbar.set)
        
        self.log_text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        log_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
    def log_message(self, message):
        """Add message to log"""
        timestamp = time.strftime("%H:%M:%S")
        self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
        self.log_text.see(tk.END)
        self.root.update_idletasks()

    def select_model(self):
        """Open file dialog to select YOLO model"""
        filetypes = [
            ("PyTorch models", "*.pt"),
            ("ONNX models", "*.onnx"),
            ("All files", "*.*")
        ]

        filename = filedialog.askopenfilename(
            title="Select YOLO Model",
            filetypes=filetypes,
            initialdir=os.getcwd()
        )

        if filename:
            old_model_name = os.path.basename(self.model_path)
            self.load_yolo_model(filename)
            if self.model:  # Only update if model loaded successfully
                self.current_model_label.config(text=f"Model: {os.path.basename(self.model_path)}")
                self.log_message(f"Model changed from {old_model_name} to {os.path.basename(self.model_path)}")

    def create_hotspot(self):
        """Create a WiFi hotspot (Windows specific)"""
        try:
            if platform.system() == "Windows":
                # Create mobile hotspot using netsh
                subprocess.run([
                    "netsh", "wlan", "set", "hostednetwork", 
                    "mode=allow", "ssid=YOLO_Feed_Server", "key=12345678"
                ], check=True)
                subprocess.run(["netsh", "wlan", "start", "hostednetwork"], check=True)
                self.log_message("Hotspot created: YOLO_Feed_Server (password: 12345678)")
            else:
                self.log_message("Hotspot creation not supported on this platform")
        except Exception as e:
            self.log_message(f"Error creating hotspot: {e}")
    
    def start_server(self):
        """Start the server"""
        if self.running:
            return
            
        try:
            self.running = True
            
            # Start UDP server for control messages
            self.udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.udp_socket.bind(('0.0.0.0', self.SERVER_PORT))
            
            # Start TCP server for image data
            self.tcp_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.tcp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.tcp_socket.bind(('0.0.0.0', self.SERVER_PORT))
            self.tcp_socket.listen(5)
            
            # Start discovery server
            self.discovery_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.discovery_socket.bind(('0.0.0.0', self.DISCOVERY_PORT))
            
            # Start server threads
            threading.Thread(target=self.udp_handler, daemon=True).start()
            threading.Thread(target=self.tcp_handler, daemon=True).start()
            threading.Thread(target=self.discovery_handler, daemon=True).start()
            threading.Thread(target=self.metrics_updater, daemon=True).start()
            
            self.start_btn.config(state=tk.DISABLED)
            self.stop_btn.config(state=tk.NORMAL)
            self.status_label.config(text="Server running", foreground="green")
            self.log_message("Server started on port 8888")
            
        except Exception as e:
            self.log_message(f"Error starting server: {e}")
            self.running = False
    
    def stop_server(self):
        """Stop the server"""
        self.running = False
        
        if self.udp_socket:
            self.udp_socket.close()
        if self.tcp_socket:
            self.tcp_socket.close()
        if self.discovery_socket:
            self.discovery_socket.close()
            
        self.clients.clear()
        
        self.start_btn.config(state=tk.NORMAL)
        self.stop_btn.config(state=tk.DISABLED)
        self.status_label.config(text="Server stopped", foreground="red")
        self.log_message("Server stopped")
    
    def discovery_handler(self):
        """Handle discovery requests"""
        while self.running:
            try:
                data, addr = self.discovery_socket.recvfrom(1024)
                message = json.loads(data.decode())
                
                if message.get('type') == 'DISCOVER':
                    response = {
                        'type': 'DISCOVER',
                        'timestamp': int(time.time() * 1000),
                        'data': {
                            'server_name': 'YOLO Feed Server',
                            'version': '1.0'
                        }
                    }
                    
                    response_data = json.dumps(response).encode()
                    self.discovery_socket.sendto(response_data, addr)
                    self.log_message(f"Discovery request from {addr[0]}")
                    
            except Exception as e:
                if self.running:
                    self.log_message(f"Discovery handler error: {e}")
    
    def udp_handler(self):
        """Handle UDP messages (control, metrics)"""
        while self.running:
            try:
                data, addr = self.udp_socket.recvfrom(4096)
                message = json.loads(data.decode())
                
                client_id = f"{addr[0]}:{addr[1]}"
                
                if message.get('type') == 'CONNECT':
                    self.clients[client_id] = {
                        'addr': addr,
                        'udp_addr': addr,  # Store UDP address for responses
                        'connected_time': time.time(),
                        'frames_received': 0
                    }
                    print(f"BASIC_DEBUG: Client UDP address stored: {addr}")
                    self.log_message(f"Client connected: {addr[0]}")
                    
                elif message.get('type') == 'PING':
                    # Send pong response
                    pong = {
                        'type': 'PONG',
                        'timestamp': int(time.time() * 1000)
                    }
                    response_data = json.dumps(pong).encode()
                    self.udp_socket.sendto(response_data, addr)
                    
            except Exception as e:
                if self.running:
                    self.log_message(f"UDP handler error: {e}")
    
    def tcp_handler(self):
        """Handle TCP connections for image data"""
        while self.running:
            try:
                client_socket, addr = self.tcp_socket.accept()
                threading.Thread(target=self.handle_client_images, args=(client_socket, addr), daemon=True).start()
            except Exception as e:
                if self.running:
                    self.log_message(f"TCP handler error: {e}")
    
    def handle_client_images(self, client_socket, addr):
        """Handle image data from a specific client"""
        self.log_message(f"Image connection from {addr[0]}")
        print(f"BASIC_DEBUG: Starting image handler for client {addr}")

        while self.running:
            try:
                # Read header length (4 bytes, big-endian integer)
                header_length_data = client_socket.recv(4)
                if not header_length_data or len(header_length_data) != 4:
                    break
                    
                header_length = struct.unpack('>I', header_length_data)[0]
                
                if header_length <= 0 or header_length > 10000:  # Sanity check
                    self.log_message(f"Client {addr[0]} error: invalid header length: {header_length}")
                    break
                
                # Read header
                header_data = b''
                while len(header_data) < header_length:
                    chunk = client_socket.recv(header_length - len(header_data))
                    if not chunk:
                        break
                    header_data += chunk
                
                if len(header_data) != header_length:
                    self.log_message(f"Client {addr[0]} error: incomplete header")
                    break
                
                try:
                    header = json.loads(header_data.decode('utf-8'))
                except json.JSONDecodeError as e:
                    self.log_message(f"Client {addr[0]} error: invalid JSON header: {e}")
                    break
                
                # Read image data - read until we get a complete JPEG
                frame_data = header.get('data', {})
                image_data = b''
                
                # Read in chunks and try to decode JPEG until we get a valid image
                max_image_size = 2000000  # 2MB max
                while len(image_data) < max_image_size:
                    chunk = client_socket.recv(32768)  # 32KB chunks
                    if not chunk:
                        break
                    image_data += chunk
                    
                    # Try to decode JPEG to see if we have complete image
                    # JPEG files end with FFD9, so check for that
                    if len(image_data) > 1000 and image_data[-2:] == b'\xff\xd9':
                        try:
                            np_arr = np.frombuffer(image_data, np.uint8)
                            img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
                            if img is not None:
                                self.log_message(f"Received complete JPEG image: {len(image_data)} bytes")
                                break
                        except:
                            continue
                
                if image_data and len(image_data) > 0:
                    print(f"BASIC_DEBUG: Processing frame from {addr}, size={len(image_data)} bytes")
                    self.process_frame(image_data, frame_data, addr)
                else:
                    print(f"BASIC_DEBUG: No valid image data received from {addr}")
                    
            except Exception as e:
                if self.running:
                    self.log_message(f"Client {addr[0]} error: {e}")
                break
                
        client_socket.close()
        self.log_message(f"Client {addr[0]} disconnected")
    
    def process_frame(self, image_data, frame_info, client_addr):
        """Process received frame with YOLO detection"""
        print(f"BASIC_DEBUG: process_frame called for {client_addr}")
        if not self.model:
            print("BASIC_DEBUG: No YOLO model loaded, skipping detection")
            return

        start_time = time.time()
        
        try:
            # Decode image
            np_arr = np.frombuffer(image_data, np.uint8)
            img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
            
            if img is None:
                return
                
            # Run YOLO detection
            results = self.model(img)
            
            # Extract detections
            detections = []
            print(f"DETECTION_DEBUG: Processing YOLO results, found {len(results)} result objects")
            for r in results:
                boxes = r.boxes
                print(f"DETECTION_DEBUG: Result has boxes: {boxes is not None}")
                if boxes is not None:
                    print(f"DETECTION_DEBUG: Found {len(boxes)} boxes")
                    for i, box in enumerate(boxes):
                        x1, y1, x2, y2 = box.xyxy[0].tolist()
                        conf = box.conf[0].item()
                        cls = int(box.cls[0].item())

                        print(f"DETECTION_DEBUG: Box {i}: class={cls} ({self.model.names[cls]}) conf={conf:.2f} coords=({x1:.1f},{y1:.1f},{x2:.1f},{y2:.1f})")

                        # Convert to relative coordinates
                        h, w = img.shape[:2]
                        x = x1 / w
                        y = y1 / h
                        width = (x2 - x1) / w
                        height = (y2 - y1) / h

                        detection = {
                            'x': x,
                            'y': y,
                            'width': width,
                            'height': height,
                            'class_name': self.model.names[cls],
                            'confidence': conf
                        }
                        detections.append(detection)
                        print(f"DETECTION_DEBUG: Added detection: {detection}")

                        # Draw detection on image for display
                        cv2.rectangle(img, (int(x1), int(y1)), (int(x2), int(y2)), (0, 255, 0), 2)
                        label = f"{self.model.names[cls]} {conf:.2f}"
                        cv2.putText(img, label, (int(x1), int(y1)-10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)

            print(f"DETECTION_DEBUG: Total detections created: {len(detections)}")
            
            detection_time = int((time.time() - start_time) * 1000)
            
            # Update metrics
            self.metrics['frames_processed'] += 1
            self.metrics['total_detections'] += len(detections)
            self.metrics['avg_detection_time'] = ((self.metrics['avg_detection_time'] * (self.metrics['frames_processed'] - 1)) + detection_time) / self.metrics['frames_processed']
            
            # Send detections back to client
            self.send_detections(detections, frame_info.get('frame_id', 0), detection_time, client_addr)
            
            # Update GUI display
            self.update_video_display(img)
            
        except Exception as e:
            self.log_message(f"Frame processing error: {e}")
    
    def send_detections(self, detections, frame_id, detection_time, tcp_client_addr):
        """Send detection results back to client"""
        try:
            # Find the UDP address for this client
            client_ip = tcp_client_addr[0]
            udp_addr = None

            # Look for a client with matching IP address
            for client_id, client_info in self.clients.items():
                if client_info['addr'][0] == client_ip:
                    udp_addr = client_info['udp_addr']
                    break

            if not udp_addr:
                print(f"BASIC_DEBUG: No UDP address found for client {client_ip}, cannot send detections")
                return

            response = {
                'type': 'DETECTIONS',
                'timestamp': int(time.time() * 1000),
                'data': {
                    'frame_id': frame_id,
                    'detections': detections,
                    'processing_time_ms': detection_time
                }
            }

            print(f"DETECTION_DEBUG: Sending {len(detections)} detections to UDP {udp_addr} (TCP was {tcp_client_addr})")
            print(f"DETECTION_DEBUG: Frame ID: {frame_id}, Processing time: {detection_time}ms")
            for i, det in enumerate(detections):
                print(f"DETECTION_DEBUG: Detection {i}: {det}")

            response_data = json.dumps(response).encode()
            print(f"DETECTION_DEBUG: JSON response length: {len(response_data)} bytes")
            print(f"DETECTION_DEBUG: JSON response: {response_data.decode()[:200]}...")  # First 200 chars

            self.udp_socket.sendto(response_data, udp_addr)
            print(f"DETECTION_DEBUG: Successfully sent detection response to UDP {udp_addr}")

        except Exception as e:
            print(f"DETECTION_DEBUG: Error sending detections: {e}")
            self.log_message(f"Error sending detections: {e}")
    
    def update_video_display(self, img):
        """Update the GUI video display"""
        try:
            # Resize image to fit display
            display_height = 400
            h, w = img.shape[:2]
            display_width = int(w * display_height / h)
            
            img_resized = cv2.resize(img, (display_width, display_height))
            img_rgb = cv2.cvtColor(img_resized, cv2.COLOR_BGR2RGB)
            img_pil = Image.fromarray(img_rgb)
            img_tk = ImageTk.PhotoImage(img_pil)
            
            self.video_label.config(image=img_tk, text="")
            self.video_label.image = img_tk  # Keep a reference
            
        except Exception as e:
            self.log_message(f"Display update error: {e}")
    
    def metrics_updater(self):
        """Update performance metrics"""
        while self.running:
            try:
                current_time = time.time()
                self.metrics['frames_since_fps'] += 1
                
                if current_time - self.metrics['last_fps_time'] >= 1.0:
                    self.metrics['fps'] = self.metrics['frames_since_fps'] / (current_time - self.metrics['last_fps_time'])
                    self.metrics['frames_since_fps'] = 0
                    self.metrics['last_fps_time'] = current_time
                
                # Update GUI metrics
                self.fps_label.config(text=f"FPS: {self.metrics['fps']:.1f}")
                self.detection_time_label.config(text=f"Detection Time: {self.metrics['avg_detection_time']:.0f}ms")
                self.total_detections_label.config(text=f"Total Detections: {self.metrics['total_detections']}")
                self.frames_processed_label.config(text=f"Frames Processed: {self.metrics['frames_processed']}")
                self.clients_label.config(text=f"Connected Clients: {len(self.clients)}")
                
                time.sleep(0.1)
                
            except Exception as e:
                if self.running:
                    self.log_message(f"Metrics update error: {e}")
    
    def run(self):
        """Run the server"""
        self.log_message("YOLO Feed PC Server started")
        self.log_message("Click 'Create Hotspot' to setup WiFi, then 'Start Server'")
        self.root.mainloop()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='YOLO Feed PC Server')
    parser.add_argument('--model', '-m', type=str, default=None,
                       help='Path to YOLO model file (default: yolov8n.pt)')
    parser.add_argument('--verbose', '-v', action='store_true',
                       help='Enable verbose logging')

    args = parser.parse_args()

    server = YFPServer(model_path=args.model)
    server.run()