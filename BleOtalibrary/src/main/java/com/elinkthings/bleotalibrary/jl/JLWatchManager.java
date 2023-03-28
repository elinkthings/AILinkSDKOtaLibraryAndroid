package com.elinkthings.bleotalibrary.jl;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import com.elinkthings.bleotalibrary.config.FlashConfig;
import com.elinkthings.bleotalibrary.listener.OnBleFlashListener;
import com.jieli.jl_bt_ota.constant.BluetoothConstant;
import com.jieli.jl_bt_ota.impl.RcspAuth;
import com.jieli.jl_fatfs.FatFsErrCode;
import com.jieli.jl_fatfs.interfaces.OnFatFileProgressListener;
import com.jieli.jl_fatfs.model.FatFile;
import com.jieli.jl_rcsp.constant.StateCode;
import com.jieli.jl_rcsp.impl.WatchOpImpl;
import com.jieli.jl_rcsp.interfaces.watch.OnWatchCallback;
import com.jieli.jl_rcsp.interfaces.watch.OnWatchOpCallback;
import com.jieli.jl_rcsp.model.base.BaseError;
import com.pingwang.bluetoothlib.config.BleConfig;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.device.SendDataBean;
import com.pingwang.bluetoothlib.listener.OnBleMtuListener;
import com.pingwang.bluetoothlib.listener.OnCharacteristicListener;
import com.pingwang.bluetoothlib.utils.BleLog;

import java.util.ArrayList;

/**
 * xing<br>
 * 2022/6/22<br>
 * 杰里手表表盘(Flash)管理类
 */
class JLWatchManager extends WatchOpImpl implements BleDevice.onDisConnectedListener, OnFatFileProgressListener, OnBleMtuListener, OnCharacteristicListener {

    public final static String CUSTOMIZE_WATCH_NAME = "watch9";
    private final static int WATCH_FILE_SIZE = 3;
    private BleDevice mBleDevice;
    private int mMtu = 245;
    private OnBleFlashListener mOnBleFlashListener;
    private boolean mInitOk = false;
    private String mFilePath;
    /**
     * Notify是否成功
     */
    private boolean mNotify = false;
    private RcspAuth mRcspAuth;

    /**
     * 是否在升级表盘状态
     */
    private boolean mStartStatus = false;
    /**
     * 待删除的表盘路径
     */
    private volatile String mDeleteWatchPath = "";
    /**
     * 新表盘路径
     */
    private volatile String mNewWatchPath = "";
    private static Builder mBuilder;


    public JLWatchManager(Builder builder) {
        super(WatchOpImpl.FUNC_WATCH);
        mOnBleFlashListener = builder.mOnBleFlashListener;
        mFilePath = builder.mFilePath;
        onBtDeviceConnection(builder.mBleDevice);

    }

    public static Builder newBuilder() {
        if (mBuilder == null) {
            synchronized (Builder.class) {
                if (mBuilder == null)
                    mBuilder = new Builder();
            }
        }
        return mBuilder;
    }

    public final static class Builder {
        private BleDevice mBleDevice;
        private OnBleFlashListener mOnBleFlashListener;
        private String mFilePath = "";
        private boolean mAuthOk;

        public Builder() {
        }


        public Builder setOnBleFlashListener(OnBleFlashListener onBleFlashListener) {
            mOnBleFlashListener = onBleFlashListener;
            return this;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }


        public JLWatchManager build(BleDevice bleDevice) {
            if (mBleDevice != null && mBleDevice != bleDevice) {
                mAuthOk = false;
            }
            mBleDevice = bleDevice;
            if (mBleDevice == null) {
                return null;
            }
            return new JLWatchManager(this);
        }
    }

