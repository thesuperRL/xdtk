package com.google.xrinput;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.util.Log;

@SuppressLint("MissingPermission")
public class BluetoothClassicHandler {
    private final String TAG = BluetoothClassicHandler.class.getSimpleName();
    Activity mainApp;
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothClassicConnectingThread connectingThread;
    BluetoothClassicAcceptConnectedThread connectedThread;
    private String NAME = "XDTKAndroid3";
    private String ANDROID_UUID = "59a8bede-af7b-49de-b454-e9e469e740ab"; // randomly generated
    private boolean running;

    public BluetoothClassicHandler(Activity activity){
        mainApp = activity;
        bluetoothManager = mainApp.getSystemService(BluetoothManager.class);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        this.running = true;

        if (!bluetoothAdapter.isEnabled()) {
            //TODO request user enable bluetooth
        }
    }

    void ChangeDeviceName(){
        bluetoothAdapter.setName(name);
        Log.i("BluetoothClassicConnector", "localdevicename : "+ name +" localdeviceAddress : " + bluetoothAdapter.getAddress());
    }

    public BluetoothClassicAcceptConnectedThread makeSelfDiscoverable(){
        // create a thread to allow for discoverability and connectivity in the background
        BluetoothClassicConnectingThread connectivityThread = new BluetoothClassicConnectingThread();
        connectivityThread.start();

        int requestCode = 1;
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

    public boolean isRunning() {
        return running;
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

        return connectedThread;
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
                Log.d(TAG, "Output Stream Created");
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
