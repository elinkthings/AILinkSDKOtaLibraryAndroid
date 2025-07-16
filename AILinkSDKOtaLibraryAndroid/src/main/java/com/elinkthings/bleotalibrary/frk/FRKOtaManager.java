package com.elinkthings.bleotalibrary.frk;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.elinkthings.bleotalibrary.base.BaseOtaManager;
import com.elinkthings.bleotalibrary.config.OtaConfig;
import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.utils.BleLog;

/**
 * xing<br>
 * 2023/02/06<br>
 * 富芮坤手表OTA管理类
 */
public class FRKOtaManager extends BaseOtaManager implements FRBleCallBack {


    private final static int OTA_TIME_OUT = 1;

    /**
     * 超时时间
     */
    private final static int TIME_OUT_TIME = 20 * 1000;
    private final static String TAG = "FRKOtaManager";
    private OnBleOTAListener mOnBleOTAListener;
    private String mFilePath;
    private FrBleOTASDK mFrBleOTASDK;
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
                    otaFailure("FRKOtaManager: ota time out");
                    break;
            }
        }
    };

    public FRKOtaManager(Builder builder) {
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
            mFrBleOTASDK = new FrBleOTASDK(mContext, bluetoothDevice, this);
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


        public FRKOtaManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            if (mBleDevice == null) {
                return null;
            }
            return new FRKOtaManager(this);
        }
    }


    /**
     * 开始OTA
     */
    @Override
    public void startOta() {
        BleLog.i("开始OTA:mInitOk="+mInitOk);
        mStartOta = true;
        if (mInitOk) {
            if (mFrBleOTASDK != null && !mOtaStatus) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mHandler.sendEmptyMessageDelayed(OTA_TIME_OUT, TIME_OUT_TIME);
                        BleLog.iw("开始OTA");
                        try {
                            mOtaStatus = true;
                            mFrBleOTASDK.FrBleOTAUpate(mFilePath);
                        } catch (Exception e) {
                            BleLog.e(e.toString());
                            e.printStackTrace();
                            otaFailure("load file err");

                        }
                    }
                }).start();
            }
        }


    }

    @Override
    public void onBleOtaConnect() {
        //连接成功
        BleLog.i(TAG, "连接成功");
    }

    @Override
    public void onBleOtaDisConnect() {
        //连接断开
        BleLog.i(TAG, "连接断开");
        otaFailure("disconnect");
    }

    private void otaFailure(String msg) {
        mOtaStatus=false;
        mHandler.removeMessages(OTA_TIME_OUT);
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_FAIL, msg);
        }
        clear();
    }

    @Override
    public void onBleOtaReady() {
        //notify成功
        BleLog.i(TAG, "notify成功");
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
    public void onBleOtaFailure(String msg) {
        otaFailure(msg);
    }

    @Override
    public void onBleOtaUUIDError() {
        //UUID错误
        otaFailure("error code:UUIDError");
    }


    /**
     * ble ota状态改变
     *
     * @param state   状态
     * @param percent 百分比
     */
    @Override
    public void onBleOtaStateChange(int state, int percent) {
        if (mHandler.hasMessages(OTA_TIME_OUT)) {
            mHandler.removeMessages(OTA_TIME_OUT);
        }
        BleLog.i("状态:" + state + "  ||  进度:" + percent);
        if (mOnBleOTAListener != null) {
            switch (state) {
                //擦除
                case 0:
                    break;
                //写入,进度
                case 1:
                    mOnBleOTAListener.onOtaProgress(percent, 1, 1);
                    break;
                //完成
                case 2:
                    mOnBleOTAListener.onOtaSuccess();
                    clear();
                    break;

            }

        }
    }

    @Override
    public void clear() {
        mHandler.removeMessages(OTA_TIME_OUT);
        mInitOk = false;
        mStartOta = false;
        mOtaStatus=false;
        BleLog.i("clear");
    }


}