    public void onBtDeviceConnection(BleDevice bleDevice) {
        mBleDevice = bleDevice;
        mBleDevice.setOnCharacteristicListener(this);

        if (!mNotify) {
            mBleDevice.setNotify(BluetoothConstant.UUID_SERVICE, BluetoothConstant.UUID_NOTIFICATION);
            return;
        }
        BluetoothDevice device = bleDevice.getBluetoothGatt().getDevice();
        mRcspAuth = new RcspAuth(new RcspAuth.IRcspAuthOp() {
            @Override
            public boolean sendAuthDataToDevice(BluetoothDevice bluetoothDevice, byte[] bytes) {
                return sendDataToDevice(bluetoothDevice, bytes);
            }
        }, new RcspAuth.OnRcspAuthListener() {
            @Override
            public void onInitResult(boolean b) {
                BleLog.i("onInitResult:" + b);
            }

            @Override
            public void onAuthSuccess(BluetoothDevice bluetoothDevice) {
                mBuilder.mAuthOk = true;
                mRcspAuth.destroy();
                BleLog.i("onAuthSuccess");
                notifyBtDeviceConnection(bleDevice.getBluetoothGatt().getDevice(), StateCode.CONNECTION_OK);
            }


            @Override
            public void onAuthFailed(BluetoothDevice bluetoothDevice, int i, String s) {
                mRcspAuth.destroy();
                mBuilder.mAuthOk = false;
                BleLog.i("onAuthFailed" + s + "   code:" + i);
                notifyBtDeviceConnection(bleDevice.getBluetoothGatt().getDevice(), StateCode.CONNECTION_FAILED);
            }
        });
        if (!mBuilder.mAuthOk) {
            mRcspAuth.stopAuth(device, false);
            boolean b = mRcspAuth.startAuth(device);//认证
        } else {
            notifyBtDeviceConnection(bleDevice.getBluetoothGatt().getDevice(), StateCode.CONNECTION_OK);
        }



        registerOnWatchCallback(new OnWatchCallback() {
            @Override
            public void onWatchSystemInit(int code) {
                super.onWatchSystemInit(code);
                BleLog.i("onWatchSystemInit:" + code + "  StartStatus:" + mStartStatus);
                //code为0，是初始化成功，其他为错误码
                mInitOk = code == 0;
                if (mInitOk) {
                    if (mStartStatus) {
                        startFlash();
                    }
                } else {
                    onStop(code);
                }
            }

            @Override
            public void onWatchSystemException(BluetoothDevice device, int sysStatus) {
                super.onWatchSystemException(device, sysStatus);
                //sysStatus 为0是正常，其他为错误码
                //有此回调时，表示设备系统异常，需要恢复系统
                if (sysStatus == 1)
                    restoreWatchSystem(null, new OnFatFileProgressListener() {
                        @Override
                        public void onStart(String s) {
                            BleLog.i("系统异常,正在恢复中.");
                        }

                        @Override
                        public void onProgress(float v) {

                        }

                        @Override
                        public void onStop(int i) {
                            if (i == 0) {
                                BleLog.i("系统异常,恢复成功.");
                                if (mStartStatus) {
                                    startFlash();
                                } else {
                                    release();
                                }
                            }
                        }
                    });

            }

            @Override
            public void onResourceUpdateUnfinished(BluetoothDevice device) {
                super.onResourceUpdateUnfinished(device);
                //设备处于升级资源过程，需要继续更新资源才能正常运作
                //重新进⾏更新资源操作,zipPath为更新压缩包，压缩包名称为upgrade.zip
//                updateWatchResource(zipPath,updateResourceListener);
                BleLog.i("onResourceUpdateUnfinished:" + device.getAddress());
            }
        });


    }


    private boolean isValidAuthData(byte[] var1) {
        if (var1 != null && var1.length != 0) {
            return var1.length == 5 && var1[0] == 2 || var1.length == 17 && (var1[0] == 0 || var1[0] == 1);
        } else {
            return false;
        }
    }


    public void setFilePath(String filePath) {
        mFilePath = filePath;
    }

    private int mMaxCount = 1;
    private int mCurrentCount = 1;
    private boolean mSendWatchIng = false;

    /**
     * 开始传输文件
     * 新增表盘
     *
     * @return 操作结果
     */
    public boolean startFlash() {
        mBleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        mStartStatus = true;
        if (mInitOk) {
            if (mSendWatchIng) {
                //避免多次调用发送数据包
                return true;
            }
            mSendWatchIng = true;
            BleLog.i("当前上传的表盘路径:" + mFilePath);
            mNewWatchPath = mFilePath.substring(mFilePath.lastIndexOf("/"));
            listWatchList(new OnWatchOpCallback<ArrayList<FatFile>>() {
                @Override
                public void onSuccess(ArrayList<FatFile> fatFiles) {
                    for (FatFile fatFile : fatFiles) {
                        if (fatFile.getName().equalsIgnoreCase(CUSTOMIZE_WATCH_NAME)) {
                            replaceWatchFile(mFilePath, JLWatchManager.this);
                            return;
                        }
                    }

                    if (fatFiles.size() >= WATCH_FILE_SIZE) {
                        FatFile fileBean = fatFiles.get(fatFiles.size() - 1);
                        mDeleteWatchPath = fileBean.getPath();
                    }
                    if (mNewWatchPath.equalsIgnoreCase(mDeleteWatchPath)) {
                        //新表盘与旧表盘一致,直接替换
                        mDeleteWatchPath = "";
                        mMaxCount = 1;
                        replaceWatchFile(mFilePath, JLWatchManager.this);
                    } else {
                        mMaxCount = 2;
                        createWatchFile(mFilePath, false, JLWatchManager.this);
                    }
                }

                @Override
                public void onFailed(BaseError baseError) {

                }
            });
        }
        return mInitOk;
    }


