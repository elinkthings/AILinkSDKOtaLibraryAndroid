package com.elinkthings.bleotalibrary.zhlx;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.pingwang.bluetoothlib.utils.BleLog;

public final class SppOtaManager extends OtaManager {

    private static final String TAG = SppOtaManager.class.getSimpleName();

    private static final int DELAY_SEND = 15; // ms

    private BluetoothSppService sppService;


    /* Constructor */

    public SppOtaManager(Context context,
                         BluetoothDevice device,
                         EventListener eventListener) {
        super(context, device, eventListener);
    }


    /* Public */

    @Override
    public void init() {
        sppService = new BluetoothSppService(new Handler(sppCallback));
        sppService.connect(device);
    }

    @Override
    public void release() {
        if (sppService != null) {
            sppService.stop();
            sppService = null;
        }
        super.release();
    }

    @Override
    public void startOTA() {
        if (sppService.getState() != BluetoothSppService.STATE_CONNECTED) {
            notifyOnError(OtaError.NOT_CONNECTED);
            return;
        }

        super.startOTA();
    }


    /* Private */

    @Override
    protected void sendOtaDataOnce() {
        // TODO: 临时加的延时，以后用什么别的方法换掉吧
        try {
            Thread.sleep(DELAY_SEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.sendOtaDataOnce();
    }

    private final Handler.Callback sppCallback = msg -> {
        switch (msg.what) {
            // 状态
            case BluetoothSppService.MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                    case BluetoothSppService.STATE_CONNECTING:
                        break;
                    case BluetoothSppService.STATE_CONNECTED:
                        setDeviceReady(true);
                        if (needIdentification()) {
                            // 发送OTA识别信息
                            sendOtaIdentification();
                        } else {
                            // 获取设备信息
                            getAllInfo();
                        }
                        // 如果是以前的设备，或者TWS设备还没提供TWS信息
                        if (isTwsDevice == null) {
                            checkIfReadyToUpdate();
                        }
                        break;
                    case BluetoothSppService.STATE_NONE:
                        BleLog.i("相当于断开后");
                        // 相当于断开后
                        setDeviceReady(false);
                        isUpdating = false;
                        // 在SPP中，主动断开也是一种错误
                        // 为了避免升级完成的断开被判断为错误
                        if (dataProvider == null || !dataProvider.isAllDataSent()) {
                            notifyOnStop();
                        } else {
                            dataProvider.reset();
                        }
                        break;
                }
                break;
            // 读取到数据
            case BluetoothSppService.MESSAGE_READ:
                // 处理接收到的数据
                byte[] readData = (byte[]) msg.obj;
                boolean ret = processData(readData);
                Log.d(TAG, "消息" + (ret ? "已" : "未") + "处理");
                break;
            case BluetoothSppService.MESSAGE_WRITE:
                // 这里当做是发送完成后的回调吧
                runDataSend();
                break;
        }
        return true;
    };

    @Override
    protected boolean canSendNow() {
        return allowedUpdate && !isUpdatePause && !dataProvider.isBlockSendFinish();
    }

    @Override
    protected void btSendData(byte[] data) {
        if (sppService != null) {
            Log.v(TAG, "btSendData: " + HexUtils.bytesToHex(data));
            sppService.write(data);
        }
    }

    @Override
    protected void onOneFinish() {
        // 断开并释放资源
        release();

        // 延时，保证设备主从切换完毕
        long delayTime = 3000;
        // 重新初始化连接
        new Handler().postDelayed(this::init, delayTime);
    }

}
