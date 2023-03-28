package com.elinkthings.bleotalibrary.jl;

import android.content.Context;

import com.elinkthings.bleotalibrary.listener.OnBleFlashListener;
import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.jieli.jl_fatfs.model.FatFile;
import com.jieli.jl_rcsp.interfaces.watch.OnWatchOpCallback;
import com.jieli.jl_rcsp.model.base.BaseError;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.utils.BleLog;

import java.util.ArrayList;

/**
 * xing<br>
 * 2022/6/22<br>
 * java类作用描述
 */
public class JLManager {

    public final static int TYPE_OTA = 1;
    public final static int TYPE_WATCH = 2;
    public final static String CUSTOMIZE_WATCH_NAME = JLWatchManager.CUSTOMIZE_WATCH_NAME;
    public final static String CUSTOMIZE_OTA_NAME = JLOtaManager.WATCH_OTA_NAME;
    private JLOtaManager mJLOtaManager;
    private JLWatchManager mJLWatchManager;
    private int mType;


    public JLManager(Builder builder) {
        mType = builder.mType;
        if (TYPE_OTA == builder.mType) {
            mJLOtaManager = JLOtaManager.newBuilder(builder.mContext).setFilePath(builder.mFilePath).setOnBleOTAListener(builder.mOnBleOTAListener).build(builder.mBleDevice);
        } else {
            mJLWatchManager = JLWatchManager.newBuilder().setFilePath(builder.mFilePath).setOnBleFlashListener(builder.mOnBleFlashListener).build(builder.mBleDevice);
//            mJLWatchManager.init();
        }
    }




    public int getType() {
        return mType;
    }

    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    public final static class Builder {
        private BleDevice mBleDevice;
        private Context mContext;
        private OnBleOTAListener mOnBleOTAListener;
        private OnBleFlashListener mOnBleFlashListener;
        private String mFilePath = "";
        private int mType;

        public Builder(Context context) {
            mContext = context;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }

        /**
         * 设置类型,是OTA还是表盘
         *
         * @param type {@link JLManager#TYPE_OTA} and  {@link JLManager#TYPE_WATCH}
         * @return Builder
         */
        public Builder setType(int type) {
            mType = type;
            return this;
        }

        /**
         * OTA需要实现
         *
         * @param onBleOTAListener OTA进度接口
         * @return Builder
         */
        public Builder setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
            mOnBleOTAListener = onBleOTAListener;
            return this;
        }

        /**
         * 表盘需要实现
         *
         * @param onBleFlashListener 表盘升级进度接口
         * @return Builder
         */
        public Builder setOnBleFlashListener(OnBleFlashListener onBleFlashListener) {
            mOnBleFlashListener = onBleFlashListener;
            return this;
        }


        public JLManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            if (mBleDevice == null) {
                return null;
            }
            return new JLManager(this);
        }
    }

    public void startOta() {
        if (mJLOtaManager != null) {
            mJLOtaManager.startOta();
        }
    }


    public void setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
        if (mJLOtaManager != null) {
            mJLOtaManager.setOnBleOTAListener(onBleOTAListener);
        }
    }


    public void onBtDeviceConnection(BleDevice bleDevice) {
        if (mJLOtaManager != null) {
            BleLog.i("OTA回连");
            mJLOtaManager.onBtDeviceConnection(bleDevice);
        }
    }


    //---------------------


    /**
     * 开始传输文件
     *
     * @return 操作结果
     */
    public boolean startFlash() {
        if (mJLWatchManager != null) {
            return mJLWatchManager.startFlash();
        }
        return false;

    }


    public void release() {
        if (mJLOtaManager != null) {
            mJLOtaManager.release();
        }
        if (mJLWatchManager != null) {
            mJLWatchManager.release();
        }
    }


    /**
     * 获取手表保存的文件列表
     *
     * @param listener OnWatchOpCallback
     * @return 操作结果
     */
    public boolean getWatchList(OnWatchFileListener<ArrayList<FileBean>> listener) {
        if (mJLWatchManager != null) {
            return mJLWatchManager.getWatchList(listener);
        }
        return false;
    }

    /**
     * 获取手表保存的文件列表
     *
     * @param listener OnWatchOpCallback
     * @return 操作结果
     */
    public boolean setCurrentWatch(String path, OnWatchFileListener<FileBean> listener) {
        if (mJLWatchManager != null) {
            if (!path.startsWith("/")){
                path="/"+path;
            }
            return mJLWatchManager.setCurrentWatch(path, new OnWatchOpCallback<FatFile>() {
                @Override
                public void onSuccess(FatFile fatFile) {
                    if (listener != null) {
                        if (fatFile != null) {
                            listener.onSuccess(new FileBean(fatFile));
                        }else {
                            listener.onSuccess(null);
                        }
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
        return false;
    }


    /**
     * 获取手表保存的文件列表
     *
     * @param listener OnWatchOpCallback
     * @return 操作结果
     */
    public boolean deleteWatchFile(String path, OnWatchFileListener<Boolean> listener) {
        if (mJLWatchManager != null) {
            if (!path.startsWith("/")){
                path="/"+path;
            }
            return mJLWatchManager.deleteWatch(path,listener);
        }
        return false;
    }


    public interface OnWatchFileListener<T> {
        void onSuccess(T list);

        void onFailed(int code, String message);
    }


}
