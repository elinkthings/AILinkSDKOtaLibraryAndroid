package com.elinkthings.bleotalibrary.dialog;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.listener.OnCharacteristicListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;


/**
 * Dialog OTA
 */
public class DialogOtaManager implements OnCharacteristicListener {


    private final static String TAG = DialogOtaManager.class.getSimpleName();

    public final static String OTA_STEP = "OTA_STEP";
    public final static String OTA_ERROR = "OTA_ERROR";
    public final static String OTA_MEMDEV_VALUE = "OTA_MEMDEV_VALUE";

    public final static String ERROR_SEND_IMG_STR = "Send img error.";

    public final static int IC_TYPE_585 = 1;
    public final static int IC_TYPE_580 = 2;
    public final static int IC_TYPE_531 = 3;


    private BluetoothGatt mBluetoothGatt;
    private OnBleOTAListener mOnBleOTAListener;

    private HashMap errors;
    public static final int ERROR_COMMUNICATION = 0xffff; // ble communication error
    public static final int ERROR_SUOTA_NOT_FOUND = 0xfffe; // mSuota service was not found
    public static final int ERROR_READ_FILE = 0xfffd; // read fileUtils error
    public static final int ERROR_ON_START = 0xfffc; // Work can't upgrade
    public static final int ERROR_LOW_POWER = 0xfffb; // Low power can't upgrade
    public static final int ERROR_SEND_IMG = 0xfffa; // Write Characteristic error

