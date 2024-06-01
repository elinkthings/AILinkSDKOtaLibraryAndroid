package com.elinkthings.bleotalibrary.linkedsemi;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.listener.OnCharacteristicListener;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
 * @author ljl
 * on 2023/12/14
 */

public class LinkedSeMiOtaManager implements OnCharacteristicListener {

    private final static String TAG = LinkedSeMiOtaManager.class.getSimpleName();

    private BluetoothGatt mBluetoothGatt;
    private OnBleOTAListener mOnBleOTAListener;

    private HashMap errors;
    /**
     * 读取文件失败
     */
    public static final int ERROR_READ_FILE = 3;
    /**
     * 开始OTA失败
     */
    public static final int ERROR_ON_START = 4;
    /**
     * 电量太低
     */
    public static final int ERROR_LOW_POWER = 5;
    /**
     * OTA完整性校验失败
     */
    public static final int ERROR_OTA_CRC = 6;

    public static final UUID UUID_OTA_SERVICE = UUID.fromString("00002600-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_OTA_INDICATE = UUID.fromString("00007000-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_OTA_WRITE = UUID.fromString("00007001-0000-1000-8000-00805f9b34fb");

    private static final UUID DESCR_TWO = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGattCharacteristic ota7000Chara, ota7001Chara;

    private BleLinkedSeMiOtaUtils fileUtils;

    /**
     * 是否支持OTA
     */
    private boolean mVerifySupport = false;
    private String mFilePath = "";
    private BleDevice mBleDevice;


    private LinkedSeMiOtaManager(Builder builder) {
        this.mBleDevice = builder.mBleDevice;
        if (mBleDevice != null) {
            this.mBluetoothGatt = mBleDevice.getBluetoothGatt();
            if (mBluetoothGatt == null) {
                mBleDevice.disconnect();
                return;
            }
        }
        this.mFilePath = builder.mFilePath;
        this.mOnBleOTAListener = builder.mOnBleOTAListener;
        initErrorMap();
        BluetoothGattService suota = mBluetoothGatt.getService(UUID_OTA_SERVICE);
        if (suota != null) {
            ota7000Chara = suota.getCharacteristic(UUID_OTA_INDICATE);
            ota7001Chara = suota.getCharacteristic(UUID_OTA_WRITE);
            mVerifySupport = true;
            if (mBleDevice != null) {
                mBleDevice.setOnCharacteristicListener(this);
                mBleDevice.setIndication(UUID_OTA_SERVICE, UUID_OTA_INDICATE);
            }

        } else {
            Log.e(TAG, "不支持Dialog OTA");
            mVerifySupport = false;
        }
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

        public LinkedSeMiOtaManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            if (mBleDevice == null) {
                return null;
            }
            return new LinkedSeMiOtaManager(this);
        }
    }


    /**
     * 最后执行开始升级
     */
    public void startOta() {
        this.startOta(mFilePath);
    }

    /**
     * INDICATE打开成功
     */
    private boolean mIndicate = false;

    /**
     * 开始OTA升级
     */
    private boolean mStartOta = false;

    /**
     * 初始化,开始OTA
     */
    public synchronized void startOta(String fileName) {
        if (!mVerifySupport || mBleDevice == null) {
            return;
        }

        mBleDevice.setOnCharacteristicListener(this);
        setFileName(fileName);
        mStartOta = true;
    }


    private void setFileName(String fileName) {
        try {
            if (!TextUtils.isEmpty(fileName)) {
                fileUtils = BleLinkedSeMiOtaUtils.getByFileName(fileName);
                fileUtils.setFileBlockSize(4096);
            }
        } catch (IOException e) {
            e.printStackTrace();
            onError(ERROR_READ_FILE);
        }

    }

    /**
     * 初始化dialog 错误信息
     */
    private void initErrorMap() {
        this.errors = new HashMap<>();

        // Application error codes
        errors.put(ERROR_LOW_POWER, "Low power can't upgrade.");
        errors.put(ERROR_ON_START, "Work can't upgrade.");
        errors.put(ERROR_READ_FILE, "Read fileUtils error.");
        errors.put(ERROR_OTA_CRC, "OTA patch crc error.");
    }

