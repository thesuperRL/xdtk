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
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BluetoothClassicHandler {
    private final String TAG = BluetoothClassicHandler.class.getSimpleName();
    Activity mainApp;
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothClassicConnectingThread connectingThread;
    BluetoothClassicAcceptListenThread listenThread;
    BluetoothClassicAcceptSendThread sendThread;
    private String NAME = "XDTKAndroid3";
    private String ANDROID_UUID = "59a8bede-af7b-49de-b454-e9e469e740ab"; // randomly generated
    private boolean running;
    public String STOPCHAR = " | ";
    private CommunicationHandler commsHandler;

    private Queue<String> informationToSend = new ArrayDeque<String>();
    private int packetsInOStream = 0;

    public BluetoothClassicHandler(Activity activity, CommunicationHandler commsHandler){
        mainApp = activity;
        bluetoothManager = mainApp.getSystemService(BluetoothManager.class);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.commsHandler = commsHandler;

        this.running = true;

        if (!bluetoothAdapter.isEnabled()) {
            //TODO request user enable bluetooth
        }
    }

    void ChangeDeviceName(){
        bluetoothAdapter.setName(NAME);
        Log.i("BluetoothClassicConnector", "localdevicename : "+ NAME +" localdeviceAddress : " + bluetoothAdapter.getAddress());
    }

    public void makeSelfDiscoverable(){ int requestCode = 1;
        // create a thread to allow for discoverability and connectivity in the background
        connectingThread = new BluetoothClassicConnectingThread();
        connectingThread.start();

        ChangeDeviceName(); // change the device name to make it more unique
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        mainApp.startActivityForResult(discoverableIntent, requestCode, null);
    }

    public synchronized void connect(BluetoothSocket socket){
        listenThread = new BluetoothClassicAcceptListenThread(socket);
        listenThread.start();
        sendThread = new BluetoothClassicAcceptSendThread(socket);
        Log.d(TAG, "Socket connected!");
    }

    public void sendData(String info){
        if (sendThread != null){
            sendThread.write(info + STOPCHAR);
        }
    }

    public boolean isRunning() {
        return running;
    }

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

    public class BluetoothClassicConnectingThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public BluetoothClassicConnectingThread() {
            Log.d(TAG, "Thread Initiated");
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = BluetoothAdapter.getDefaultAdapter().listenUsingInsecureRfcommWithServiceRecord(NAME, UUID.fromString(ANDROID_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            Log.d(TAG, "Thread Initiated and Run");
            while (true) {
                Log.d(TAG, "inside while true");
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                    Log.d(TAG, "Socket Accepted");
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    Log.d(TAG, "Syncing...");
                    synchronized (BluetoothClassicHandler.this) {
                        Log.d(TAG, "Synced!");
                        connect(socket);
                    }
                    cancel();
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    public class BluetoothClassicAcceptListenThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public BluetoothClassicAcceptListenThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
                Log.d(TAG, "Input Stream Created");
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }

            mmInStream = tmpIn;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    mmBuffer = new byte[9]; // the only two possible texts received is "WHOAREYOU" and "HEARTBEAT" so 1024 should be more than enough
                    numBytes = mmInStream.read(mmBuffer);
                    String message = new String(mmBuffer, StandardCharsets.UTF_8); // Decode with UTF-8
                    Log.d(TAG, "Received Message from Client: " + message);
                    if (message.equals("HEARTBEAT")){
                        packetsInOStream = 0;
                    }
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
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    public class BluetoothClassicAcceptSendThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;

        public BluetoothClassicAcceptSendThread(BluetoothSocket socket) {
            mmSocket = socket;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpOut = socket.getOutputStream();
                Log.d(TAG, "Output Stream Created");
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }
            mmOutStream = tmpOut;
        }

        public void enqueue(String data){
            informationToSend.add(data);
        }

        // Call this from the main activity to send data to the remote device.
        public void write(String data) {
            if(packetsInOStream == 6){
                return;
            }
            // pre-append timestamp
            long timestamp = System.currentTimeMillis();
            String dataToSend = timestamp + "," + data;
            byte[] bytes = dataToSend.getBytes(StandardCharsets.UTF_8);
            try {
                mmOutStream.write(bytes);
                packetsInOStream++;
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}
