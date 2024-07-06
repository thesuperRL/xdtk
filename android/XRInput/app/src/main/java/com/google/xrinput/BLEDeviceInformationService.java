package com.google.xrinput;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.xrinput.BLEBasicService;
import com.google.xrinput.BLEService;
import com.google.xrinput.BLEssedTransceiver;
import com.welie.blessed.BluetoothCentral;
import com.welie.blessed.BluetoothPeripheralManager;
import com.welie.blessed.GattStatus;
import com.welie.blessed.ReadResponse;

import org.jetbrains.annotations.NotNull;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;

import androidx.annotation.NonNull;

public class BLEDeviceInformationService extends BLEBasicService {
    private final String TAG = BLEDeviceInformationService.class.getSimpleName();

    private static final UUID SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002A69-0000-1000-8000-00805f9b34fb");

    private @NotNull final BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, SERVICE_TYPE_PRIMARY);
    private @NotNull final BluetoothGattCharacteristic orientation = new BluetoothGattCharacteristic(SERVICE_UUID, PROPERTY_READ | PROPERTY_WRITE | PROPERTY_INDICATE, PERMISSION_READ);
    private @NotNull final Handler handler = new Handler(Looper.getMainLooper());
    private @NotNull final Runnable notifyRunnable = this::notifyOrientation;
    private int current = 80;

    public BLEDeviceInformationService(@NotNull BluetoothPeripheralManager peripheralManager) {
        super(peripheralManager);
        Log.i(TAG, "Service Initiated");
        service.addCharacteristic(orientation);
        orientation.addDescriptor(getClientCharacteristicConfigurationDescriptor());
    }

    @Override
    public void onCentralDisconnected(@NotNull BluetoothCentral central) {
        if (noCentralsConnected()) {
            stopNotifying();
        }
    }

    @Override
    public ReadResponse onCharacteristicRead(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
        Log.i(TAG, "Detected a read request");
        if (characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
            return new ReadResponse(GattStatus.SUCCESS, new byte[]{0x00, 0x40});
        }
        return super.onCharacteristicRead(central, characteristic);
    }

    @Override
    public GattStatus onCharacteristicWrite(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value) {
        return null;
    }

    @Override
    public void onCharacteristicWriteCompleted(@NonNull BluetoothCentral central, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {

    }

    @Override
    public ReadResponse onDescriptorRead(@NotNull BluetoothCentral central, @NotNull BluetoothGattDescriptor descriptor) {
        return null;
    }

    @Override
    public GattStatus onDescriptorWrite(@NotNull BluetoothCentral central, @NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value) {
        return null;
    }

    @Override
    public void onNotifyingEnabled(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
        Log.i(TAG, "Notifying...");
        if (characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
            notifyOrientation();
        }
    }

    @Override
    public void onNotifyingDisabled(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
            stopNotifying();
        }
    }

    @Override
    public void onNotificationSent(@NotNull BluetoothCentral central, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {

    }

    @Override
    public void onCentralConnected(@NotNull BluetoothCentral central) {

    }

    private void notifyOrientation() {
        Log.i(TAG, "Generating Information");
        final byte[] value = new byte[]{0x00, 0x66};
        notifyCharacteristicChanged(value, orientation);
        handler.postDelayed(notifyRunnable, 1000);
        Log.i(TAG, "new test: " + current);
    }

    private void stopNotifying() {
        handler.removeCallbacks(notifyRunnable);
    }

    @Override
    public @NotNull BluetoothGattService getService() {
        return service;
    }

    @Override
    public String getServiceName() {
        return "Orientation Service";
    }
}