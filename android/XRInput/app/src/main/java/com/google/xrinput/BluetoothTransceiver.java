package com.google.xrinput;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// Overall don't have to care about users not giving Bluetooth access.
// It's already handled by the Bluetooth Permissions helper
@SuppressLint("MissingPermission")
public class BluetoothTransceiver extends Transceiver {
    // Objects related to Bluetooth
    Activity mainApp;
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;

    // Threads during and after connection
    BluetoothStreamManager btStreamManager;
    BluetoothConnectorThread connectingThread;
    BluetoothListeningThread listenThread;
    BluetoothSendingThread sendThread;

    // Device Details
    private final String NAME = "XDTKAndroid"; // Name of the device. Will be displayed on Bluetooth pickers in Unity side
    private final String ANDROID_UUID = "59a8bede-af7b-49de-b454-e9e469e740ab"; // randomly generated. Should match the Stopchars in the Unity side

    // Message sending and receiving
    private final BlockingQueue<String[]> messageQueue; // Blocking queue to store messages and timeframes. Filled with String[2]
                                                        // First index indicates time packet was made. Second is the packet contents
    private String latestCommands = "";
    private boolean streamRead = true;

    // Configurable details
    public String STOPCHAR = "|"; // to act as a character that separates different instructions. Should match the Stopchars in the Unity side
    private final int MAX_PACKET_LENGTH = 1024; // maximum size of the stream is 8196 bytes
    private final int MAX_ALLOWED_DELAY = 100; // maximum delay in packet sending before it's ignored
                                                // because of how old it is (in milliseconds)

    // Creates a bluetooth handler. CommsHandler is included to parse received messages
    public BluetoothTransceiver(Activity activity, CommunicationHandler commsHandler){
        mainApp = activity;
        // create a BluetoothManager to acquire a BluetoothAdapter
        bluetoothManager = mainApp.getSystemService(BluetoothManager.class);
        // create a BluetoothAdapter to acquire a connection thread
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Record items to parent class
        this.communicationHandler = commsHandler;
        this.TAG = BluetoothTransceiver.class.getSimpleName();
        this.running = true;

        // Message Queue initialization
        // This blocks on no values left
        messageQueue = new LinkedBlockingQueue<>();

        // Make the Bluetooth device discoverable
        makeSelfDiscoverable();
    }

    // Changes device name to NAME
    void ChangeDeviceName(){
        bluetoothAdapter.setName(NAME);
        Log.i("BluetoothClassicConnector", "localdevicename : "+ NAME +" localdeviceAddress : " + bluetoothAdapter.getAddress());
    }

