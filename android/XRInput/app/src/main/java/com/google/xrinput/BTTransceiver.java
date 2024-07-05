package com.google.xrinput;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.Manifest;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.ar.core.examples.java.common.helpers.BTPermissionHelper;

import java.util.UUID;

public class BTTransceiver {
    private final String TAG = BTTransceiver.class.getSimpleName();

    // Context
    private Activity activity;

    // Grab a Bluetooth Manager and Adapter
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothDevice selfDevice;
    private BluetoothGatt hackyWriter;

    // The Advertising and its settings
    private boolean advertising;
    private BluetoothLeAdvertiser bleAdvertiser;
    private AdvertiseSettings settings;
    private AdvertiseData advertiseData;
    private BluetoothGattServer gattServer;
    private BluetoothGattCallback bluetoothGattCallback;

    @SuppressLint("MissingPermission")
    public BTTransceiver(Activity activity){
        Log.d(TAG, "Setting up Bluetooth Transceiver...");

        // Setup Context
        this.activity = activity;

        // setup BT objects
        btManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        // "Valid Bluetooth names are a maximum of 248 bytes using UTF-8 encoding,
        // although many remote devices can only display the first 40 characters,
        // and some may be limited to just 20."
        btAdapter.setName("XDTKAndroid");

        // setup itself as a device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            selfDevice = btAdapter.getRemoteLeDevice(btAdapter.getAddress(), BluetoothDevice.ADDRESS_TYPE_PUBLIC);

            bluetoothGattCallback = new BluetoothGattCallback() {};

            hackyWriter = selfDevice.connectGatt(activity, true, bluetoothGattCallback);
        }

        // setup advertising settings
        constructAdvertisementSettings();

        // Is not advertising right now
        advertising = false;
    }

    public void constructAdvertisementSettings(){
        settings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .build();

        advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .build();
    }

    private boolean isBluetoothEnabled(){
        // The former means that BT is not available on device
        // The latter means that BT is not enabled on device
        return btAdapter == null || !btAdapter.isEnabled();
    }

    public boolean isAdvertising(){
        return advertising;
    }

    private void askEnableBluetooth(){
        if (btAdapter == null){
            // Device does not support Bluetooth
            // TODO: Handle this case!
        } else if (!btAdapter.isEnabled()){
            // Bluetooth is not enabled
            Log.d(TAG, "Bluetooth isn't enabled. Asking to enable it...");
            BTPermissionHelper.askToEnableBT(activity);
        }
    }

    // An advertising callback used to deliver the operation's status
    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            Log.e("AdvertiseCallback", "Start Advertise Callback Failed");
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i("AdvertiseCallback", "Start Advertise Callback Success");
        }

        ;
    };

    // Replace it with LoggingGATTServerCallback for more logs
    private final BluetoothGattServerCallback blegattServerCallback = new SimpleGATTServerCallback();

    @SuppressLint("MissingPermission")
    private void addDeviceInfoService() {
        // 0x180A indicates the device information
        final String SERVICE_DEVICE_INFORMATION = "0000180a-0000-1000-8000-00805f9b34fb";

        BluetoothGattService deviceInfoService = new BluetoothGattService(
                UUID.fromString(SERVICE_DEVICE_INFORMATION),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );
        gattServer.addService(deviceInfoService);
    }

    @SuppressLint("MissingPermission")
    public void startAdvertise(){
        Log.d(TAG, "Starting Bluetooth Advertisement");
        openServer();
        addDeviceInfoService();
        addRequiredDetailsService();

        runAdvertiser();
    }

    @SuppressLint("MissingPermission")
    private void openServer() {
        gattServer = btManager.openGattServer(activity, blegattServerCallback);
    }

    @SuppressLint("MissingPermission")
    public void addRequiredDetailsService() {
        // Randomly generated base UUID: f115xxxx-d3be-43bb-b5f1-a210e2c6757b
        // replace the xxxx with a number indicating which service

        // String indicating Public Key Open Credential (PKOC) Service
        final String SERVICE_UUID = "00002902-0000-1000-8000-00805F9B34FB";
        final String CHARACTERISTIC_TEST_UUID = "00001234-0000-1000-8000-00805F9B34FB";
        final String DESCRIPTOR_TEST_UUID = "00005678-0000-1000-8000-00805F9B34FB";

        Log.d(TAG, "Descriptor Coding");
        BluetoothGattDescriptor testDescriptor = new BluetoothGattDescriptor(
                UUID.fromString(DESCRIPTOR_TEST_UUID),
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
        );

        //testDescriptor.setValue(("testingd").getBytes());

        Log.d(TAG, "Characteristic Coding");
        BluetoothGattCharacteristic testCharacteristic = new BluetoothGattCharacteristic(
                UUID.fromString(CHARACTERISTIC_TEST_UUID),
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE | BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattService service = new BluetoothGattService(
                UUID.fromString(SERVICE_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        //testCharacteristic.addDescriptor(testDescriptor);

        testCharacteristic.setValue(1234,
                BluetoothGattCharacteristic.FORMAT_UINT8, /* offset */ 0);
        testCharacteristic.setValue(("testing").getBytes());
        service.addCharacteristic(testCharacteristic);
        gattServer.addService(service);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            Log.d(TAG, "WRITING");
//            hackyWriter.writeCharacteristic(testCharacteristic, ("testing").getBytes(), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
//            Log.d(TAG, "WRITING DONE");
        }

    }

    @SuppressLint("MissingPermission") // checked already
    public void runAdvertiser() {
        bleAdvertiser = btAdapter.getBluetoothLeAdvertiser();
        bleAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback);
        advertising = true;
    }

    @SuppressLint("MissingPermission")
    public void stopAdvertise() {
        if (bleAdvertiser != null){
            bleAdvertiser.stopAdvertising(advertiseCallback);
        }

        bleAdvertiser = null;
        advertising = false;
    }
}