    /**
     * 设置监听OTA升级的Notification,在BleDevice中设置了
     */
    @SuppressLint("MissingPermission")
    public void enableOTAIndication() {
        if (mBluetoothGatt != null && ota7000Chara != null) {
            mBluetoothGatt.setCharacteristicNotification(ota7000Chara, true);
            BluetoothGattDescriptor descriptor = ota7000Chara.getDescriptor(DESCR_TWO);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean writeSuccess = mBluetoothGatt.writeDescriptor(descriptor);
            if (!writeSuccess) {
                onError(ERROR_ON_START);
            }
        }
    }


    @Override
    public void onCharacteristicReadOK(BluetoothGattCharacteristic characteristic) {

        byte[] value = characteristic.getValue();
        if (characteristic.getUuid().equals(UUID_OTA_WRITE)) {
            if (value.length == 27) {
                if (lastChunkSent && blockCounter < (fileUtils.getNumberOfBlocks() - 1)) {
                    //发送完一包了，发送新的sectorID
                    sendBlockInfo(++blockCounter);
                    lastChunkSent = false;
                } else if (lastChunkSent && blockCounter == (fileUtils.getNumberOfBlocks() - 1)) {
                    //收完了，发送固件校验指令
                    sendCheckResult();
                } else {
                    sendBlockDates(blockCounter);
                }

            }

        }
    }

    private boolean checkOk(byte[] value) {
        byte a = (byte) 0xFF;
        for (byte b : value) {
            a = (byte) (a & b);
        }
        return (a & 0xFF) == 255;
    }

    @Override
    public void onCharacteristicWriteOK(BluetoothGattCharacteristic characteristic) {
        onWriteDataOk(characteristic);
    }