    public static final UUID UUID_OTA_SERVICE = UUID.fromString("0000fef5-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_OTA_NOTIFY = UUID.fromString("5f78df94-798c-46f5-990a-b3eb6a065c88");

    public static final UUID OTA_MEM_DEV_UUID = UUID.fromString("8082caa8-41a6-4021-91c6-56f9b954cc34");
    public static final UUID OTA_GPIO_MAP_UUID = UUID.fromString("724249f0-5eC3-4b5f-8804-42345af08651");
    public static final UUID OTA_PATCH_LEN_UUID = UUID.fromString("9d84b9a3-000c-49d8-9183-855b673fda31");
    public static final UUID OTA_PATCH_DATA_UUID = UUID.fromString("457871e8-d516-4ca1-9116-57d0b17b9cb2");
    public static final UUID OTA_SERV_STATUS_UUID = UUID.fromString("5f78df94-798c-46f5-990a-b3eb6a065c88");
    private static final UUID DESCR_TWO = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGattCharacteristic otaMemDevChara, otaGpioMapChara, otaPatchLenChara, otaPatchDataChara, otaServStatusChara;

    private BleDialogOtaUtils fileUtils;
    private int gpioMapPrereq = 0;
    /**
     * 是否支持OTA
     */
    private boolean mVerifySupport = false;
    private String mFilePath = "";
    private int mIcType = IC_TYPE_585;
    private BleDevice mBleDevice;


    private DialogOtaManager(Builder builder) {
        this.mBleDevice = builder.mBleDevice;
        if (mBleDevice != null) {
            this.mBluetoothGatt = mBleDevice.getBluetoothGatt();
        }
        this.mFilePath = builder.mFilePath;
        this.mIcType = builder.mIcType;
        this.mOnBleOTAListener = builder.mOnBleOTAListener;
        initErrorMap();
        BluetoothGattService suota = mBluetoothGatt.getService(UUID_OTA_SERVICE);
        if (suota != null) {
            otaMemDevChara = suota.getCharacteristic(OTA_MEM_DEV_UUID);
            otaGpioMapChara = suota.getCharacteristic(OTA_GPIO_MAP_UUID);
            otaPatchLenChara = suota.getCharacteristic(OTA_PATCH_LEN_UUID);
            otaPatchDataChara = suota.getCharacteristic(OTA_PATCH_DATA_UUID);
            otaServStatusChara = suota.getCharacteristic(OTA_SERV_STATUS_UUID);
            mVerifySupport = true;
            if (mBleDevice != null)
                mBleDevice.setNotify(UUID_OTA_SERVICE, UUID_OTA_NOTIFY);
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
        private int mIcType = IC_TYPE_585;

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

        public Builder setIcType(int icType) {
            mIcType = icType;
            return this;
        }

        public DialogOtaManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            return new DialogOtaManager(this);
        }
    }


    /**
     * 是否支持OTA
     */
    public boolean isVerifySupport() {
        return mVerifySupport;
    }


    /**
     * 最后执行开始升级
     */
    public void startOta() {
        this.startOta(mFilePath, mIcType);
    }

    /**
     * 初始化,开始OTA
     */
    public void startOta(String fileName, int icType) {
        if (!mVerifySupport || mBleDevice == null) {
            return;
        }
        mBleDevice.setOnCharacteristicListener(this);
        switch (icType) {
            case IC_TYPE_585:
                init585();
                break;
            case IC_TYPE_531:
                init531();
                break;
            case IC_TYPE_580:
                init580();
                break;
        }

        setFileName(fileName);
        setOtaMemDev();

    }


    private void setFileName(String fileName) {
        try {
            if (!TextUtils.isEmpty(fileName)) {
                fileUtils = BleDialogOtaUtils.getByFileName(fileName);
                fileUtils.setFileBlockSize(240);
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
        // Value zero must not be used !! Notifications are sent when status changes.
        errors.put(0x03, "Forced exit of SPOTA service. See Table 1");
        errors.put(0x04, "Patch Data CRC mismatch.");
        errors.put(0x05, "Received patch Length not equal to PATCH_LEN characteristic value.");
        errors.put(0x06, "External Memory Error. Writing to external device failed.");
        errors.put(0x07, "Internal Memory Error. Not enough internal memory space for patch.");
        errors.put(0x08, "Invalid memory device.");
        errors.put(0x09, "Application error.");

        // SUOTAR application specific error codes
        errors.put(0x11, "Invalid image bank");
        errors.put(0x12, "Invalid image header");
        errors.put(0x13, "Invalid image size");
        errors.put(0x14, "Invalid product header");
        errors.put(0x15, "Same Image Error");
        errors.put(0x16, "Failed to read from external memory device");

        // Application error codes
        errors.put(ERROR_SEND_IMG, ERROR_SEND_IMG_STR);
        errors.put(ERROR_LOW_POWER, "Low power can't upgrade.");
        errors.put(ERROR_ON_START, "Work can't upgrade.");
        errors.put(ERROR_READ_FILE, "Read fileUtils error.");
        errors.put(ERROR_COMMUNICATION, "Communication error.");
        errors.put(ERROR_SUOTA_NOT_FOUND, "The remote device does not support SUOTA.");
    }


    /**
     * 设置监听OTA升级的Notification,在BleDevice中设置了
     */
    public void enableOTANotification() {
        if (mBluetoothGatt != null && otaServStatusChara != null) {
            mBluetoothGatt.setCharacteristicNotification(otaServStatusChara, true);
            BluetoothGattDescriptor descriptor = otaServStatusChara.getDescriptor(DESCR_TWO);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean writeSuccess = mBluetoothGatt.writeDescriptor(descriptor);
            if (!writeSuccess) {
                onError(ERROR_SEND_IMG);
            }
        }
    }


    @Override
    public void onCharacteristicReadOK(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onCharacteristicWriteOK(BluetoothGattCharacteristic characteristic) {
        onWriteDataOk(characteristic);
    }

    @Override
    public void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        if (uuid.equals(DialogOtaManager.OTA_SERV_STATUS_UUID)) {
            //OTA升级返回的通知
            onNotifyData(characteristic);
        }

    }

    /**
     * 通知返回数据
     */
    public final void onNotifyData(BluetoothGattCharacteristic characteristic) {
        int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        int step = -1;
        int error = -1;
        int memDevValue = -1;

        if (value == 0x10) {// Set memtype callback
            step = 3;
        } else if (value == 0x02) {// Successfully sent a block, send the next one
            step = 5;
        } else if (value == 0x03 || value == 0x01) {
            memDevValue = value;
        } else {
            error = value;
        }
        if ((step >= 0 || error >= 0 || memDevValue >= 0)) {
            Intent intent = new Intent();
            intent.putExtra(DialogOtaManager.OTA_STEP, step);
            intent.putExtra(DialogOtaManager.OTA_ERROR, error);
            intent.putExtra(DialogOtaManager.OTA_MEMDEV_VALUE, memDevValue);
            processStep(intent);
        }
    }


    public void onWriteDataOk(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(DialogOtaManager.OTA_MEM_DEV_UUID)) {
            setStep(3);
        } else if (characteristic.getUuid().equals(DialogOtaManager.OTA_GPIO_MAP_UUID)) {
            setStep(4);
        } else if (characteristic.getUuid().equals(DialogOtaManager.OTA_PATCH_LEN_UUID)) {
            setStep(5);
        } else if (characteristic.getUuid().equals(DialogOtaManager.OTA_PATCH_DATA_UUID)) {
            if (getChunkCounter() != -1 && !isHasError()) {
                sendBlock();
            }
        }
    }


    private void setStep(int step) {
        Intent intent = new Intent();
        intent.putExtra(DialogOtaManager.OTA_STEP, step);
        processStep(intent);
    }


    private int step;

    private void processStep(Intent intent) {
        int newStep = intent.getIntExtra(OTA_STEP, -1);
        int error = intent.getIntExtra(OTA_ERROR, -1);
        int memDevValue = intent.getIntExtra(OTA_MEMDEV_VALUE, -1);
        if (error != -1) {
            onError(error);
        } else if (memDevValue >= 0) {
            processMemDevValue(memDevValue);
        }
        // If a step is set, change the global step to this value
        if (newStep >= 0) {
            this.step = newStep;
        }
        switch (this.step) {
            case 0:
                this.step = -1;
                break;
            case 1:// Enable notifications
                enableOTANotification();
                break;
            case 2:// Init mem type
                setOtaMemDev();
                //activity.fileListView.setVisibility(View.GONE);
                break;
            // Set mem_type for SPOTA_GPIO_MAP_UUID
            case 3:
                // After setting SPOTAR_MEM_DEV and SPOTAR_IMG_STARTED notification is received,
                // we must set the GPIO map.
                // The order of the callbacks is unpredictable, so the notification may be
                // received before the write response.
                // We don't have a GATT operation queue, so the SPOTA_GPIO_MAP write will fail if
                // the SPOTAR_MEM_DEV hasn't finished yet.
                // Since this call is synchronized, we can wait for both broadcast intents from
                // the callbacks before proceeding.
                // The order of the callbacks doesn't matter with this implementation.
                if (++gpioMapPrereq == 2)
                    setOtaGpioMap();
                break;
            // Set SPOTA_PATCH_LEN_UUID
            case 4:
                setPatchLength();
                break;
            // Send a block containing blocks of 20 bytes until the patch length (default 240)
            // has been reached
            // Wait for response and repeat this action
            case 5:
                if (!lastBlock) {
                    sendBlock();
                } else {
                    if (!preparedForLastBlock) {
                        setPatchLength();
                    } else if (!lastBlockSent) {
                        sendBlock();
                    } else if (!endSignalSent) {
                        sendEndSignal();
                    } else if (error == -1) {
                        onSuccess();
                    }
                }
                break;
        }
    }

    private void processMemDevValue(int memDevValue) {
        if (step == 2) {
            if (memDevValue == 0x1) {
                goToStep(3);
            } else {
                onError(0);
            }
        }
    }

    private void goToStep(int step) {
        Intent i = new Intent();
        i.putExtra(OTA_STEP, step);
        processStep(i);
    }

    private static final int MEMORY_TYPE_EXTERNAL_SPI = 0x13;
    //585
    private static int IMAGE_BANK = 0;
    private static int MISO_GPIO = 0x05;
    private static int MOSI_GPIO = 0X06;
    private static int CS_GPIO = 0x03;
    private static int SCK_GPIO = 0x00;


    private void init585() {
        MISO_GPIO = 0x05;
        MOSI_GPIO = 0X06;
        CS_GPIO = 0x03;
        SCK_GPIO = 0x00;
    }

    private void init531() {
        MISO_GPIO = 0x03;
        MOSI_GPIO = 0X00;
        CS_GPIO = 0x01;
        SCK_GPIO = 0x04;
    }

    private void init580() {
        MISO_GPIO = 0x05;
        MOSI_GPIO = 0X06;
        CS_GPIO = 0x03;
        SCK_GPIO = 0x00;
    }


    /**
     * 写入OTA的升级包头//第一步
     */
    private void setOtaMemDev() {
        int memType = (MEMORY_TYPE_EXTERNAL_SPI << 24) | IMAGE_BANK;
        if (otaMemDevChara != null) {
            otaMemDevChara.setValue(memType, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(otaMemDevChara);
            if (!writeSuccess) {
                onError(ERROR_SEND_IMG);
            }
        }
    }

    /**
     * 写入OTA的升级包索引//第二步
     */
    private void setOtaGpioMap() {
        int memInfoData = this.getMemParamsSPI();
        if (otaGpioMapChara != null) {
            otaGpioMapChara.setValue(memInfoData, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(otaGpioMapChara);
            if (!writeSuccess) {
                onError(ERROR_SEND_IMG);
            }
        }
    }


    /**
     * 0x05060300 when
     * mem_type:        "External SPI" (0x13)
     * MISO GPIO:       P0_5 (0x05)
     * MOSI GPIO:       P0_6 (0x06)
     * CS GPIO:         P0_3 (0x03)
     * SCK GPIO:        P0_0 (0x00)
     * image_bank:      "Oldest" (value: 0)
     */
    private int getMemParamsSPI() {
        return (MISO_GPIO << 24) | (MOSI_GPIO << 16) | (CS_GPIO << 8) | SCK_GPIO;
    }


    private static final int END_SIGNAL = 0xfe000000;
    private static final int REBOOT_SIGNAL = 0xfd000000;

    private boolean endSignalSent = false;

    /**
     * 发送OTA升级结束
     */
    private void sendEndSignal() {
        if (otaMemDevChara != null) {
            otaMemDevChara.setValue(END_SIGNAL, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(otaMemDevChara);
            if (!writeSuccess) {
                onError(ERROR_SEND_IMG);
            }
            endSignalSent = true;
        }
    }

    private int mSendRebootSignalErr = 0;

    /**
     * 发送重启指令
     */
    public void reboot() {
        runOnMainThread(() -> {
            if (otaMemDevChara != null) {
                otaMemDevChara.setValue(REBOOT_SIGNAL, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
                boolean b = mBluetoothGatt.writeCharacteristic(otaMemDevChara);
                mSendRebootSignalErr++;
                if (b) {
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                    }
                } else {
                    if (mSendRebootSignalErr < 3) {
                        reboot();
                    }
                }
            }
        });
    }

    /**
     * 是否为最后一块
     */
    private boolean lastBlock = false;
    private boolean preparedForLastBlock = false;

    /**
     * 设置OTA升级包的文件大小和数据块信息//第三步
     */
    private void setPatchLength() {
        int blocksize = fileUtils.getFileBlockSize();
        if (lastBlock) {
            blocksize = this.fileUtils.getNumberOfBytes() % fileUtils.getFileBlockSize();
            preparedForLastBlock = true;
        }
        if (otaPatchLenChara != null) {
            otaPatchLenChara.setValue(blocksize, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            boolean writeSuccess = mBluetoothGatt.writeCharacteristic(otaPatchLenChara);
            if (!writeSuccess) {
                onError(ERROR_SEND_IMG);
            }
        }
    }

    private int blockCounter = 0;
    private int chunkCounter = -1;
    /**
     * 最后一块是否已发送
     */
    private boolean lastBlockSent = false;

    private int getChunkCounter() {
        return chunkCounter;
    }

    /**
     * 发送OTA升级包的数据块
     */
    private synchronized void sendBlock() {
        final float progress = ((float) (blockCounter + 1) / (float) fileUtils.getNumberOfBlocks()) * 100;
        if (!lastBlockSent) {
            byte[][] block = fileUtils.getBlock(blockCounter);

            int i = ++chunkCounter;
            boolean lastChunk = false;
            if (chunkCounter == block.length - 1) {
                chunkCounter = -1;
                lastChunk = true;
            }
            byte[] chunk = block[i];

//            final int chunkNumber = (blockCounter * fileUtils.getChunksPerBlockCount()) + i + 1;
//            final String message = "Sending chunk " + chunkNumber + " of " + fileUtils
//                    .getTotalChunkCount() + " (with " + chunk.length + " bytes)";
//            BleLog.i(TAG, message);
//            String systemLogMessage =
//                    "Sending block " + (blockCounter + 1) + ", chunk " + (i + 1) + ", blocksize: "
//                            + block.length + ", chunksize " + chunk.length;
//            BleLog.i(TAG, systemLogMessage);
            if (otaPatchDataChara != null) {
                otaPatchDataChara.setValue(chunk);
                otaPatchDataChara.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                boolean writeSuccess = mBluetoothGatt.writeCharacteristic(otaPatchDataChara);
                if (!writeSuccess) {
                    onError(ERROR_SEND_IMG);
                }
            }

            if (lastChunk) {
                if (!lastBlock) {
                    blockCounter++;
                } else {
                    lastBlockSent = true;
                }
                if (blockCounter + 1 == fileUtils.getNumberOfBlocks()) {
                    lastBlock = true;
                }
            }
        }
        runOnMainThread(() -> {
            if (mOnBleOTAListener != null)
                mOnBleOTAListener.onOtaProgress(progress, 1, 1);
        });

    }

    /**
     * 是否错误
     */
    private boolean hasError = false;

    /**
     * OTA升级完成
     */
    private void onSuccess() {
        runOnMainThread(() -> {
            if (mOnBleOTAListener != null)
                mOnBleOTAListener.onOtaSuccess();
        });
        reboot();
        if (fileUtils != null)
            fileUtils.close();

    }

    private void onError(int errorCode) {
        if (!hasError) {
            String error = (String) errors.get(errorCode);
            Log.e(TAG, "Error: " + errorCode + " " + error);
            runOnMainThread(() -> {
                if (mOnBleOTAListener != null)
                    mOnBleOTAListener.onOtaFailure(errorCode, error);
            });

            hasError = true;
            if (fileUtils != null)
                fileUtils.close();
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
