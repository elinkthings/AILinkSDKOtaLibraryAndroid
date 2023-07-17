package com.elinkthings.bleotalibrary.jl;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.elinkthings.bleotalibrary.config.FlashConfig;
import com.elinkthings.bleotalibrary.config.OtaConfig;
import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.jieli.jl_bt_ota.constant.BluetoothConstant;
import com.jieli.jl_bt_ota.constant.StateCode;
import com.jieli.jl_bt_ota.impl.BluetoothOTAManager;
import com.jieli.jl_bt_ota.interfaces.BtEventCallback;
import com.jieli.jl_bt_ota.interfaces.IActionCallback;
import com.jieli.jl_bt_ota.interfaces.IUpgradeCallback;
import com.jieli.jl_bt_ota.model.BluetoothOTAConfigure;
import com.jieli.jl_bt_ota.model.base.BaseError;
import com.jieli.jl_bt_ota.model.response.TargetInfoResponse;
import com.pingwang.bluetoothlib.config.BleConfig;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.device.SendDataBean;
import com.pingwang.bluetoothlib.listener.OnBleMtuListener;
import com.pingwang.bluetoothlib.listener.OnBleSendResultListener;
import com.pingwang.bluetoothlib.listener.OnCharacteristicListener;
import com.pingwang.bluetoothlib.utils.BleLog;
import com.pingwang.bluetoothlib.utils.BleStrUtils;
import com.pingwang.bluetoothlib.utils.MyBleDeviceUtils;

import java.util.UUID;

/**
 * xing<br>
 * 2022/6/22<br>
 * 杰里手表OTA管理类
 * 1,
 */
class JLOtaManager extends BluetoothOTAManager implements OnBleMtuListener, IUpgradeCallback, OnCharacteristicListener, BleDevice.onDisConnectedListener, OnBleSendResultListener {

    public final static String WATCH_OTA_NAME = "watch_ota.ufw";
    private final static int SEND_INTERVAL = 40;
    private volatile BluetoothGatt mBluetoothGatt;
    private volatile BleDevice mBleDevice;
    private int mMtu = 0;
    private OnBleOTAListener mOnBleOTAListener;
    private boolean mInitOk = false;

