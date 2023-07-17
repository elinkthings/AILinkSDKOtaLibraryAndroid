package com.elinkthings.bleotalibrary.zhlx;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;


import com.pingwang.bluetoothlib.utils.BleLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class OtaManager {

    private static final String TAG = OtaManager.class.getSimpleName();

    // 因为固件那边埋下的坑，加上这个标志。此处留作说明，客户无需理会。
    // 旧版固件采用主从切换升级方式，有客户已经用了，但是新版固件
    // 使用的是主副耳同时升级。由于新版中不会发送声道信息，所以为
    // 了在库中区分不同的升级方式，使用这个标志作为Workaround。
    // 加入此值，配合isPrimaryUpdated使用。
    // receivedChannelInfo = false时，使用的是新版固件（同时升级）；
    // receivedChannelInfo = true时，使用的是旧版固件（主从切换）。
    private boolean receivedChannelInfo = false;

    private static final int DEFAULT_TIMEOUT = 10000; // in ms, 10s
    private static final int UNDEFINED_FIRMWARE_VERSION = 0xFFFFFFFF;

    final protected Context context;
    final private EventListener eventListener;

    final private Handler notifyHandler;
    final private Handler timeoutHandler;

    protected BluetoothDevice device; // 子类BleOtaManager在TWS升级时会重新赋值

    protected OtaDataProvider dataProvider;
    protected OtaCommandGenerator commandGenerator;
    protected int otaFirmwareVersion = UNDEFINED_FIRMWARE_VERSION;
    protected int deviceFirmwareVersion = UNDEFINED_FIRMWARE_VERSION;
    protected boolean allowedUpdate;

    private int mBlockSize = OtaConstants.DEFAULT_BLOCK_SIZE;
    private int mPacketSize = OtaConstants.DEFAULT_PACKET_SIZE;

    protected boolean isUpdating = false;
    protected boolean isUpdatePause = false;

    // TWS升级时使用，释放时不需要重置
    protected boolean isPrimaryUpdated = false;
    protected Boolean isTwsDevice = null; // 使用类是留给如果设备没有上传信息这种情况
    protected Boolean isTwsConnected = null;

    // 蓝牙地址，BLE升级使用
    protected byte[] bluetoothAddress;

    private boolean isDeviceReady;

    public boolean disconnectedDueToDeviceError = false; // 报错后断开不再调用onStop

    // 识别
    private boolean _needIdentification = true;
    protected boolean sentIdentification = false;
    protected static final int DELAY_AFTER_SEND_IDENTIFICATION = 200; // ms

    /* Constructor */

    /**
     * 构造器
     *
     * @param context       {@link Context}
     * @param device        需要进行升级的蓝牙设备{@link BluetoothDevice}
     * @param eventListener 事件监听器{@link EventListener}
     */
    public OtaManager( Context context,  BluetoothDevice device,  EventListener eventListener) {
        this.context = context;
        this.device = device;
        this.eventListener = eventListener;

        this.notifyHandler = new Handler(Looper.getMainLooper());
        this.timeoutHandler = new Handler(Looper.getMainLooper());

        this.commandGenerator = new OtaCommandGenerator();
    }

    /* Public */

    /**
     * OTA Manager初始化，在进行OTA升级之前必须进行初始化。
     */
    public abstract void init();

    /**
     * 释放资源，包括断开蓝牙BLE和SPP连接。
     * 通常在升级完成、遇到错误，或者超时的时候，会自动释放资源，但是如果需要中断升级，亦可手动调用。
     */
    public void release() {
        otaFirmwareVersion = UNDEFINED_FIRMWARE_VERSION;
        deviceFirmwareVersion = UNDEFINED_FIRMWARE_VERSION;
        allowedUpdate = false;
        isUpdating = false;
        isUpdatePause = false;
        sentIdentification = false;
        receivedChannelInfo = false;
        cancelTimeout();
        // 在蓝牙断开的地方调用
//        if (dataProvider != null) {
//            dataProvider.reset();
//        }
        if (commandGenerator!=null) {
            commandGenerator.reset();
        }
    }

    protected int getPacketSize() {
        return mPacketSize;
    }

    protected int getBlockSize() {
        return mBlockSize;
    }

    /**
     * 开始进行OTA升级，升级之前必须先判断{@link #isReadyToUpdate()}。
     */
    public void startOTA() {
        isUpdating = true;
        notifyOnStart();
        // 从获取版本号开始吧
        getOtaInfoVersion();
    }

    /**
     * 判断是否已经就绪。
     *
     * @return 是否已就绪
     */
    public boolean isReadyToUpdate() {
        Log.d(TAG, "isReady: " + isTwsDevice + ", " + isTwsConnected + ", " + (dataProvider != null) + ", " + isDeviceReady());
        // 一定需要getAllInfo有回复之后才能继续判断
        if (isTwsDevice == null) {
            return false;
        } else {
            if (isTwsDevice) {
                return isTwsConnected() && dataProvider != null && isDeviceReady();
            } else {
                return dataProvider != null && isDeviceReady();
            }
        }
    }

    protected abstract boolean canSendNow();

    /**
     * 设置OTA文件数据
     *
     * @param otaData OTA数据
     */
    public void setOtaData( byte[] otaData) {
        dataProvider = new OtaDataProvider(otaData);

        dataProvider.setBlockSize(getBlockSize());
        dataProvider.setPacketSize(getPacketSize());
        if (commandGenerator != null) {
            commandGenerator.setDataProvider(dataProvider);
        } else {
            commandGenerator = new OtaCommandGenerator(dataProvider);
        }
        checkIfReadyToUpdate();
    }

    /**
     * 是否正在进行升级
     *
     * @return 是否正在升级
     */
    public boolean isUpdating() {
        return isUpdating;
    }

    /**
     * 当前设备是否是TWS设备
     *
     * @return 是否TWS设备
     */
    public boolean isTwsDevice() {
        return isTwsDevice != null && isTwsDevice;
    }

    /**
     * TWS对耳是否已连接
     *
     * @return TWS是否已连接
     */
    public boolean isTwsConnected() {
        return isTwsConnected != null && isTwsConnected;
    }

    // 通知

    /**
     * 如果准备好了，就发出通知
     */
    protected void checkIfReadyToUpdate() {
        boolean isReadyToUpdate = isReadyToUpdate();
        BleLog.i("checkIfReadyToUpdate: " + isReadyToUpdate);
        if (isReadyToUpdate) {
            notifyOnReady();
        }
    }

    protected void notifyOnReady() {
        notifyHandler.post(eventListener::onOtaReady);
    }

    protected void notifyOnStart() {
        notifyHandler.post(eventListener::onOtaStart);
    }

    protected void notifyOnProgress(int progress) {
        notifyHandler.post(() -> eventListener.onOtaProgress(progress));
    }

    protected void notifyOnStop() {
        if (!disconnectedDueToDeviceError) {
            notifyHandler.post(eventListener::onOtaStop);
        }
        disconnectedDueToDeviceError = false;
    }

    protected void notifyOnOneFinish() {
        notifyHandler.post(eventListener::onOtaOneFinish);
    }

    protected void notifyOnAllFinish() {
        notifyHandler.post(eventListener::onOtaAllFinish);
    }

    protected void notifyOnPause() {
        notifyHandler.post(eventListener::onOtaPause);
    }

    protected void notifyOnContinue() {
        notifyHandler.post(eventListener::onOtaContinue);
    }

    protected void notifyOnError(OtaError error) {
        disconnectedDueToDeviceError = true;
        notifyHandler.post(() -> eventListener.onOtaError(error));
        // 停止升级，释放资源
        release();
    }

    protected void notifyTWSDisconnected() {
        notifyHandler.post(eventListener::onTWSDisconnected);
    }

    protected void notifyOnReceiveVersion(int version) {
        notifyHandler.post(() -> eventListener.onReceiveVersion(version));
    }

    protected void notifyOnReceiveIsTWS(boolean isTwsDevice) {
        notifyHandler.post(() -> eventListener.onReceiveIsTWS(isTwsDevice));
    }

    protected void notifyOnReceiveTWSConnected(boolean connected) {
        notifyHandler.post(() -> eventListener.onReceiveTWSConnected(connected));
    }

    protected void notifyOnReceiveChannel(boolean isLeftChannel) {
        notifyHandler.post(() -> eventListener.onReceiveChannel(isLeftChannel));
    }

    // 发送的命令

    protected void getOtaInfoVersion() {
        byte[] cmd = commandGenerator.cmdGetInfoVersion();
        btSendData(cmd);
    }

    protected void getOtaInfoUpdate(int version) {
        if (dataProvider != null) {
            byte[] hashData = dataProvider.getHash();
            byte[] cmd = commandGenerator.cmdGetInfoUpdate(version, hashData);
            btSendData(cmd);
        }
    }

    protected void sendOtaIdentification() {
        byte[] cmd = commandGenerator.cmdOtaIdentification();
        btSendData(cmd);
    }

    protected void getAllInfo() {
        byte[] cmd = commandGenerator.cmdGetAllInfo();
        btSendData(cmd);
    }

    protected void sendOtaStart() {
        byte[] cmd = commandGenerator.cmdStartSend();
        btSendData(cmd);
        // 进度
        int progress = dataProvider.getProgress();
        notifyOnProgress(progress);
    }

    /**
     * 发送OTA数据
     */
    protected void sendOtaData() {
        byte[] cmd = commandGenerator.cmdSendData();
        btSendData(cmd);
        // 进度
        int progress = dataProvider.getProgress();
        notifyOnProgress(progress);
    }


    /**
     * 发送相关
     */
    protected void sendOtaDataOnce() {
        sendOtaData();
    }

    protected void runDataSend() {
        // 如果不需要发送第一串识别码，或者已经发过识别码，则进入发送流程
        if (!needIdentification() || sentIdentification) {
            // 如果可以，就一直发
            if (canSendNow()) {
                sendOtaDataOnce();
            }
            // 发完块的最后一包后，就等待固件回复
            else if (dataProvider != null && dataProvider.isBlockSendFinish()) {
                // 等待固件回复
                timeoutHandler.postDelayed(this::handleTimeout, DEFAULT_TIMEOUT);
            }
        } else {
            // 延迟发送，等待固件准备环境
            HandlerThread thread = new HandlerThread("Get All Info");
            thread.start();
            new Handler(thread.getLooper()).postDelayed(() -> {
                sentIdentification = true;
                // 获取设备信息
                getAllInfo();
            }, DELAY_AFTER_SEND_IDENTIFICATION);
        }
    }

    /**
     * 处理超时
     */
    private void handleTimeout() {
        // 错误通知
        notifyOnError(OtaError.TIMEOUT_RECEIVE_RESPONSE);
    }

    /**
     * 取消超时
     */
    private void cancelTimeout() {
        timeoutHandler.removeCallbacksAndMessages(null);
    }


    /**
     * 处理数据
     *
     * @param data 数据
     * @return boolean
     */
    protected boolean processData(byte[] data) {
        if (data.length < 3) {
            Log.w(TAG, "接收到数据长度小于3");
            return false;
        }

        ByteBuffer bb = ByteBuffer.wrap(data);
        byte cmdType = bb.get();
        byte seqNum = bb.get(); // 暂时没使用

        switch (cmdType) {
            case OtaCommandGenerator.CMD_NOTIFY_STATUS:
                byte status = bb.get();
                processNotifyStatus(status);
                return true;
            case OtaCommandGenerator.CMD_GET_INFO:
                byte cmdSubType = bb.get();
                byte[] extraData = new byte[bb.remaining()];
                bb.get(extraData, 0, bb.remaining());
                processGetInfo(cmdSubType, extraData);
                return true;
            case OtaCommandGenerator.CMD_GET_INFO_TLV:
                byte[] infoData = new byte[bb.remaining()];
                bb.get(infoData, 0, bb.remaining());
                processGetInfoTLV(infoData);
                return true;
        }

        return false;
    }

    protected void processNotifyStatus(byte status) {
        switch (status) {
            case OtaCommandGenerator.STATE_OK: {
                cancelTimeout();
                // 如果没有暂停，且还没发完，继续发；发完了就等STATE_DONE
                if (!isUpdatePause && !dataProvider.isAllDataSent()) {
                    sendOtaStart();
                }
                break;
            }
            case OtaCommandGenerator.STATE_DONE: {
                cancelTimeout();
                // 如果是TWS，且现在升级的是主耳
                if (isTwsDevice() && (receivedChannelInfo && !isPrimaryUpdated)) {
                    isPrimaryUpdated = true;
                    notifyOnOneFinish();
                    onOneFinish();
                } else {
                    // DONE
                    notifyOnAllFinish();
                    release();
                }
                break;
            }
            case OtaCommandGenerator.STATE_PAUSE: {
                cancelTimeout();
                // 暂停
                allowedUpdate = false; // 清掉，不然重新开始的时候会接着之前状态发送数据
                isUpdating = false;
                isUpdatePause = true;
                notifyOnPause();
                break;
            }
            case OtaCommandGenerator.STATE_CONTINUE: {
                notifyOnContinue();
                // 恢复
                isUpdating = true;
                isUpdatePause = false;
                getOtaInfoVersion();
                break;
            }
            case OtaCommandGenerator.STATE_TWS_DISCONNECTED: {
                cancelTimeout();
                isUpdating = false;
                isTwsConnected = false;
                notifyTWSDisconnected();
                notifyOnReceiveTWSConnected(isTwsConnected);
                break;
            }

            default: {
                OtaError.REPORT_FROM_DEVICE.setDeviceErrorCode(status);
                cancelTimeout();
                notifyOnError(OtaError.REPORT_FROM_DEVICE);
                break;
            }
        }
    }

    protected void processGetInfo(byte cmdSubType, byte[] extraData) {
        // 处理命令
        processInfo(cmdSubType, extraData);

        // 处理完命令后的操作
        switch (cmdSubType) {
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_VERSION: {
                getOtaInfoUpdate(otaFirmwareVersion);
                break;
            }
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_UPDATE: {
                // 地址+是否升级
                if (allowedUpdate) {
                    // 这里开始发第一包
                    sendOtaStart();
                } else {
                    cancelTimeout();
                    notifyOnError(OtaError.REFUSED_BY_DEVICE);
                }
            }
        }
    }

    private void processGetInfoTLV(byte[] extraData) {
        while (extraData.length > 2) {
            ByteBuffer bb = ByteBuffer.wrap(extraData);
            byte infoType = bb.get();
            byte infoLength = bb.get();
            byte[] infoData = new byte[infoLength];
            bb.get(infoData);
            processInfo(infoType, infoData);

            // 处理剩下的数据
            if (bb.hasRemaining()) {
                byte[] remainingData = new byte[bb.remaining()];
                bb.get(remainingData);
                extraData = remainingData;
                continue;
            }
            break;
        }

        // 判断是否可以开始升级
        // 如果是TWS，且是副耳，则自动开始升级
        if (isReadyToUpdate() && isTwsDevice() && (receivedChannelInfo && isPrimaryUpdated)) {
            startOTA();
        } else {
            checkIfReadyToUpdate();
        }
    }

    // todo: 拆分
    // 处理设备回传信息
    private void processInfo(byte infoType, byte[] infoData) {
        Log.d(TAG, "processInfo: " + infoType + " -> " + HexUtils.bytesToHex(infoData));

        switch (infoType) {
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_VERSION: {
                if (infoData.length == 2) {
                    ByteBuffer bb = ByteBuffer.wrap(infoData).order(ByteOrder.LITTLE_ENDIAN);
                    int version = bb.getShort();
                    notifyOnReceiveVersion(version);

                    deviceFirmwareVersion = version;
                }
                break;
            }
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_UPDATE: {
                if (infoData.length == 11) {
                    ByteBuffer bb = ByteBuffer.wrap(infoData).order(ByteOrder.LITTLE_ENDIAN);
                    // 升级起始地址
                    int startAddress = bb.getInt();
                    dataProvider.setStartAddress(startAddress);
                    // 升级块大小
                    int blockSize = bb.getInt();
                    mBlockSize = blockSize;
                    dataProvider.setBlockSize(blockSize);
                    // 包最大长度
                    int packetSize = bb.getShort();
                    mPacketSize = packetSize;
                    dataProvider.setPacketSize(packetSize);

                    // 设备是否允许升级
                    allowedUpdate = (bb.get() == 1);
                }
                break;
            }
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_CAPABILITIES: {
                // 设备能力
                if (infoData.length == 2) {
                    ByteBuffer bb = ByteBuffer.wrap(infoData).order(ByteOrder.LITTLE_ENDIAN);
                    short dev_info_capabilities = bb.getShort();
                    // 是否是TWS，TWS状态
                    isTwsDevice = (dev_info_capabilities & OtaCommandGenerator.INFO_CAPABILITIES_TWS) != 0;
//                    Log.d(TAG, "isTWS: " + isTwsDevice);
                    notifyOnReceiveIsTWS(isTwsDevice);
                }
                break;
            }
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_STATUS:
                if (infoData.length == 2) {
                    ByteBuffer bb = ByteBuffer.wrap(infoData).order(ByteOrder.LITTLE_ENDIAN);
                    short device_status = bb.getShort();
                    // TWS是否已经连接
                    isTwsConnected = (device_status & OtaCommandGenerator.INFO_STATUS_TWS_CONNECTED) != 0;
//                    Log.d(TAG, "isTwsConnected: " + isTwsConnected);
                    notifyOnReceiveTWSConnected(isTwsConnected);
                }
                break;
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_ADDRESS: {
                // 蓝牙地址，BLE升级使用
                if (infoData.length == 6) {
                    bluetoothAddress = infoData;
                }
                break;
            }
            case OtaCommandGenerator.CMD_GET_INFO_TYPE_CHANNEL: {
                if (infoData.length == 1) {
                    byte channel = infoData[0];
                    if (channel == OtaCommandGenerator.INFO_CHANNEL_LEFT) {
//                        Log.d(TAG, "Channel: Left");
                        notifyOnReceiveChannel(true);
                    } else if (channel == OtaCommandGenerator.INFO_CHANNEL_RIGHT) {
//                        Log.d(TAG, "Channel: Right");
                        notifyOnReceiveChannel(false);
                    }
                    receivedChannelInfo = true;
                }
                break;
            }
            default:

                break;
        }
    }

    /**
     * 发送数据
     */
    protected abstract void btSendData(byte[] data);

    protected abstract void onOneFinish();

    /* Getter & Setter */

    public boolean needIdentification() {
        return _needIdentification;
    }

    /**
     * 是否需要发送识别码，默认是发送。
     * 3月之后发的固件均需要使用识别码；更早的固件不需要发送，需要设为false，否则会报0x40错误。
     */
    public void setNeedIdentification(boolean needIdentification) {
        this._needIdentification = needIdentification;
    }

    /**
     * 传入OTA固件版本
     *
     * @param otaFirmwareVersion 固件版本，0xFFFF表示强制升级（大于除本身外其他一切16位整数）
     */
    public void setOtaFirmwareVersion(int otaFirmwareVersion) {
        this.otaFirmwareVersion = otaFirmwareVersion;
    }

    protected boolean isDeviceReady() {
        return isDeviceReady;
    }

    protected void setDeviceReady(boolean deviceReady) {
        isDeviceReady = deviceReady;
    }

    /* Event Listener */

    /**
     * OTA事件监听器
     */
    public interface EventListener {

        /* OTA状态 */

        /**
         * OTA已经准备就绪，可以开始升级
         */
        void onOtaReady();

        /**
         * OTA已开始
         */
        void onOtaStart();

        /**
         * OTA进度
         *
         * @param progress 进度
         */
        void onOtaProgress(int progress);

        /**
         * OTA已停止
         */
        void onOtaStop();

        /**
         * TWS已完成一边升级，非TWS不会使用
         */
        void onOtaOneFinish();

        /**
         * OTA已全部完成
         */
        void onOtaAllFinish();

        /**
         * OTA已暂停
         */
        void onOtaPause();

        /**
         * OTA已继续
         */
        void onOtaContinue();

        /**
         * OTA遇到错误
         *
         * @param error {@link OtaError}
         */
        void onOtaError(OtaError error);

        /* 设备状态 */

        /**
         * TWS断开事件
         */
        void onTWSDisconnected();

        /**
         * 接收到设备当前固件版本号
         *
         * @param version 固件版本号，具体定义参见固件端说明文档
         */
        void onReceiveVersion(int version);

        /**
         * 是否TWS
         *
         * @param isTWS 是否TWS
         */
        void onReceiveIsTWS(boolean isTWS);

        /**
         * TWS对耳是否已连接
         *
         * @param connected 是否已连接
         */
        void onReceiveTWSConnected(boolean connected);

        /**
         * 声音左右通道，非TWS不用关注
         *
         * @param isLeftChannel true为左声道，false为右声道
         */
        void onReceiveChannel(boolean isLeftChannel);

    }

}
