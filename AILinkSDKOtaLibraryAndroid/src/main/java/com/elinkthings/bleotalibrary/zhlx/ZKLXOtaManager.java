package com.elinkthings.bleotalibrary.zhlx;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.elinkthings.bleotalibrary.base.BaseOtaManager;
import com.elinkthings.bleotalibrary.config.OtaConfig;
import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.utils.BleLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * xing<br>
 * 2023/02/06<br>
 * 富芮坤手表OTA管理类
 */
public class ZKLXOtaManager extends BaseOtaManager implements OtaManager.EventListener {


    private final static int OTA_TIME_OUT = 1;

    /**
     * 超时时间
     */
    private final static int TIME_OUT_TIME = 10 * 1000;
    private final static String TAG = "ZKLXOtaManager";
    private OnBleOTAListener mOnBleOTAListener;
    private String mFilePath;
    private BleOtaManager mBleOtaManager;
    private Context mContext;
    /**
     * 是否已开启OTA,用于初始化成功后是否进行OTA的判断
     */
    private boolean mStartOta = false;
    /**
     * ota状态
     */
    private boolean mOtaStatus = false;
    /**
     * 初始化OK
     */
    private boolean mInitOk = false;


    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OTA_TIME_OUT:
                    //OTA超时
                    mHandler.removeMessages(OTA_TIME_OUT);
                    otaFailure("ota time out");
                    break;
            }
        }
    };

    public ZKLXOtaManager(Builder builder) {
        mContext = builder.mContext;
        mOnBleOTAListener = builder.mOnBleOTAListener;
        mFilePath = builder.mFilePath;
        BleDevice bleDevice = builder.mBleDevice;
        if (bleDevice == null || bleDevice.getBluetoothGatt() == null) {
            otaFailure("bleDevice == null || bleDevice.getBluetoothGatt() == null");
            return;
        }
        BluetoothDevice bluetoothDevice = bleDevice.getBluetoothGatt().getDevice();
        if (bluetoothDevice != null) {
            bleDevice.disconnect(false);
            mBleOtaManager = new BleOtaManager(mContext, bluetoothDevice, this);
            mBleOtaManager.init();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] otaData = readFile(mFilePath);
                        mBleOtaManager.setOtaData(otaData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }


    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    public final static class Builder {
        private BleDevice mBleDevice;
        private Context mContext;
        private OnBleOTAListener mOnBleOTAListener;
        private String mFilePath = "";

        public Builder(Context context) {
            mContext = context;
        }


        public Builder setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
            mOnBleOTAListener = onBleOTAListener;
            return this;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }


        public ZKLXOtaManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            if (mBleDevice == null) {
                return null;
            }
            return new ZKLXOtaManager(this);
        }
    }

    /**
     * 读取文件，供OTA使用。因OTA文件较小，所以一次性读取。
     *
     * @param filePath 文件路径
     * @return 返回文件字节数组
     * @throws IOException 文件操作可能产生的IO异常
     */
    public byte[] readFile(String filePath) throws IOException {
        File file = new File(filePath);
        FileInputStream fis = new FileInputStream(file);

        int length = fis.available();
        byte[] buffer = new byte[length];
        fis.read(buffer);

        fis.close();
        return buffer;
    }


    /**
     * 开始OTA
     */
    @Override
    public void startOta() {
        mStartOta = true;
        if (mInitOk) {
            if (mBleOtaManager != null && !mOtaStatus) {
                new Thread(() -> {
                    BleLog.iw("开始OTA");
                    while (!mBleOtaManager.isReadyToUpdate()) {
                        //未准备好,100ms后再检测一次
                        SystemClock.sleep(100);
                    }
                    mBleOtaManager.startOTA();
                    mHandler.removeMessages(OTA_TIME_OUT);
                    mHandler.sendEmptyMessageDelayed(OTA_TIME_OUT, TIME_OUT_TIME);
                }).start();
            }
        }else {
            mHandler.sendEmptyMessageDelayed(OTA_TIME_OUT, TIME_OUT_TIME*6);
        }


    }


    private void otaFailure(String msg) {
        mOtaStatus = false;
        mHandler.removeMessages(OTA_TIME_OUT);
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_FAIL, msg);
        }
        clear();
    }


    @Override
    public void onOtaReady() {
        BleLog.i("onOtaReady");
        mInitOk = true;
        if (mStartOta) {
            if (mHandler != null) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startOta();
                    }
                }, 1000);
            }

        }
    }

    @Override
    public void onOtaStart() {
        BleLog.i("onOtaStart");
        if (mHandler.hasMessages(OTA_TIME_OUT)) {
            mHandler.removeMessages(OTA_TIME_OUT);
        }
    }

    @Override
    public void onOtaProgress(int progress) {
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaProgress(progress, 1, 1);
        }
    }

    @Override
    public void onOtaStop() {
        BleLog.i("onOtaStop");
        otaFailure("onOtaStop");
    }

    @Override
    public void onOtaOneFinish() {
        BleLog.i("onOtaOneFinish:主机升级完成，等待连接副机");
    }

    @Override
    public void onOtaAllFinish() {
        BleLog.i("onOtaAllFinish");
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaSuccess();
        }
        clear();
    }

    @Override
    public void onOtaPause() {
        BleLog.i("onOtaPause");
    }

    @Override
    public void onOtaContinue() {
        BleLog.i("onOtaContinue");
    }

    @Override
    public void onOtaError(OtaError error) {
        BleLog.i("onOtaError:" + error.getDescription());
        otaFailure(error.getDescription());
    }

    @Override
    public void onTWSDisconnected() {
        BleLog.i("onTWSDisconnected");
        otaFailure("onTWSDisconnected");
    }

    @Override
    public void onReceiveVersion(int version) {
        BleLog.i("onReceiveVersion:" + version);
    }

    @Override
    public void onReceiveIsTWS(boolean isTWS) {
        BleLog.i("onReceiveIsTWS:" + isTWS);
    }

    @Override
    public void onReceiveTWSConnected(boolean connected) {
        BleLog.i("onReceiveTWSConnected:" + connected);
    }

    @Override
    public void onReceiveChannel(boolean isLeftChannel) {
        BleLog.i("onReceiveChannel:" + isLeftChannel);
    }

    @Override
    public void clear() {
        mHandler.removeMessages(OTA_TIME_OUT);
        if (mBleOtaManager!=null) {
            mBleOtaManager.release();
            mBleOtaManager=null;
        }
        mInitOk = false;
        mStartOta = false;
        mOtaStatus = false;

        BleLog.i("clear");
    }


}
