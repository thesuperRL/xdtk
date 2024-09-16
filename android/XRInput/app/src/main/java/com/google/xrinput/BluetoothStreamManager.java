package com.google.xrinput;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Records information relating to both streams and allows for read/write
public class BluetoothStreamManager {
    protected String TAG = BluetoothStreamManager.class.getSimpleName();
    protected BluetoothSocket btSocket;
    private final InputStream btInputStream; // stream to listen to for incoming messages
    private final OutputStream btOutputStream; // stream to send to for outgoing messages

    // Creates both streams
    public BluetoothStreamManager(BluetoothSocket socket) {
        // initial null values to record them for final later
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output stream; using temp objects because member streams are final.
        try {
            tmpIn = socket.getInputStream();
            Log.d(TAG, "Input Stream Created");
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating input stream", e);
        }
        try {
            tmpOut = socket.getOutputStream();
            Log.d(TAG, "Output Stream Created");
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating output stream", e);
        }

        // Record streams and socket
        btInputStream = tmpIn;
        btOutputStream = tmpOut;
        btSocket = socket;
    }

    // Read values. Return the length of byte array read
    public int read(byte[] messageBuffer) {
        try {
            return btInputStream.read(messageBuffer);
        } catch (IOException e) {
            Log.d(TAG, "Input stream was disconnected", e);
        }
        // -1 if errors caused it to read unsuccessfully
        return -1;
    }

    // Write byte array buffer to the output stream
    public void write(byte[] messageContents) {
        try {
            btOutputStream.write(messageContents);
        } catch (IOException e) {
            Log.d(TAG, "Output stream was disconnected", e);
        }
    }

    // Call this method from the main activity to shut down the connection.
    public void closeAll() {
        try {
            // Close the socket and both streams
            btSocket.close();
            btInputStream.close();
            btOutputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }
}
