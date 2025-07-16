package com.elinkthings.bleotalibrary.bk;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.pingwang.bluetoothlib.config.BleConfig;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.device.SendDataBean;
import com.pingwang.bluetoothlib.listener.OnCharacteristicListener;
import com.pingwang.bluetoothlib.utils.BleLog;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;


/**
 * BK OTA
 */
public class BKOtaManager implements OnCharacteristicListener, BleDevice.onDisConnectedListener {


    private final static String TAG = BKOtaManager.class.getSimpleName();

    public final static String ERROR_SEND_IMG_STR = "Send img error.";

    private BluetoothGatt mBluetoothGatt;
    private OnBleOTAListener mOnBleOTAListener;

    private HashMap errors;
    // ble communication error
    public static final int ERROR_COMMUNICATION = 0xffff;
    // mSuota service was not found
    public static final int ERROR_SUOTA_NOT_FOUND = 0xfffe;
    // read fileUtils error
    public static final int ERROR_READ_FILE = 0xfffd;
    // Work can't upgrade
    public static final int ERROR_ON_START = 0xfffc;
    // Low power can't upgrade
    public static final int ERROR_LOW_POWER = 0xfffb;
    // Write Characteristic error
    public static final int ERROR_SEND_IMG = 0xfffa;

    public static final UUID UUID_OTA_SERVICE = UUID.fromString("f000ffc0-0451-4000-b000-000000000000");
    public static final UUID UUID_OTA_READ = UUID.fromString("f000ffc1-0451-4000-b000-000000000000");
    public static final UUID UUID_OTA_WRITE = UUID.fromString("f000ffc2-0451-4000-b000-000000000000");


    private BluetoothGattCharacteristic mOtaWriteData1, mOtaWriteData2;
    private BleBKOtaUtils fileUtils;
    /**
     * 是否支持OTA
     */
    private boolean mVerifySupport = false;
    private String mFilePath = "";
    private BleDevice mBleDevice;


    private BKOtaManager(Builder builder) {
        this.mBleDevice = builder.mBleDevice;
        if (mBleDevice != null) {
            mBleDevice.setOnDisConnectedListener(this);
            this.mBluetoothGatt = mBleDevice.getBluetoothGatt();
            if (mBluetoothGatt == null) {
                mBleDevice.disconnect();
                return;
            }
        }
        this.mFilePath = builder.mFilePath;
        this.mOnBleOTAListener = builder.mOnBleOTAListener;
        BluetoothGattService suota = mBluetoothGatt.getService(UUID_OTA_SERVICE);
        if (suota != null) {
            mOtaWriteData1 = suota.getCharacteristic(UUID_OTA_READ);
            mOtaWriteData2 = suota.getCharacteristic(UUID_OTA_WRITE);
            mVerifySupport = true;
            if (mBleDevice != null) {
                mBleDevice.setOnCharacteristicListener(this);
                mBleDevice.setNotify(UUID_OTA_SERVICE, UUID_OTA_READ, UUID_OTA_WRITE);
            }
        } else {
            Log.e(TAG, "不支持Dialog OTA");
            mVerifySupport = false;
        }
        initErrorMap();
        initFileLoad(mFilePath);
        getDeviceAndRomVersion();
    }


