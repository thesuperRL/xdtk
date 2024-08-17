package com.google.xrinput;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BluetoothClassicHandler {
    private final String TAG = BluetoothClassicHandler.class.getSimpleName();
    Activity mainApp;
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothClassicConnectingThread connectingThread;
    BluetoothClassicAcceptConnectedThread connectedThread;
    int REQUEST_ENABLE_BT = 1;
    private String NAME = "XDTKAndroid4";
    private String ANDROID_UUID = "59a8bede-af7b-49de-b454-e9e469e740ab"; // randomly generated
    private int mState;
    private int mNewState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTENING = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public BluetoothClassicHandler(Activity activity){
        mainApp = activity;
        bluetoothManager = mainApp.getSystemService(BluetoothManager.class);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!bluetoothAdapter.isEnabled()) {
            //TODO request user enable bluetooth
        }
    }

    public synchronized int getState() {
        return mState;
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
        connectedThread = new BluetoothClassicAcceptConnectedThread(socket);
        connectedThread.start();
        Log.d(TAG, "Socket connected!");
    }

    public void sendInfo(String info){
        if (connectedThread != null){
            connectedThread.write(info);
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
            mState = STATE_LISTENING;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            Log.d(TAG, "Thread Initiated and Run");
            while (mState != STATE_CONNECTED) {
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
                    synchronized (BluetoothClassicHandler.this) {
                        switch (mState) {
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connect(socket);
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                            default:

                        }
                    }
                    cancel();
                    break;
                }
            }
        }

        public BluetoothClassicAcceptConnectedThread getConnectedThread(){
            return connectedThread;
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

    public class BluetoothClassicAcceptConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public BluetoothClassicAcceptConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            // hypothetically, we also only need output stream, because we aren't trying to read anything
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmOutStream = tmpOut;

            write("Received Message From " + NAME);
        }

        // Call this from the main activity to send data to the remote device.
        public void write(String message) {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            try {
                mmOutStream.write(bytes);
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