    // Makes the Phone discoverable to other bluetooth devices for 5 minutes
    public void makeSelfDiscoverable(){
        int requestCode = 1;
        // create a thread to allow for discoverability and connectivity in the background
        connectingThread = new BluetoothConnectorThread();
        connectingThread.start();

        ChangeDeviceName(); // change the device name to make it more unique
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300); // indicates 5 minutes of extra discovery time. Can be changed
        mainApp.startActivityForResult(discoverableIntent, requestCode, null); // become discoverable
    }

    // Set up listen and send threads for a device.
    public void connected(BluetoothSocket socket){
        // Create new listen and send threads to both read and write asynchronously and simultaneously
        btStreamManager = new BluetoothStreamManager(socket);
        listenThread = new BluetoothListeningThread(btStreamManager);
        listenThread.start();
        sendThread = new BluetoothSendingThread(btStreamManager);
        sendThread.start();
        Log.d(TAG, "Socket connected!");
    }

    // write to an external device
    public void sendData(String latestCommand) {
        // return if sendThread is null i.e. connection not yet fulfilled
        if (sendThread == null){
            Log.e(TAG, "SendThread is null");
        }

        // Record timestamp and data
        long timestamp = System.currentTimeMillis();
        String dataToSend = timestamp + "," + latestCommand;
        // check if the packet is not yet filled
        if (latestCommands.length() + dataToSend.length() >= MAX_PACKET_LENGTH - 10){
            // if the packet would overflow, add the packet into the queue and restart acquiring packets
            messageQueue.add(new String[]{Long.toString(timestamp), latestCommands});
            Log.d(TAG, "ADDED " + latestCommands);
            latestCommands = STOPCHAR + dataToSend + STOPCHAR;
        } else {
            // otherwise, add the command to the packet
            latestCommands += dataToSend + STOPCHAR;
        }
    }

    // Closes all current threads
    public void close() {
        Log.d(TAG, "Closing Bluetooth...");
        running = false;
        if (connectingThread != null){
            connectingThread.cancel();
            connectingThread = null;
        }
        if (btStreamManager != null){
            btStreamManager.closeAll();
            btStreamManager = null;
        }
        listenThread = null;
        sendThread = null;
    }

    // Class to asynchronously connect to other devices
    public class BluetoothConnectorThread extends Thread {
        // the server socket to access adapters
        private final BluetoothServerSocket btServerSocket;

        public BluetoothConnectorThread() {
            Log.d(TAG, "Thread Initiated");
            // Use a temporary object that is later assigned to btServerSocket since it's final.
            BluetoothServerSocket tmp = null;
            try {
                // ANDROID_UUID is the app's UUID string, also used by the client code.
                // Make this discoverable by listening for collections
                tmp = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord(NAME, UUID.fromString(ANDROID_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            btServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket;
            // Keep listening until exception occurs or a socket is returned.
            Log.d(TAG, "Thread Initiated and Run");
            while (true) {
                Log.d(TAG, "inside while true");
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = btServerSocket.accept();
                    Log.d(TAG, "Socket Accepted");
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Connect back via connect
                    Log.d(TAG, "Connecting...");
                    connected(socket);
                    cancel(); // cancels the thread because we don't want to connect to multiple clients
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                btServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    // Thread to handle listening
    public class BluetoothListeningThread extends Thread {
        private final BluetoothStreamManager streamManager;

        // Record StreamManager
        public BluetoothListeningThread(BluetoothStreamManager manager) {
            streamManager = manager;
        }

        public void run() {
            // Keep listening to the InputStream until an exception occurs.
            while (running) {
                byte[] messageBuffer = new byte[9]; // the only two possible texts received is "WHOAREYOU" and "HEARTBEAT" so 9 should already be enough
                                             // make it larger should we need to receive larger messages

                int numBytes = streamManager.read(messageBuffer); // read the stream
                String message = new String(messageBuffer, StandardCharsets.UTF_8); // Decode with UTF-8

                Log.d(TAG, "Received Message from Client: " + message);
                if (message.equals("HEARTBEAT")){
                    // HEARTBEAT indicates that the message was successfully read. Thus load up the next packet
                    streamRead = true;
                }
                // In all cases, send it back over to comm handler to parse
                communicationHandler.parseReceivedMessage(message);
            }
        }
    }

    // Thread to handle sending
    public class BluetoothSendingThread extends Thread {
        private final BluetoothStreamManager streamManager;

        // Record StreamManager
        public BluetoothSendingThread(BluetoothStreamManager manager) {
            streamManager = manager;
        }

        // Call this from the main activity to send data to the remote device.
        @SuppressLint("DefaultLocale")
        public void run(){
            while (running) {
                try {
                    long timestamp = System.currentTimeMillis();
                    // While there are messages in the queue and there has been a long enough delay for us to no longer need the packet
                    while (!messageQueue.isEmpty() && timestamp - Long.parseLong(messageQueue.peek()[0]) > MAX_ALLOWED_DELAY) {
                        // Just remove the packet
                        Log.d(TAG, "Packet timed out. Packet age = " + (timestamp - Long.parseLong(messageQueue.peek()[0])));
                        messageQueue.take();
                    }
                    // Grab the next data to send
                    // This blocks until there's something in the queue to take
                    String[] dataToSend = messageQueue.take();
                    // Convert sending data to bytes
                    byte[] bytes = dataToSend[1].getBytes(StandardCharsets.UTF_8);
                    // Try to send it
                    if (streamRead) {
                        // If stream was just read, we load the packet
                        streamRead = false;
                        streamManager.write(bytes);
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "Bluetooth errored with " + e + " while sending message");
                }
            }
        }
    }
}