    @Override
    public void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {
        UUID uuid = descriptor.getCharacteristic().getUuid();

        if (uuid.equals(LinkedSeMiOtaManager.UUID_OTA_INDICATE)) {
            //打开indicate成功,可以发送固件摘要信息了
            mIndicate = true;
            //发送摘要信息
            setDigestData();
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        byte[] value = characteristic.getValue();

        if (uuid.equals(LinkedSeMiOtaManager.UUID_OTA_INDICATE)) {
            if (value.length >= 2 && value[0] == 0x03) {
                if ((value[1] & 0xFF) == 0x00) {
                    //发送第一个block推送
                    sendBlockInfo(blockCounter);
                } else {
                    onError(ERROR_ON_START);
                }
            }
        }

    }


    public void onWriteDataOk(BluetoothGattCharacteristic characteristic) {

        byte[] value = characteristic.getValue();
        if (characteristic.getUuid().equals(LinkedSeMiOtaManager.UUID_OTA_INDICATE)) {
            if (value.length >= 2 && value[0] == 0x01 && value[1] == 0x00) {
                //发送第二包摘要信息
                setDigestData1();
            } else if (value.length >= 2 && value[0] == 0x01 && value[1] == 0x01) {
                //发送开始OTA
                setStartOtaInfo();
            }
//            else if (value.length >= 2 && value[0] == 0x03 && value[1] == 0x00) {
//                //发送第一个block推送
//                sendBlockInfo(blockCounter);
//            }
            else if (value.length == 3 && value[0] == 0x04 && ((value[1] & value[2]) == 0)) {
                //发送第一个block里面的数据
                sendBlockDates(blockCounter);
            } else if (value.length >= 2 && value[0] == 0x06) {
                //OTA文件完整性校验
                if ((value[1] & 0xFF) == 0) {
                    //校验成功,发OTA结束指令
                    endOta();
                } else if ((value[1] & 0xFF) != 0) {
                    //校验失败，发OTA失败
                    onError(ERROR_OTA_CRC);
                }
            }
        } else if (characteristic.getUuid().equals(LinkedSeMiOtaManager.UUID_OTA_WRITE)) {
            //读取一下当前block发送到第几包
            readBlockState();

        }

    }

    /**
     * 结束OTA
     */
    @SuppressLint("MissingPermission")
    private void endOta() {
        byte[] bytes = new byte[17];
        bytes[0] = 0x07;
        bytes[1] = 0x07;
        //表示OTA文件需要复制的大小,0表示使用源地址和目标地址去复制
        int numberOfBytes = (int) getFileSize(mFilePath);
        bytes[2] = (byte) numberOfBytes;
        bytes[3] = (byte) (numberOfBytes >> 8);
        bytes[4] = (byte) (numberOfBytes >> 16);

        //源地址
        bytes[5] = 0x00;
        bytes[6] = (byte) 0x80;
        bytes[7] = 0x05;
        bytes[8] = 0x18;
        //目标地址
        bytes[9] = 0x00;
        bytes[10] = 0x40;
        bytes[11] = 0x03;
        bytes[12] = 0x18;
        //启动的地址
        bytes[13] = 0x00;
        bytes[14] = 0x40;
        bytes[15] = 0x03;
        bytes[16] = 0x18;

        if (ota7000Chara != null) {
            ota7000Chara.setValue(bytes);
            int properties = ota7000Chara.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                //写,无回复 WRITE_TYPE_NO_RESPONSE
                ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(ota7000Chara);

            if (writeSuccess) {
                onSuccess();
            }
        }
    }

    private int readSuccessFlag = -1;

    /**
     * 读取当前block状态
     *
     * @param
     */
    @SuppressLint("MissingPermission")
    private void readBlockState() {
        if (ota7001Chara != null) {
            boolean readSuccess = mBluetoothGatt.readCharacteristic(ota7001Chara);

            if (!readSuccess) {
                readSuccessFlag++;
                if (readSuccessFlag < 5) {
                    readBlockState();
                } else {
                    onError(ERROR_ON_START);
                }
            } else {
                readSuccessFlag = -1;
            }
        }
    }

    private int blockCounter = 0;
    private int chunkCounter = -1;
    private int chunkSentCounter = 0;

    /**
     * 最后一块是否已发送
     */
    private boolean lastBlockSent = false;

    /**
     * 每一块的最后一包数据是否已发送
     */
    private boolean lastChunkSent = false;

    /**
     * 发送block里面的数据
     *
     * @param index 第几个block
     */
    @SuppressLint("MissingPermission")
    private synchronized void sendBlockDates(int index) {
        final float progress = ((float) (chunkSentCounter + 1) / (float) fileUtils.getTotalChunkCount()) * 100;

        if (!lastBlockSent) {

            byte[][] block = fileUtils.getBlock(index);

            int i = ++chunkCounter;
            boolean lastChunk = false;

            if (chunkCounter == block.length - 1) {
                chunkCounter = -1;
                lastChunk = true;
                lastChunkSent = true;
            }
            byte[] chunk = block[i];
            byte[] value = new byte[chunk.length + 1];
            value[0] = (byte) i;
            System.arraycopy(chunk, 0, value, 1, chunk.length);

            if (ota7001Chara != null) {
                ota7001Chara.setValue(value);
                int properties = ota7001Chara.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    //写,无回复 WRITE_TYPE_NO_RESPONSE
                    ota7001Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    ota7001Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                boolean writeSuccess = mBluetoothGatt.writeCharacteristic(ota7001Chara);

                if (!writeSuccess) {
                    onError(ERROR_ON_START);
                } else {
                    chunkSentCounter++;
                }
            }

            if (lastChunk) {
                if (!lastBlock) {
//                    blockCounter++;
                } else {
                    lastBlockSent = true;
                }
                if (blockCounter + 1 == fileUtils.getNumberOfBlocks()) {
                    lastBlock = true;
                }
            }

        }
        runOnMainThread(() -> {
            if (mOnBleOTAListener != null) {
                mOnBleOTAListener.onOtaProgress(progress, 1, 1);
            }
        });

    }

    /**
     * 发送
     */
    @SuppressLint("MissingPermission")
    private void sendBlockInfo(int index) {
        byte[] bytes = new byte[3];
        bytes[0] = 0x04;
        bytes[1] = (byte) index;
        bytes[2] = (byte) (index >> 8);
        if (mStartOta && mIndicate) {
            if (ota7000Chara != null) {
                ota7000Chara.setValue(bytes);
                int properties = ota7000Chara.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    //写,无回复 WRITE_TYPE_NO_RESPONSE
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                mBluetoothGatt.writeCharacteristic(ota7000Chara);
//                boolean writeSuccess = mBluetoothGatt.writeCharacteristic(ota7000Chara);
            }
        }
    }

    /**
     * 固件校验
     */
    @SuppressLint("MissingPermission")
    private void sendCheckResult() {
        byte[] bytes = new byte[1];
        bytes[0] = 0x05;
        if (mStartOta && mIndicate) {
            if (ota7000Chara != null) {
                ota7000Chara.setValue(bytes);
                int properties = ota7000Chara.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    //写,无回复 WRITE_TYPE_NO_RESPONSE
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                mBluetoothGatt.writeCharacteristic(ota7000Chara);
//                boolean writeSuccess = mBluetoothGatt.writeCharacteristic(ota7000Chara);
            }
        }
    }

    /**
     * 发送OTA文件的摘要信息数据第一包
     */
    @SuppressLint("MissingPermission")
    private void setDigestData() {
        if (mStartOta && mIndicate) {
            if (ota7000Chara != null) {
                ota7000Chara.setValue(fileUtils.getDigestBytes1());
                int properties = ota7000Chara.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    //写,无回复 WRITE_TYPE_NO_RESPONSE
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                boolean writeSuccess = mBluetoothGatt.writeCharacteristic(ota7000Chara);
                if (!writeSuccess) {
                    onError(ERROR_ON_START);
                }
            }
        }
    }

    /**
     * 发送OTA文件的摘要信息数据第二包
     */
    @SuppressLint("MissingPermission")
    private void setDigestData1() {
        if (mStartOta && mIndicate) {
            if (ota7000Chara != null) {
                ota7000Chara.setValue(fileUtils.getDigestBytes2());
                int properties = ota7000Chara.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    //写,无回复 WRITE_TYPE_NO_RESPONSE
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                boolean writeSuccess = mBluetoothGatt.writeCharacteristic(ota7000Chara);
                if (!writeSuccess) {
                    onError(ERROR_ON_START);
                }
            }
        }
    }

    /**
     * 开始命令数据包
     */
    @SuppressLint("MissingPermission")
    private void setStartOtaInfo() {
        byte[] bytes = new byte[13];
        bytes[0] = 0x02;
        bytes[1] = 0x00;
        bytes[2] = (byte) 0x80;
        bytes[3] = 0x05;
        bytes[4] = 0x18;
        int numberOfBytes = (int) getFileSize(mFilePath);
        bytes[5] = (byte) numberOfBytes;
        bytes[6] = (byte) (numberOfBytes >> 8);
        bytes[7] = (byte) (numberOfBytes >> 16);
        bytes[8] = (byte) (numberOfBytes >> 24);
        int dataLength = 513;
        bytes[9] = (byte) dataLength;
        bytes[10] = (byte) (dataLength >> 8);
        bytes[11] = (byte) (dataLength >> 16);
        bytes[12] = (byte) (dataLength >> 24);
        if (mStartOta && mIndicate) {
            if (ota7000Chara != null) {
                ota7000Chara.setValue(bytes);
                int properties = ota7000Chara.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    //写,无回复 WRITE_TYPE_NO_RESPONSE
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    ota7000Chara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                mBluetoothGatt.writeCharacteristic(ota7000Chara);
//                boolean writeSuccess = mBluetoothGatt.writeCharacteristic(ota7000Chara);
            }
        }

    }

    /**
     * 获取文件大小
     *
     * @param filePath
     * @return
     */
    private long getFileSize(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return file.length();
        } else {
            return 0;
        }
    }

    /**
     * 是否为最后一块
     */
    private boolean lastBlock = false;

    private int getChunkCounter() {
        return chunkCounter;
    }


    /**
     * 错误后是否停止
     */
    private boolean hasError = false;

    /**
     * OTA升级完成
     */
    private void onSuccess() {
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

    /**
     * 是否发生错误停止
     */
    private boolean isHasError() {
        return hasError;
    }

    /**
     * 设置停止
     */
    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }


    public void setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
        mOnBleOTAListener = onBleOTAListener;
    }

    private Handler threadHandler = new Handler(Looper.getMainLooper());


    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            threadHandler.post(runnable);
        }
    }

}

