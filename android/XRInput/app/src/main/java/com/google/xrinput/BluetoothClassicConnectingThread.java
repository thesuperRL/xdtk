package com.google.xrinput;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BluetoothClassicConnectingThread extends Thread {
    private final BluetoothServerSocket mmServerSocket;
    private String NAME = "XDTKAndroid3";
    private String ANDROID_UUID = "59a8bede-af7b-49de-b454-e9e469e740ab"; // randomly generated
    private final String TAG = BluetoothClassicConnectingThread.class.getSimpleName();
    private BluetoothClassicAcceptConnectedThread connectedThread = null;

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
                socket = mmServerSocket.accept();
                Log.d(TAG, "Socket Accepted");
            } catch (IOException e) {
                Log.e(TAG, "Socket's accept() method failed", e);
                break;
            }

            if (socket != null) {
                // A connection was accepted. Perform work associated with
                // the connection in a separate thread.
                connectedThread = new BluetoothClassicAcceptConnectedThread(socket);
                connectedThread.start();
                // notify the connection was made, and then cancel the socket as we don't want to connect to more devices
                Log.d(TAG, "Socket connected!");
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