    /**
     * 初始化dialog 错误信息
     */
    private void initErrorMap() {
        this.errors = new HashMap<>();
        errors.put(ERROR_SEND_IMG, ERROR_SEND_IMG_STR);
        errors.put(ERROR_LOW_POWER, "Low power can't upgrade.");
        errors.put(ERROR_ON_START, "Work can't upgrade.");
        errors.put(ERROR_READ_FILE, "Read fileUtils error.");
        errors.put(ERROR_COMMUNICATION, "Communication error.");
        errors.put(ERROR_SUOTA_NOT_FOUND, "The remote device does not support SUOTA.");
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public final static class Builder {
        private BleDevice mBleDevice;
        private OnBleOTAListener mOnBleOTAListener;
        private String mFilePath = "";

        public Builder() {
        }


        public Builder setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
            mOnBleOTAListener = onBleOTAListener;
            return this;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }




        public BKOtaManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            if (mBleDevice == null) {
                return null;
            }
            return new BKOtaManager(this);
        }
    }


    /**
     * 是否支持OTA
     */
    public boolean isVerifySupport() {
        return mVerifySupport;
    }


    /**
     * 初始化,开始OTA
     */
    public synchronized void startOta() {
        if (!mVerifySupport || mBleDevice == null || mBluetoothGatt == null) {
            hasError = false;
            onError(ERROR_COMMUNICATION);
            return;
        }
        mOldIndex = 0;
        mBleDevice.setOnCharacteristicListener(this);
        sendHeadBlock();
//        threadHandler.sendEmptyMessageDelayed(OTA_SEND_MSG, 500);
        lastBlock = false;
    }


    private void initFileLoad(String fileName) {
        try {
            if (!TextUtils.isEmpty(fileName)) {
                fileUtils = BleBKOtaUtils.getByFileName(fileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
            hasError = false;
            onError(ERROR_READ_FILE);
        }
    }


    public long getDeviceVersion() {
        if (fileUtils != null) {
            return fileUtils.getDeviceVersion();
        }
        return 0;
    }

    public long getRomVersion() {
        if (fileUtils != null) {
            return fileUtils.getRomVersion();
        }
        return 0;
    }


    @Override
    public void onCharacteristicReadOK(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onCharacteristicWriteOK(BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        if (uuid.equals(BKOtaManager.UUID_OTA_WRITE)) {
            //notify成功,开始升级
        }
    }

    @Override
    public void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {
        UUID uuid = descriptor.getCharacteristic().getUuid();
        if (uuid.equals(BKOtaManager.UUID_OTA_WRITE)) {
            //notify成功,开始升级

        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        if (uuid.equals(BKOtaManager.UUID_OTA_WRITE)) {
            //OTA升级返回的通知
            onNotifyData(characteristic);
        } else if (uuid.equals(BKOtaManager.UUID_OTA_READ)) {
            //设备版本号回复
            byte[] value = characteristic.getValue();
            onDeviceVersion(value);
        }

    }

    /**
     * 解析设备返回的版本号信息
     *
     * @param value byte
     */
    private void onDeviceVersion(byte[] value) {
        long deviceVersion = BleBKOtaUtils.buildUint16(value[1], value[0]);
        long romVersion = BleBKOtaUtils.buildUint16(value[9], value[8]);
        if (mOnBleOTAListener instanceof OnBleBkOTAListener) {
            ((OnBleBkOTAListener) mOnBleOTAListener).onDeviceVersion(String.valueOf(deviceVersion), String.valueOf(romVersion));
        }
    }

    /**
     * 通知返回数据
     */
    public final void onNotifyData(BluetoothGattCharacteristic characteristic) {
        if (!lastBlock) {
            byte[] value = characteristic.getValue();
            int index = ((value[1] & 0xFF) << 8) + (value[0] & 0xFF);
            if (index > 0) {
                mOldIndex = index + 1;
                threadHandler.removeMessages(OTA_SEND_MSG);
                sendBlock(index);
                threadHandler.sendEmptyMessageDelayed(OTA_SEND_MSG, 100);
            }
        }
    }


    /**
     * 发送OTA升级包的数据块
     */
    private synchronized void getDeviceAndRomVersion() {
        if (mOtaWriteData1 != null) {
            SendDataBean sendDataBean = new SendDataBean(new byte[]{0x00}, UUID_OTA_READ, BleConfig.WRITE_DATA, UUID_OTA_SERVICE);
            mBleDevice.sendData(sendDataBean);
        }
//        byte[] bytes=new byte[]{0x00};
//        SendDataBean sendDataBean = new SendDataBean(bytes, BleConfig.UUID_WRITE_AILINK, BleConfig.WRITE_DATA, BleConfig.UUID_SERVER_AILINK);
//        mBleDevice.sendData(sendDataBean);
    }


    /**
     * 是否为最后一块
     */
    private boolean lastBlock = false;
    private int mOldIndex = 0;

    /**
     * 发送OTA升级包的数据块
     */
    @SuppressLint("MissingPermission")
    private synchronized void sendBlock(int index) {
        byte[] block = fileUtils.getBlock(index);
        final float progress = ((float) (index) / (float) fileUtils.getBlocks()) * 100;
        if (mOtaWriteData2 != null) {
//            SendDataBean sendDataBean=new SendDataBean(block,UUID_OTA_WRITE,BleConfig.WRITE_DATA,UUID_OTA_SERVICE);
//            mBleDevice.sendDataOta(sendDataBean);
            mOtaWriteData2.setValue(block);
            int properties = mOtaWriteData2.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                //写,无回复 WRITE_TYPE_NO_RESPONSE
                mOtaWriteData2.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                mOtaWriteData2.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(mOtaWriteData2);
            if (!writeSuccess) {
                threadHandler.removeMessages(OTA_SEND_MSG);
            }
        }
        runOnMainThread(() -> {
            if (mOnBleOTAListener != null) {
                mOnBleOTAListener.onOtaProgress(progress, 1, 1);
            }
        });
        if (index == fileUtils.getBlocks()) {
            lastBlock = true;
            onSuccess();
        }
    }


    /**
     * 发送OTA升级包的数据块
     */
    @SuppressLint("MissingPermission")
    private synchronized void sendHeadBlock() {
        byte[] block = fileUtils.getHeadBlock();
        if (mOtaWriteData1 != null) {
//            SendDataBean sendDataBean=new SendDataBean(block,UUID_OTA_WRITE,BleConfig.WRITE_DATA,UUID_OTA_SERVICE);
//            mBleDevice.sendDataOta(sendDataBean);
            mOtaWriteData1.setValue(block);
            int properties = mOtaWriteData2.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                //写,无回复 WRITE_TYPE_NO_RESPONSE
                mOtaWriteData2.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                mOtaWriteData2.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(mOtaWriteData1);
            threadHandler.removeMessages(OTA_SEND_MSG);
            if (!writeSuccess) {
                onError(ERROR_SEND_IMG);
            } else {
                threadHandler.sendEmptyMessageDelayed(OTA_SEND_MSG, 100);
            }
        }
    }

    /**
     * 是否错误
     */
    private boolean hasError = false;

    /**
     * OTA升级完成,设备会自动复位,如果没有复位就代表可能是失败了
     */
    private void onSuccess() {
        threadHandler.removeMessages(OTA_SEND_MSG);

        runOnMainThread(() -> {
            if (mOnBleOTAListener != null) {
                mOnBleOTAListener.onOtaSuccess();
            }
        });
        if (fileUtils != null) {
            fileUtils.close();
        }

    }

    private void onError(int errorCode) {
        if (!hasError) {
            String error = (String) errors.get(errorCode);
            Log.e(TAG, "Error: " + errorCode + " " + error);
            runOnMainThread(() -> {
                if (mOnBleOTAListener != null) {
                    mOnBleOTAListener.onOtaFailure(errorCode, error);
                }
            });

            hasError = true;
            if (fileUtils != null) {
                fileUtils.close();
            }
        }
    }

    private final static int OTA_SEND_MSG = 2;

    public void setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
        mOnBleOTAListener = onBleOTAListener;
    }

    private Handler threadHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case OTA_SEND_MSG:
                    threadHandler.removeMessages(OTA_SEND_MSG);
                    if (mOldIndex > fileUtils.getBlocks()) {
                        //更新完成
                        onSuccess();
                        return;
                    }
                    sendBlock(mOldIndex);
                    mOldIndex = mOldIndex + 1;
                    threadHandler.sendEmptyMessageDelayed(OTA_SEND_MSG, 20);
                    break;

            }
        }
    };


    @SuppressLint("MissingPermission")
    @Override
    public void onDisConnected() {
        if (!lastBlock) {
            BleLog.i(TAG, "onDisConnected:连接断开");
            mBluetoothGatt.close();
            hasError = false;
            onError(ERROR_COMMUNICATION);
            threadHandler.removeMessages(OTA_SEND_MSG);
        }
    }


    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            threadHandler.post(runnable);
        }
    }

}