    /**
     * 删除表盘
     *
     * @param filePath
     * @return
     */
    public boolean deleteWatch(String filePath, JLManager.OnWatchFileListener<Boolean> listener) {
        if (mInitOk) {
            deleteWatchFile(filePath, new OnFatFileProgressListener() {
                @Override
                public void onStart(String s) {
                    if (listener != null) {
                        listener.onSuccess(true);
                    }
                }

                @Override
                public void onProgress(float v) {

                }

                @Override
                public void onStop(int i) {
                    if (listener != null) {
                        listener.onSuccess(false);
                    }
                }
            });
        }
        return mInitOk;
    }


    /**
     * 设置当前表盘
     *
     * @param path
     * @return
     */
    public boolean setCurrentWatch(String path, OnWatchOpCallback<FatFile> listener) {
        if (mInitOk) {
            this.setCurrentWatchInfo(path, new OnWatchOpCallback<FatFile>() {
                @Override
                public void onSuccess(FatFile fatFile) {
                    if (listener != null) {
                        listener.onSuccess(fatFile);
                    }
                }

                @Override
                public void onFailed(BaseError baseError) {
                    if (listener != null) {
                        listener.onFailed(baseError);
                    }
                }
            });
        }
        return mInitOk;
    }

    /**
     * 获取手表保存的文件列表
     *
     * @param listener OnWatchOpCallback
     * @return 操作结果
     */
    public boolean getWatchList(JLManager.OnWatchFileListener<ArrayList<FileBean>> listener) {
        if (mInitOk) {
            listWatchList(new OnWatchOpCallback<ArrayList<FatFile>>() {
                @Override
                public void onSuccess(ArrayList<FatFile> fatFiles) {
                    if (listener != null) {
                        ArrayList<FileBean> list = new ArrayList<>();
                        for (FatFile fatFile : fatFiles) {
                            list.add(new FileBean(fatFile));
                        }
                        listener.onSuccess(list);
                    }
                }

                @Override
                public void onFailed(BaseError baseError) {
                    if (listener != null) {
                        listener.onFailed(baseError.getCode(), baseError.getMessage());
                    }
                }
            });
        }
        return mInitOk;
    }


    @Override
    public void OnMtu(int mtu) {
        mMtu = mtu - 3;
        BleLog.i("OnMtu:" + mtu);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        boolean validAuthData = isValidAuthData(data);
        if (validAuthData) {
            mRcspAuth.handleAuthData(mBleDevice.getBluetoothGatt().getDevice(), data);
        } else {
            notifyReceiveDeviceData(mBleDevice.getBluetoothGatt().getDevice(), data);
        }
    }


