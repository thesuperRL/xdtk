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
    int REQUEST_ENABLE_BT = 1;
    String name = "XDTKAndroid3";
    private int mState;
    private int mNewState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
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

        BluetoothClassicAcceptConnectedThread connectedThread = null;

        // TODO: Make sure it doesn't freeze the phone when you are trying to get thread
        while (connectedThread == null){
            connectedThread = connectivityThread.getConnectedThread();
        }

        return connectedThread;
    }

//    public void clearPastThreads(){
//        // Cancel any thread attempting to make a connection
//        if (connectingThread != null) {
//            connectingThread.cancel();
//            connectingThread = null;
//        }
//
//        // Cancel any thread currently running a connection
//        if (connectedThread != null) {
//            connectedThread.cancel();
//            connectedThread = null;
//        }
//
//    }
}
