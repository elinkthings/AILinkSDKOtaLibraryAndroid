package com.elinkthings.bleotalibrary.zhlx;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 扫描指定地址BLE设备的类，manufacturerId为0x0642（Bluetrum），
 * 如果扫描到指定的设备，则在回调中给出设备{@link BluetoothDevice}。
 */
class BleDeviceFinder {

    /**
     * 超时错误代码，不和{@link ScanCallback#onScanFailed}冲突。
     */
    public static final int ERROR_TIMEOUT = 0x80000000;

    private static final int manufacturerId = 0x0642;
    private static final int DEFAULT_TIMEOUT = 10000; // in ms, 10s

    private final byte[] addressData;
    private final DeviceFinderCallback finderCallback;

    // Timeout
    private final Handler timeoutHandler;
    private int timeout = DEFAULT_TIMEOUT; // 10s

    public BleDeviceFinder( byte[] addressData,
                            DeviceFinderCallback finderCallback) {
        this.addressData = addressData;
        this.finderCallback = finderCallback;

        this.timeoutHandler = new Handler(Looper.getMainLooper());
    }

    private final ScanCallback callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            byte[] data = result.getScanRecord().getManufacturerSpecificData(manufacturerId);
            if (Arrays.equals(data, addressData)) {
                stopFindDevice();
                if (finderCallback != null) {
                    BluetoothDevice device = result.getDevice();
                    finderCallback.onFound(device);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            stopFindDevice();
            if (finderCallback != null) {
                finderCallback.onError(errorCode);
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void startFindDevice() {
        // Scanning settings
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build();

        // Let's use the filter to scan only for Bluetrum bluetooth earbuds.
        final List<ScanFilter> filters = new ArrayList<>();
//        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid((BleOtaManager.OTA_SERVICE_UUID))).build());

        BluetoothLeScanner scanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        scanner.startScan(filters, settings, callback);

        timeoutHandler.postDelayed(this::handleTimeout, timeout);
    }

    @SuppressLint("MissingPermission")
    public void stopFindDevice() {
        cancelTimeout();
        BluetoothLeScanner scanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        scanner.stopScan(callback);
    }

    private void handleTimeout() {
        stopFindDevice();
        if (finderCallback != null) {
            finderCallback.onError(ERROR_TIMEOUT);
        }
    }

    private void cancelTimeout() {
        timeoutHandler.removeCallbacksAndMessages(null);
    }

    /* Getter & Setter */

    /**
     * 获取超时时间，毫秒
     * @return 超时时间
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * 设置超时，毫秒
     * @param timeout 超时时间
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /* interface */

    public interface DeviceFinderCallback {
        void onFound( BluetoothDevice device);
        void onError(int errorCode);
    }
}