    @Override
    public void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {
        if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(BluetoothConstant.UUID_NOTIFICATION.toString())) {
            BleLog.i("onDescriptorWriteOK");
            mNotify = true;
            onBtDeviceConnection(mBleDevice);
        }
    }


    @Override
    public BluetoothDevice getConnectedDevice() {
        return mBleDevice == null ? null : mBleDevice.getBluetoothGatt().getDevice();
    }

    @Override
    public boolean sendDataToDevice(BluetoothDevice bluetoothDevice, byte[] bytes) {
        if (bluetoothDevice == null || bytes == null)
            return false;
        return toSendData(bytes);
    }

    private boolean toSendData(byte[] bytes) {
        int time = 0;
        int mtu = mMtu;
        int dataLen = bytes.length;
        int blockCount = dataLen / mtu;
        boolean status = true;
        for (int i = 0; i < blockCount; i++) {
            byte[] mBlockData = new byte[mtu];
            System.arraycopy(bytes, i * mtu, mBlockData, 0, mBlockData.length);
            time = time + mBlockData.length;
            SendDataBean sendDataBean = new SendDataBean(mBlockData, BluetoothConstant.UUID_WRITE, BleConfig.WRITE_DATA, BluetoothConstant.UUID_SERVICE);
            status = mBleDevice.sendDataOta(sendDataBean);
            if (!status) {
                SystemClock.sleep(20);
                status = mBleDevice.sendDataOta(sendDataBean);
                if (!status) {
                    SystemClock.sleep(20);
                    status = mBleDevice.sendDataOta(sendDataBean);
                    if (!status) {
                        return false;
                    }
                }
            }
        }
        if (0 != dataLen % mtu) {
            SystemClock.sleep(20);
            byte[] noBlockData = new byte[dataLen % mtu];
            System.arraycopy(bytes, dataLen - (dataLen % mtu), noBlockData, 0, noBlockData.length);
            SendDataBean sendDataBean = new SendDataBean(noBlockData, BluetoothConstant.UUID_WRITE, BleConfig.WRITE_DATA, BluetoothConstant.UUID_SERVICE);
            status = mBleDevice.sendDataOta(sendDataBean);
            if (!status) {
                SystemClock.sleep(20);
                status = mBleDevice.sendDataOta(sendDataBean);
                if (!status) {
                    return false;
                }
            }
        }
        return status;
    }


    @Override
    public void onStart(String s) {
        mSendWatchIng = true;
        mStartStatus = false;
        if (mOnBleFlashListener != null) {
            mOnBleFlashListener.onFlashProgress(0, mCurrentCount, mMaxCount);
        }

    }

    @Override
    public void onProgress(float v) {
        if (mOnBleFlashListener != null) {
            mOnBleFlashListener.onFlashProgress(v, mCurrentCount, mMaxCount);
        }
    }

    @Override
    public void onStop(int result) {
        mSendWatchIng = false;
        mStartStatus = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
        }
        if (result == 0) {

            if (!TextUtils.isEmpty(mDeleteWatchPath)) {
                BleLog.i("表盘成功,需要删除:" + mDeleteWatchPath);
                deleteWatchFile(mDeleteWatchPath, new OnFatFileProgressListener() {
                    @Override
                    public void onStart(String s) {

                    }

                    @Override
                    public void onProgress(float v) {

                    }

                    @Override
                    public void onStop(int i) {
                        if (i == 0) {
                            //创建表盘成功,切换到当前表盘
                            setCurrentWatchInfo(mNewWatchPath, new OnWatchOpCallback<FatFile>() {
                                @Override
                                public void onSuccess(FatFile fatFile) {
                                    if (mOnBleFlashListener != null) {
                                        mOnBleFlashListener.onFlashSuccess();
                                    }
                                    BleLog.i("设置表盘成功:" + (fatFile != null ? fatFile.getPath() : mNewWatchPath));
                                }

                                @Override
                                public void onFailed(BaseError baseError) {
                                }
                            });
                        }
                    }
                });
                mDeleteWatchPath = "";
            } else {
                //创建表盘成功,切换到当前表盘
                setCurrentWatchInfo(mNewWatchPath, new OnWatchOpCallback<FatFile>() {
                    @Override
                    public void onSuccess(FatFile fatFile) {
                        BleLog.i("设置表盘成功:" + (fatFile != null ? fatFile.getPath() : mNewWatchPath));
                        if (mOnBleFlashListener != null) {
                            mOnBleFlashListener.onFlashSuccess();
                        }
                    }

                    @Override
                    public void onFailed(BaseError baseError) {
                    }
                });
            }
        } else if (result == FatFsErrCode.RES_ERR_SPACE_TO_UPDATE) {
            //空间不足,提示客户删除表盘
            if (mOnBleFlashListener != null) {
                mOnBleFlashListener.onFlashFailure(FlashConfig.FLASH_NOT_ENOUGH_SPACE, "空间不足");
            }
        } else {
            if (mOnBleFlashListener != null) {
                mOnBleFlashListener.onFlashFailure(FlashConfig.FLASH_FAIL, "失败:" + result);
            }
        }
    }

    @Override
    public void onDisConnected() {
        mSendWatchIng = false;
        mBuilder.mAuthOk = false;
        if (mBleDevice != null)
            notifyBtDeviceConnection(mBleDevice.getBluetoothGatt().getDevice(), StateCode.CONNECTION_FAILED);
        mNotify = false;
        mBleDevice = null;
        release();
    }

    @Override
    public void release() {
        if (mBleDevice != null)
            mBleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
        super.release();
        mSendWatchIng = false;
        mBuilder.mAuthOk = false;
        mStartStatus = false;
    }
}
