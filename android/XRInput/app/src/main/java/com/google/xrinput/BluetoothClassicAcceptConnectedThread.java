package com.google.xrinput;

import android.bluetooth.*;
import android.util.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class BluetoothClassicAcceptConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final OutputStream mmOutStream;
    private byte[] mmBuffer; // mmBuffer store for the stream
    private final String TAG = BluetoothClassicAcceptConnectedThread.class.getSimpleName();

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
