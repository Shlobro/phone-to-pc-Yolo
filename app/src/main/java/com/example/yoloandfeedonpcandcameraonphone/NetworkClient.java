package com.example.yoloandfeedonpcandcameraonphone;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkClient {
    private static final String TAG = "NetworkClient";
    private static final int SERVER_PORT = 8888;
    private static final int DISCOVERY_PORT = 8889;
    
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private String serverIP;
    private ExecutorService executor;
    private Handler mainHandler;
    private NetworkCallback callback;
    private boolean isConnected = false;

    public interface NetworkCallback {
        void onServerDiscovered(String serverIP);
        void onConnected();
        void onDisconnected();
        void onDetectionsReceived(YFPMessage.DetectionsData detections);
        void onMetricsReceived(YFPMessage.MetricsData metrics);
        void onError(String error);
    }

    public NetworkClient(NetworkCallback callback) {
        this.callback = callback;
        this.executor = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void discoverServer() {
        executor.execute(() -> {
            try {
                DatagramSocket discoverySocket = new DatagramSocket();
                discoverySocket.setBroadcast(true);
                
                YFPMessage discoverMsg = new YFPMessage(YFPMessage.MessageType.DISCOVER, 
                    new YFPMessage.DiscoverData(android.os.Build.MODEL, "1.0"));
                
                byte[] data = discoverMsg.toJson().getBytes();
                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, DISCOVERY_PORT);
                
                discoverySocket.send(packet);
                
                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                discoverySocket.setSoTimeout(5000);
                discoverySocket.receive(response);
                
                String responseData = new String(response.getData(), 0, response.getLength());
                YFPMessage responseMsg = YFPMessage.fromJson(responseData);
                
                if (responseMsg.type == YFPMessage.MessageType.DISCOVER) {
                    serverIP = response.getAddress().getHostAddress();
                    mainHandler.post(() -> callback.onServerDiscovered(serverIP));
                }
                
                discoverySocket.close();
                
            } catch (Exception e) {
                Log.e(TAG, "Discovery failed", e);
                mainHandler.post(() -> callback.onError("Server discovery failed: " + e.getMessage()));
            }
        });
    }

    public void connect(String serverIP) {
        this.serverIP = serverIP;
        executor.execute(() -> {
            try {
                udpSocket = new DatagramSocket();
                
                YFPMessage connectMsg = new YFPMessage(YFPMessage.MessageType.CONNECT, 
                    new YFPMessage.ConnectData(android.os.Build.DEVICE, 1920, 1080));
                
                sendUdpMessage(connectMsg);
                
                tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(serverIP, SERVER_PORT), 5000);
                
                isConnected = true;
                mainHandler.post(() -> callback.onConnected());
                
                startListening();
                
            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                mainHandler.post(() -> callback.onError("Connection failed: " + e.getMessage()));
            }
        });
    }

    public void sendFrame(byte[] imageData, long frameId, int width, int height) {
        if (!isConnected) return;
        
        executor.execute(() -> {
            try {
                YFPMessage frameMsg = new YFPMessage(YFPMessage.MessageType.FRAME,
                    new YFPMessage.FrameData(frameId, width, height, "JPEG", 80));
                
                String header = frameMsg.toJson();
                byte[] headerBytes = header.getBytes("UTF-8");
                
                // Use binary protocol: 4-byte header length + header + image
                java.nio.ByteBuffer headerLengthBuffer = java.nio.ByteBuffer.allocate(4);
                headerLengthBuffer.putInt(headerBytes.length);
                byte[] headerLengthBytes = headerLengthBuffer.array();
                
                tcpSocket.getOutputStream().write(headerLengthBytes);
                tcpSocket.getOutputStream().write(headerBytes);
                tcpSocket.getOutputStream().write(imageData);
                tcpSocket.getOutputStream().flush();
                
            } catch (IOException e) {
                Log.e(TAG, "Failed to send frame", e);
                mainHandler.post(() -> callback.onError("Failed to send frame"));
            }
        });
    }

    private void sendUdpMessage(YFPMessage message) throws IOException {
        byte[] data = message.toJson().getBytes();
        InetAddress serverAddr = InetAddress.getByName(serverIP);
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, SERVER_PORT);
        udpSocket.send(packet);
    }

    private void startListening() {
        executor.execute(() -> {
            byte[] buffer = new byte[4096];
            while (isConnected) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    
                    String data = new String(packet.getData(), 0, packet.getLength());
                    YFPMessage message = YFPMessage.fromJson(data);
                    
                    mainHandler.post(() -> handleMessage(message));
                    
                } catch (SocketException e) {
                    if (isConnected) {
                        Log.e(TAG, "Socket exception while listening", e);
                    }
                    break;
                } catch (IOException e) {
                    Log.e(TAG, "Error receiving data", e);
                    break;
                }
            }
        });
    }

    private void handleMessage(YFPMessage message) {
        switch (message.type) {
            case DETECTIONS:
                if (callback != null) {
                    YFPMessage.DetectionsData detections = (YFPMessage.DetectionsData) message.data;
                    callback.onDetectionsReceived(detections);
                }
                break;
            case METRICS:
                if (callback != null) {
                    YFPMessage.MetricsData metrics = (YFPMessage.MetricsData) message.data;
                    callback.onMetricsReceived(metrics);
                }
                break;
            case PING:
                try {
                    YFPMessage pong = new YFPMessage(YFPMessage.MessageType.PONG, null);
                    sendUdpMessage(pong);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to send pong", e);
                }
                break;
        }
    }

    public void disconnect() {
        isConnected = false;
        
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        
        if (tcpSocket != null && !tcpSocket.isClosed()) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing TCP socket", e);
            }
        }
        
        mainHandler.post(() -> callback.onDisconnected());
    }

    public void shutdown() {
        disconnect();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}