    private String mFilePath;
    /**
     * 是否已开启OTA,用于初始化成功后是否进行OTA的判断
     */
    private boolean mStartOta = false;
    /**
     * Notify是否成功
     */
    private boolean mNotify = false;
    /**
     * 连接状态
     */
    private boolean mConnectStatus = false;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                mHandler.removeMessages(1);
                BleLog.i("onDescriptorWrite超时");
                if (null != mBleDevice) {
                    mNotify = true;
                    onBtDeviceConnection(mBleDevice);
                }
            }
        }
    };

    public JLOtaManager(Builder builder) {
        super(builder.mContext);
        //JL的log
        //        JL_Log.setIsSaveLogFile(builder.mContext, true);
        mOnBleOTAListener = builder.mOnBleOTAListener;
        mFilePath = builder.mFilePath;
        mBleDevice = builder.mBleDevice;
        mMtu = builder.mMtu;
        onBtDeviceConnection(mBleDevice);

    }

    public void onBtDeviceConnection(BleDevice bleDevice) {
        try {
            BleLog.i("onBtDeviceConnection");
            if (bleDevice == null) {
                BleLog.i("bleDevice=null");
                if (mOnBleOTAListener != null) {
                    mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_FAIL, "bleDevice=null");
                }
                return;
            }
            mConnectStatus = true;
            registerBluetoothCallback(mBtEventCallback);
            mBleDevice = bleDevice;
            mBleDevice.setOnDisConnectedListener(this);
            mBleDevice.setOnCharacteristicListener(this);
            mBleDevice.setOnBleSendResultListener(this);
            mBleDevice.setOnBleMtuListener(this);
            mBluetoothGatt = bleDevice.getBluetoothGatt();
            if (mMtu == 0) {
                mBleDevice.setMtu(FlashConfig.MTU_MAX);
            }
            if (mBluetoothGatt == null) {
                BleLog.i("bluetoothGatt=null");
                return;
            }
            if (!mNotify) {
                bleDevice.setNotify(BluetoothConstant.UUID_SERVICE, BluetoothConstant.UUID_NOTIFICATION);
                return;
            }
            mInitOk = isOTA();
            if (mInitOk) {
                if (null != getConnectedDevice()) {
                    onBtDeviceConnection(getConnectedDevice(), StateCode.CONNECTION_OK);
                }
                return;
            }
            initBleDeviceInfo();
            BleLog.i("通知JL SDK连接成功");
            if (null != getConnectedDevice()) {
                onBtDeviceConnection(getConnectedDevice(), StateCode.CONNECTION_OK);
            } else {
                BleLog.i("getConnectedDevice()=null");
                //OTA 错误,失败
                if (mOnBleOTAListener != null) {
                    mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_FAIL, "BluetoothDevice=null;bluetoothGatt=" + mBluetoothGatt);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void initBleDeviceInfo() {
        BluetoothOTAConfigure bluetoothOTAConfigure = BluetoothOTAConfigure.createDefault();
        bluetoothOTAConfigure.setPriority(BluetoothOTAConfigure.PREFER_BLE);
        //默认是500毫秒
        bluetoothOTAConfigure.setBleIntervalMs(500);
        //超时时间
        bluetoothOTAConfigure.setTimeoutMs(10000);
        //是否启用设备认证流程(与固件工程师确认)
        bluetoothOTAConfigure.setUseAuthDevice(true);
        //设置BLE的MTU
        bluetoothOTAConfigure.setMtu(mMtu + 3);
        //是否需要改变BLE的MTU
        bluetoothOTAConfigure.setNeedChangeMtu(true);
        bluetoothOTAConfigure.setUseJLServer(true);
        //设置本地存储OTA文件的路径
        bluetoothOTAConfigure.setFirmwareFilePath(mFilePath);
        //设置回连
        bluetoothOTAConfigure.setUseReconnect(false);
        //配置OTA参数
        configure(bluetoothOTAConfigure);
        BleLog.i("配置OTA参数");
        if (getConnectedBluetoothGatt() != null) {
            onMtuChanged(getConnectedBluetoothGatt(), mMtu + 3, 0);
            BleLog.i("配置SDK MTU");
        }

    }


    private BtEventCallback mBtEventCallback = new BtEventCallback() {
        @Override
        public void onConnection(BluetoothDevice bluetoothDevice, int status) {
            if (status == StateCode.CONNECTION_OK) {
                if (isOTA()) {
                    BleLog.i("连接成功,已经在OTA状态了,什么都不处理" + isOTA());
                } else {
                    //查询强制升级状态
                    //                    queryMandatoryUpdate();
                    mInitOk = true;
                    BleLog.i("连接成功,可以进行OTA升级:" + isOTA());
                    if (mStartOta) {
                        startOta();
                    }
                }
            } else {
                if (!mConnectStatus) {
                    BleLog.i("不在连接状态的失败" + status);
                    return;
                }
                BleLog.i("连接失败:" + status);
                //OTA 错误,失败
                if (mOnBleOTAListener != null && mBleDevice != null) {
                    mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_FAIL, "error code:" + status);
                }
                clear();
            }
        }
    };


    /**
     * 查询强制更新
     */
    private void queryMandatoryUpdate() {
        queryMandatoryUpdate(new IActionCallback<TargetInfoResponse>() {
            @Override
            public void onSuccess(TargetInfoResponse targetInfoResponse) {
                //说明设备需要强制升级，请跳转到OTA界面，引导用户升级固件
                int versionCode = targetInfoResponse.getVersionCode();//设备版本号
                String versionName = targetInfoResponse.getVersionName();//设备版本名
                String projectCode = targetInfoResponse.getProjectCode();//设备产品ID(默认是0，如果设备支持会改变)
                BleLog.i("queryMandatoryUpdate:设备版本号:" + versionCode + " 设备版本名称:" + versionName + "  设备产品ID:" + projectCode + " isOTA()" + isOTA());
                if (!isOTA()) {
                    startOta();
                }
            }

            @Override
            public void onError(BaseError baseError) {
                BleLog.i("queryMandatoryUpdate:可以不用处理");
            }
        });
    }


    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    public final static class Builder {
        private BleDevice mBleDevice;
        private Context mContext;
        private OnBleOTAListener mOnBleOTAListener;
        private String mFilePath = "";
        private int mMtu;


        public Builder(Context context) {
            mContext = context;
        }


        public Builder setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
            mOnBleOTAListener = onBleOTAListener;
            return this;
        }

        public Builder setMtu(int mtu) {
            mMtu = mtu;
            return this;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }


        public JLOtaManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            if (mBleDevice == null) {
                return null;
            }
            return new JLOtaManager(this);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().toString().equalsIgnoreCase(BluetoothConstant.UUID_NOTIFICATION.toString())) {
            byte[] data = characteristic.getValue();
            //            BleLog.i(TAG, "OTA接收的数据:" + BleStrUtils.byte2HexStrToUpperCase(data));
            BluetoothDevice connectedDevice = getConnectedDevice();
            if (connectedDevice != null) {
                onReceiveDeviceData(connectedDevice, data);
            } else {
                BleLog.i("getConnectedDevice()==null");
            }
        }
    }


    /**
     * 开始OTA
     */
    public void startOta() {
        try {
            if (mBleDevice != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mBleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                }
            }
            mStartOta = true;
            if (mInitOk) {
                startOTA(this);
            }
        } catch (Exception e) {
            BleLog.e(e.toString());
            e.printStackTrace();
        }
    }


    @Override
    public BluetoothDevice getConnectedDevice() {
        return getConnectedBluetoothGatt() != null ? mBluetoothGatt.getDevice() : null;
    }

    @Override
    public BluetoothGatt getConnectedBluetoothGatt() {
        return mBluetoothGatt;
    }


    @Override
    public void connectBluetoothDevice(BluetoothDevice bluetoothDevice) {
        //需要连接的蓝牙设备
        mHandler.removeMessages(1);
        BleLog.i("connectBluetoothDevice:" + bluetoothDevice.getAddress());
        //        DeviceReConnectManager.getInstance(this).setReconnectAddress(null);
        mNotify = false;
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onReconnect(bluetoothDevice.getAddress());
        }
    }

    @Override
    public void disconnectBluetoothDevice(BluetoothDevice bluetoothDevice) {
        BleLog.i("disconnectBluetoothDevice:" + bluetoothDevice.getAddress());
        mNotify = false;
        clear();
    }


    @Override
    public boolean sendDataToDevice(BluetoothDevice bluetoothDevice, byte[] bytes) {
        if (bluetoothDevice == null || bytes == null) {
            return false;
        }
        //        BleLog.i(TAG, "OTA发送的数据:" + BleStrUtils.byte2HexStrToUpperCase(bytes));
        return toSendData(bytes);
    }

    private synchronized boolean toSendData(byte[] bytes) {
        int mtu = mMtu;
        int dataLen = bytes.length;
        int blockCount = dataLen / mtu;

        boolean status = true;
        for (int i = 0; i < blockCount; i++) {
            byte[] mBlockData = new byte[mtu];
            System.arraycopy(bytes, i * mtu, mBlockData, 0, mBlockData.length);
            SendDataBean sendDataBean = new SendDataBean(mBlockData, BluetoothConstant.UUID_WRITE, BleConfig.WRITE_DATA, BluetoothConstant.UUID_SERVICE);
            status = sendDataOta(sendDataBean);
        }
        if (0 != dataLen % mtu) {
            byte[] noBlockData = new byte[dataLen % mtu];
            System.arraycopy(bytes, dataLen - (dataLen % mtu), noBlockData, 0, noBlockData.length);
            SendDataBean sendDataBean = new SendDataBean(noBlockData, BluetoothConstant.UUID_WRITE, BleConfig.WRITE_DATA, BluetoothConstant.UUID_SERVICE);
            status = sendDataOta(sendDataBean);

        }
        return status;
    }

    /**
     * 发送数据OTA
     *
     * @param sendDataBean 发送数据bean
     * @return boolean
     */
    private boolean sendDataOta(SendDataBean sendDataBean) {
        boolean status;
        int time = SEND_INTERVAL;
        SystemClock.sleep(time);
        if (mBleDevice == null) {
            return false;
        }
        status = mBleDevice.sendDataOta(sendDataBean);
        String sendData = BleStrUtils.byte2HexStrToUpperCase(sendDataBean.getHex());
        if (!status) {
            time -= 5;
            SystemClock.sleep(time);
            if (mBleDevice == null) {
                return false;
            }
            BleLog.i("发送数据失败,重发:" + sendData);
            status = mBleDevice.sendDataOta(sendDataBean);
            if (!status) {
                BleLog.i("发送数据失败,停止重发:" + sendData);
                return false;
            }
        }
        return true;
    }


    @Override
    public void release() {
        try {
            unregisterBluetoothCallback(mBtEventCallback);
            mHandler.removeCallbacksAndMessages(null);
            if (mBleDevice != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mBleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
                }
            }
            clear();
            super.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //-------------BLE相关接口

    @Override
    public void onDisConnected() {
        BleLog.i("onDisConnected");
        mConnectStatus = false;
        if (mBleDevice != null) {
            BluetoothGatt bluetoothGatt = mBleDevice.getBluetoothGatt();
            if (null != bluetoothGatt) {
                MyBleDeviceUtils.refreshDeviceCache(bluetoothGatt);
                if (null != bluetoothGatt.getDevice()) {
                    onBtDeviceConnection(bluetoothGatt.getDevice(), StateCode.CONNECTION_FAILED);
                }
            }
        }
        clear();
        mNotify = false;
    }

    @Override
    public void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {
        if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(BluetoothConstant.UUID_NOTIFICATION.toString())) {
            BleLog.i("onDescriptorWriteOK");
            mNotify = true;
            onBtDeviceConnection(mBleDevice);
            mHandler.removeMessages(1);
        }
    }

    @Override
    public void onNotifyResult(UUID uuid, boolean result) {
        if (uuid.toString().equalsIgnoreCase(BluetoothConstant.UUID_NOTIFICATION.toString())) {
            BleLog.i("onNotifyResult:" + result);
            if (result) {
                mHandler.removeMessages(1);
                //            mNotify = true;
                //            onBtDeviceConnection(mBleDevice);
                mHandler.sendEmptyMessageDelayed(1, 5000);
            } else {
                //OTA 错误,失败
                if (mOnBleOTAListener != null && mBleDevice != null) {
                    mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_FAIL, "Notify:" + result);
                }
            }

        }
    }

    @Override
    public void OnMtu(int mtu) {
        if (getConnectedBluetoothGatt() != null) {
            onMtuChanged(getConnectedBluetoothGatt(), mtu, 0);
        }
        mMtu = mtu - 3;
        BleLog.i("MTU:" + mtu);
    }


    //-------------------OTA状态回调


    @Override
    public void onStartOTA() {
        BleLog.i("onStartOTA");
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaProgress(-1, 0, 0);
        }
    }

    /**
     * 需要回连的回调
     *
     * <p>注意: 1.仅连接BLE通讯通道
     * 2.用于单备份OTA</p>
     *
     * @param addr              回连设备的MAC地址
     * @param isNewReconnectWay 是否使用新回连方式
     */
    @Override
    public void onNeedReconnect(String addr, boolean isNewReconnectWay) {
        //回调需要回连的设备地址,单备份的产品回连后需要重新连接进行后面的升级操作
        mConnectStatus = false;
        clear();
        BleLog.i("onNeedReconnect:" + addr + " status:" + isNewReconnectWay);
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onReconnect(addr);
        }
    }

    @Override
    public void onProgress(int type, float progress) {
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaProgress(progress, type + 1, 2);
        }

    }

    @Override
    public void onStopOTA() {
        //回调OTA升级完成
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaSuccess();
        }
        release();
    }

    @Override
    public void onCancelOTA() {
        mHandler.removeMessages(1);
        //回调OTA升级被取消
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_CANCEL, "cancel");
        }
    }

    @Override
    public void onError(BaseError error) {
        mHandler.removeMessages(1);
        int code = error.getCode();
        BleLog.i("onError:" + code + " msg:" + error.getMessage());
        //OTA 错误,失败
        if (mOnBleOTAListener != null) {
            mOnBleOTAListener.onOtaFailure(OtaConfig.OTA_FAIL, error.getMessage());
        }
    }


    public void setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
        mOnBleOTAListener = onBleOTAListener;
    }

    public void clear() {
        mConnectStatus = false;
        mHandler.removeMessages(1);
        mBluetoothGatt = null;
        mBleDevice = null;
        BleLog.i("clear");
    }
}
