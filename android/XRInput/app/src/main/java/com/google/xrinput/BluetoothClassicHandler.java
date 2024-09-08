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
public class BluetoothClassicHandler {
    private final String TAG = BluetoothClassicHandler.class.getSimpleName();
    Activity mainApp;
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothClassicConnectingThread connectingThread;
    BTClassicAcceptListenThread listenThread;
    BTClassicAcceptSendThread sendThread;
    private String NAME = "XDTKAndroid";
    private String ANDROID_UUID = "59a8bede-af7b-49de-b454-e9e469e740ab"; // randomly generated
    private boolean running;
    public String STOPCHAR = "|"; // to act as a character that separates different instructions
    private CommunicationHandler commsHandler;
    private BlockingQueue<String[]> messageQueue;
    private String latestCommands = "";
    private boolean streamRead = true;

    private final int MAX_PACKET_LENGTH = 1000; // maximum size of the stream is 8196 bytes
    private final int MAX_ALLOWED_DELAY = 100; // maximum delay in packet sending before it's ignored
                                                // because of how old it is (in milliseconds)


    // Creates a bluetooth handler. CommsHandler is included to parse received messages
    public BluetoothClassicHandler(Activity activity, CommunicationHandler commsHandler){
        mainApp = activity;
        // create a BluetoothManager to acquire a BluetoothAdapter
        bluetoothManager = mainApp.getSystemService(BluetoothManager.class);
        // create a BluetoothAdapter to acquire a connection thread
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        this.commsHandler = commsHandler;
        this.running = true;
        messageQueue = new LinkedBlockingQueue<>();
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
        connectingThread = new BluetoothClassicConnectingThread();
        connectingThread.start();

        ChangeDeviceName(); // change the device name to make it more unique
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300); // indicates 5 minutes of extra discovery time
        mainApp.startActivityForResult(discoverableIntent, requestCode, null);
    }

    // Set up listen and send threads for a device.
    // Synchronized with the BluetoothClassicConnectingThread after connection's finished
    public void connect(BluetoothSocket socket){
        // Create new listen and send threads to both read and write asynchronously
        listenThread = new BTClassicAcceptListenThread(socket);
        listenThread.start();
        sendThread = new BTClassicAcceptSendThread(socket);
        sendThread.start();
        Log.d(TAG, "Socket connected!");
    }

    public void write(String data) {
        long timestamp = System.currentTimeMillis();
        String dataToSend = timestamp + "," + data;
        if (latestCommands.length() + dataToSend.length() >= MAX_PACKET_LENGTH - 10){
            //@SuppressLint("DefaultLocale") String paddedLength = String.format("%04d", latestCommands.length() + 10);
            messageQueue.add(new String[]{Long.toString(timestamp), latestCommands});
            Log.d(TAG, "ADDED " + latestCommands);
            latestCommands = STOPCHAR + dataToSend + STOPCHAR;
        } else {
            latestCommands += dataToSend + STOPCHAR;
        }
    }

    // sends given data using the send thread
    public void sendData(String info){
        if (sendThread != null){
            write(info);
        }
    }

    // returns whether it's running
    public boolean isRunning() {
        return running;
    }

    // Closes all current threads
    public void close() {
        if (connectingThread != null){
            connectingThread.cancel();
            connectingThread = null;
        }
        if (listenThread != null){
            listenThread.cancel();
            listenThread = null;
        }
        if (sendThread != null){
            sendThread.cancel();
            sendThread = null;
        }
    }

    // Class to asynchronously connect to other devices
    public class BluetoothClassicConnectingThread extends Thread {
        private final BluetoothServerSocket btServerSocket;

        public BluetoothClassicConnectingThread() {
            Log.d(TAG, "Thread Initiated");
            // Use a temporary object that is later assigned to btServerSocket since it's final.
            BluetoothServerSocket tmp = null;
            try {
                // ANDROID_UUID is the app's UUID string, also used by the client code.
                tmp = BluetoothAdapter.getDefaultAdapter().listenUsingInsecureRfcommWithServiceRecord(NAME, UUID.fromString(ANDROID_UUID));
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

                // If a connection was accepted, call synchronized connect to make other threads
                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    Log.d(TAG, "Syncing...");
                    connect(socket);
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
    public class BTClassicAcceptListenThread extends Thread {
        private final BluetoothSocket btSocket;
        private final InputStream btInputStream;
        private byte[] messageBuffer; // mmBuffer store for the stream

        public BTClassicAcceptListenThread(BluetoothSocket socket) {
            btSocket = socket;
            InputStream tmpIn = null;

            // Get the input stream; using temp objects because member streams are final.
            // we don't need the output stream here, that's for the other thread
            try {
                tmpIn = socket.getInputStream();
                Log.d(TAG, "Input Stream Created");
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }

            btInputStream = tmpIn;
            Log.d(TAG, "InputStream Initiated");
        }

        public void run() {
            messageBuffer = new byte[1024]; // max size for read messages
            // Maximum is 8192, but in this case we are getting 6-byte messages so we are good
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    messageBuffer = new byte[9]; // the only two possible texts received is "WHOAREYOU" and "HEARTBEAT" so 1024 should be more than enough

                    numBytes = btInputStream.read(messageBuffer); // read the stream
                    String message = new String(messageBuffer, StandardCharsets.UTF_8); // Decode with UTF-8

                    Log.d(TAG, "Received Message from Client: " + message);
                    if (message.equals("HEARTBEAT")){
                        streamRead = true;
                    }
                    // In all cases, send it back over to comm handler to parse
                    commsHandler.parseReceivedMessage(message);
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    // Thread to handle sending
    public class BTClassicAcceptSendThread extends Thread {
        private final BluetoothSocket btSocket;
        private final OutputStream btOutputStream;

        public BTClassicAcceptSendThread(BluetoothSocket socket) {
            btSocket = socket;
            OutputStream tmpOut = null;

            // Get the output stream; using temp objects because member streams are final.
            // we don't need the input stream here, that's for the other thread
            try {
                tmpOut = socket.getOutputStream();
                Log.d(TAG, "Output Stream Created");
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }
            btOutputStream = tmpOut;
        }

        // Call this from the main activity to send data to the remote device.
        @SuppressLint("DefaultLocale")
        public void run(){
            while (running) {
                try {
                    long timestamp = System.currentTimeMillis();
                    while (!messageQueue.isEmpty() && timestamp - Long.parseLong(messageQueue.peek()[0]) > MAX_ALLOWED_DELAY) {
                        Log.d(TAG, "Time = " + (timestamp - Long.parseLong(messageQueue.peek()[0])));
                        messageQueue.take();
                    }
                    Log.d(TAG, "blocking");
                    String[] dataToSend = messageQueue.take();
                    Log.d(TAG, "unblocked");
                    // Convert sending data to bytes
                    Log.d(TAG, "bytes1");
                    byte[] bytes = dataToSend[1].getBytes(StandardCharsets.UTF_8);
//                    // Pad bytes length until there's 4 digits (max 8196)
//                    Log.d(TAG, "pad");
//                    String paddedLength = String.format("%04d", bytes.length);
//                    // Encode that into another message
//                    Log.d(TAG, "bytes2");
//                    byte[] length = paddedLength.getBytes(StandardCharsets.UTF_8);
                    // Try to send it
                    Log.d(TAG, "try");
                    if (streamRead) {
                        try {
                            streamRead = false;
                            btOutputStream.write(bytes);
                        } catch (IOException e) {
                            Log.e(TAG, "Error occurred when sending data", e);
                        }
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "Bluetooth errored with " + e + " while sending message");
                }
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}
