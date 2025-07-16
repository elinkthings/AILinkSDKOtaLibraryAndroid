package com.elinkthings.bleotalibrary.zhlx;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pingwang.bluetoothlib.utils.BleLog;

import java.util.UUID;

public final class BleOtaManager extends OtaManager implements BleDeviceFinder.DeviceFinderCallback {

    private static final String TAG = BleOtaManager.class.getSimpleName();

    private static final UUID OTA_SERVICE_UUID = UUID.fromString("0000ff12-0000-1000-8000-00805f9b34fb");
    private static final UUID OTA_DATA_IN_UUID = UUID.fromString("0000ff14-0000-1000-8000-00805f9b34fb");
    private static final UUID OTA_DATA_OUT_UUID = UUID.fromString("0000ff15-0000-1000-8000-00805f9b34fb"); // without response
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService otaService;
    private BluetoothGattCharacteristic dataInCharacteristic;
    private BluetoothGattCharacteristic dataOutCharacteristic;

    private int mMtuSize = OtaConstants.DEFAULT_MTU_SIZE;

    private boolean mAutoConnect = false;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    /* Constructor */

    public BleOtaManager( Context context,  BluetoothDevice device,  EventListener eventListener) {
        super(context, device, eventListener);
    }


    /* Public */

    @SuppressLint("MissingPermission")
    @Override
    public void init() {
        BleLog.i("初始化连接设备");
        if (device == null) {
            BleLog.i("device==null");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.MANUFACTURER.equalsIgnoreCase("HUAWEI")) {
                bluetoothGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }else {
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void release() {
        // 蓝牙
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        otaService = null;
        dataInCharacteristic = null;
        dataOutCharacteristic = null;
        super.release();
    }

    @Override
    public void startOTA() {
        if (bluetoothGatt == null) {
            notifyOnError(OtaError.NOT_INIT);
            return;
        }
        if (dataInCharacteristic == null) {
            notifyOnError(OtaError.NOT_FOUND_OTA_DATA_IN);
            return;
        }
        if (dataOutCharacteristic == null) {
            notifyOnError(OtaError.NOT_FOUND_OTA_DATA_OUT);
            return;
        }

        super.startOTA();
    }

    /**
     * 获取蓝牙数据包最大长度
     * 设备返回的PacketSize和（MTU长度-3）其实是一样的，
     * 但PacketSize主要还是给SPP使用，BLE使用后者会更符合协议
     *
     * @return 返回数据包最大长度
     */
    @Override
    public int getPacketSize() {
        return getPacketSizeFromMtu(mMtuSize);
    }


    /* Private */

    /**
     * 获取Service
     * 获取Characteristic (Data in & Data out)
     * 订阅Data in
     */
    @SuppressLint("MissingPermission")
    private void findCharacteristics() {
        BleLog.i("findCharacteristics:start");
        otaService = bluetoothGatt.getService(OTA_SERVICE_UUID);
        if (otaService == null) {
            notifyOnError(OtaError.NOT_FOUND_OTA_SERVICE);
            return;
        }
        dataInCharacteristic = otaService.getCharacteristic(OTA_DATA_IN_UUID);
        dataOutCharacteristic = otaService.getCharacteristic(OTA_DATA_OUT_UUID);

        if (dataInCharacteristic == null || dataOutCharacteristic == null) {
            notifyOnError(OtaError.NOT_FOUND_OTA_CHARACTERISTIC);
            return;
        }

        // 订阅Data In
        boolean enabled = bluetoothGatt.setCharacteristicNotification(dataInCharacteristic, true);
        if (!enabled) {
            notifyOnError(OtaError.CAN_NOT_SUBSCRIBE_DATA_IN);
            return;
        }
        BluetoothGattDescriptor descriptor = dataInCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
        if (descriptor == null) {
            notifyOnError(OtaError.NOT_FOUND_CLIENT_CHARACTERISTIC_CONFIG);
            return;
        }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        boolean b = bluetoothGatt.writeDescriptor(descriptor);
        BleLog.i("findCharacteristics:end=" + b);
        // 等待onDescriptorWrite回调做下一步操作
    }

    private long mOldTime = 0;
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BleLog.i("onConnectionStateChange:status=" + status + "  ||  newState=" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // 开始查找服务
                if (System.currentTimeMillis() - mOldTime > 200) {
                    mOldTime = System.currentTimeMillis();
                    bluetoothGatt.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setDeviceReady(false);
                isUpdating = false;
                // 为了避免升级完成的断开被判断为错误
                if (dataProvider == null || !dataProvider.isAllDataSent()) {
                    notifyOnStop();
                } else {
                    dataProvider.reset();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BleLog.i("onServicesDiscovered:" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 开始查找特征
                findCharacteristics();
            } else {
                setDeviceReady(false);
                isUpdating = false;
                notifyOnStop();
                release();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runDataSend();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            boolean ret = processData(data);
//            Log.d(TAG, "消息" + (ret ? "已" : "未") + "处理");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            // 设置Notify成功
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @SuppressLint("MissingPermission") boolean ret = bluetoothGatt.requestMtu(OtaConstants.MAX_MTU_SIZE);
                BleLog.i("requestMtu(" + OtaConstants.MAX_MTU_SIZE + ") = " + ret);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            BleLog.i("onMtuChanged: mtu = " + mtu);
            mMtuSize = mtu;
            // 设置新的MTU
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (dataProvider != null) {
                    dataProvider.setPacketSize(getPacketSizeFromMtu(mtu));
                }

                setDeviceReady(true);
                if (needIdentification()) {
                    // 发送OTA识别信息
                    BleLog.i("发送OTA识别信息");
                    sendOtaIdentification();
                } else {
                    BleLog.i("获取设备信息");
                    // 获取设备信息
                    getAllInfo();
                }

                // 如果是以前的设备，或者TWS设备还没提供TWS信息
                if (isTwsDevice == null) {
                    checkIfReadyToUpdate();
                }
            }
        }
    };

    private int getPacketSizeFromMtu(int mtu) {
        return mtu - 3;
    }

    @Override
    protected boolean canSendNow() {
        return allowedUpdate && !dataProvider.isBlockSendFinish();
    }

    /**
     * 发送数据，without response
     */
    @SuppressLint("MissingPermission")
    @Override
    protected void btSendData(byte[] data) {
        if (dataOutCharacteristic != null) {
            Log.v(TAG, "btSendData = " + HexUtils.bytesToHex(data));
            dataOutCharacteristic.setValue(data);
            dataOutCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            bluetoothGatt.writeCharacteristic(dataOutCharacteristic);
        }
    }

    private BleDeviceFinder deviceFinder;

    @Override
    protected void onOneFinish() {
        // 先释放资源
        release();

        // 查找副耳，和主耳相同MAC地址
        deviceFinder = new BleDeviceFinder(bluetoothAddress, this);
        // 延迟1秒钟再扫描
        new Handler(Looper.getMainLooper()).postDelayed(deviceFinder::startFindDevice, 1000);
    }

    /* DeviceFinderCallback */

    @Override
    public void onFound( BluetoothDevice device) {
        BleLog.i("找到设备:"+device.getAddress());
        this.device = device;
        // 因为已经自动停止了，所以直接置空
        deviceFinder = null;
        // 开始初始化并升级
        init();
    }

    @Override
    public void onError(int errorCode) {
        deviceFinder = null;
        if (errorCode == BleDeviceFinder.ERROR_TIMEOUT) {
            notifyOnError(OtaError.TIMEOUT_SCAN_NON_PRIMARY_DEVICE);
        } else {
            notifyOnError(OtaError.NOT_FOUND_NON_PRIMARY_DEVICE);
        }
    }

}
