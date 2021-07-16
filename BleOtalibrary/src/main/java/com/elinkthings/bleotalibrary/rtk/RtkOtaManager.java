package com.elinkthings.bleotalibrary.rtk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.realsil.sdk.dfu.DfuConstants;
import com.realsil.sdk.dfu.DfuException;
import com.realsil.sdk.dfu.image.BinFactory;
import com.realsil.sdk.dfu.image.BinIndicator;
import com.realsil.sdk.dfu.image.LoadParams;
import com.realsil.sdk.dfu.model.BinInfo;
import com.realsil.sdk.dfu.model.DfuConfig;
import com.realsil.sdk.dfu.model.DfuProgressInfo;
import com.realsil.sdk.dfu.model.FileTypeInfo;
import com.realsil.sdk.dfu.model.OtaDeviceInfo;
import com.realsil.sdk.dfu.model.OtaModeInfo;
import com.realsil.sdk.dfu.model.Throughput;
import com.realsil.sdk.dfu.utils.BaseDfuAdapter;
import com.realsil.sdk.dfu.utils.ConnectParams;
import com.realsil.sdk.dfu.utils.DfuAdapter;
import com.realsil.sdk.dfu.utils.GattDfuAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * xing<br>
 * 2021/2/2<br>
 * realtek ota工具类
 */
public class RtkOtaManager {
    private String mMac = "";
    private String mFilePath = "";
    private OnBleOTAListener mOnBleOTAListener;
    private int mOtaType = DfuConstants.OTA_MODE_NORMAL_FUNCTION;
    private int mFileLocation = -1;
    private Context mContext;
    private DfuConfig mDfuConfig;
    private GattDfuAdapter mDfuAdapter;
    private OtaDeviceInfo mOtaDeviceInfo = null;
    private ConnectParams mConnectParams = null;
    private BinInfo mBinInfo;
    private boolean mStartOta = false;

    /**
     * 总步骤
     */
    private int mStepSize=0;
    private int mStep=-1;
    private List<FileTypeInfo> mFileTypeInfos;
    private List<String> mListVersion=new ArrayList<>();

    public static Builder newInstance(Context context) {
        return new Builder(context);
    }


    public static final class Builder {
       private BinInfo mBinInfo;
       private String mMac = "";
       private String mFilePath = "";
       private OnBleOTAListener mOnBleOTAListener;
       private int mFileLocation = -1;
       private Context mContext;
       private List<FileTypeInfo> mFileTypeInfos;

        public Builder(Context context) {
            mContext = context;
        }

        public RtkOtaManager build() {
            return new RtkOtaManager(this);
        }

        public Builder setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
            mOnBleOTAListener = onBleOTAListener;
            return this;
        }

        /**
         * 开始升级
         *
         * @param mac      设备mac地址
         * @param fileName 文件名称
         */
        public Builder setOtaPathAssets(String mac, String fileName) {
            mMac = mac;
            mFilePath = fileName;
            mFileLocation = DfuConfig.FILE_LOCATION_ASSETS;
            try {
                LoadParams.Builder bin = new LoadParams.Builder().with(mContext).fileLocation(mFileLocation).setFilePath(mFilePath).setFileSuffix("bin").setIcCheckEnabled(true);
                mBinInfo = BinFactory.loadImageBinInfo(bin.build());
                mFileTypeInfos = DfuAdapter.getSupportedFileContents(mBinInfo);
            } catch (DfuException e) {
                e.printStackTrace();
            }

            return this;
        }

        /**
         * 开始升级
         *
         * @param mac      设备mac地址
         * @param filePath 文件路径
         */
        public Builder setOtaPathSdcard(String mac, String filePath) {
            mMac = mac;
            mFilePath = filePath;
            mFileLocation = DfuConfig.FILE_LOCATION_SDCARD;
            try {
                LoadParams.Builder bin = new LoadParams.Builder().with(mContext).fileLocation(mFileLocation).setFilePath(mFilePath).setFileSuffix("bin").setIcCheckEnabled(true);
                mBinInfo = BinFactory.loadImageBinInfo(bin.build());
                mFileTypeInfos = DfuAdapter.getSupportedFileContents(mBinInfo);
            } catch (DfuException e) {
                e.printStackTrace();
            }

            return this;
        }
    }

    public RtkOtaManager(Builder builder) {
        this.mContext = builder.mContext;
        this.mMac = builder.mMac;
        this.mFileLocation = builder.mFileLocation;
        this.mFilePath = builder.mFilePath;
        this.mOnBleOTAListener = builder.mOnBleOTAListener;
        mDfuConfig = new DfuConfig();
        mDfuAdapter = GattDfuAdapter.getInstance(mContext);
        mDfuAdapter.initialize(mDfuHelperCallback);
        mBinInfo = builder.mBinInfo;
        mFileTypeInfos=builder.mFileTypeInfos;
        for (FileTypeInfo fileTypeInfo : mFileTypeInfos) {
            mListVersion.add(fileTypeInfo.getName());
        }
        mStepSize=mFileTypeInfos.size();

    }

    public int getStepSize() {
        return mStepSize;
    }

    /**
     * 最后执行开始升级
     */
    public void startOta() {
        this.startOta(mOtaType,mStep);
    }

    /**
     * 最后执行开始升级
     *
     * @param otaType {@link DfuConstants.OTA_MODE_NORMAL_FUNCTION, DfuConstants.OTA_MODE_SILENT_FUNCTION, DfuConstants.OTA_MODE_SILENT_EXTEND_FLASH, DfuConstants.OTA_MODE_SILENT_NO_TEMP, DfuConstants.OTA_MODE_AUTOMATIC}
     */
    public void startOta(int otaType,int step) {
        mOtaType = otaType;
        mStep = step;
        if (mOtaDeviceInfo == null) {
            mStartOta = true;
            return;
        }
        mStartOta = true;
        mDfuConfig.setProtocolType(mOtaDeviceInfo.getProtocolType());
        mDfuConfig.setChannelType(DfuConfig.CHANNEL_TYPE_GATT);
        mDfuConfig.setFileLocation(mFileLocation);
        mDfuConfig.setFilePath(mBinInfo.path);
        mDfuConfig.setAddress(mMac);
        mDfuConfig.setBreakpointResumeEnabled(true);//断点续传
        mDfuConfig.setAutomaticActiveEnabled(true);//自动激活
        mDfuConfig.setBatteryCheckEnabled(false);//是否开启电池检查
        mDfuConfig.setLowBatteryThreshold(10);//低电阀值
        mDfuConfig.setBatteryLevelFormat(0);//电池格数
        mDfuConfig.setVersionCheckEnabled(false);//是否开启版本检查
        mDfuConfig.setIcCheckEnabled(true);//是否开启芯片类型检查
        mDfuConfig.setSectionSizeCheckEnabled(true);//是否开启数据包检查
        mDfuConfig.setThroughputEnabled(false);//是否开启吞吐量
        mDfuConfig.setMtuUpdateEnabled(true);//是否更新mtu
        mDfuConfig.setWaitActiveCmdAckEnabled(false);//ack激活
        mDfuConfig.setFileSuffix("bin");
        if (step >= 0) {
            if (mFileTypeInfos.size()>step){
                FileTypeInfo fileTypeInfo = mFileTypeInfos.get(step);
                mDfuConfig.setFileIndicator((1 << fileTypeInfo.getBitNumber()));
            }
        }else {
            mDfuConfig.setFileIndicator(BinIndicator.INDICATOR_FULL);
        }
//        mDfuConfig.setFileIndicator();
        mDfuConfig.setOtaWorkMode(otaType);//默认升级模式
//        mDfuConfig.setOtaWorkMode(DfuConstants.OTA_MODE_NORMAL_FUNCTION);//默认升级模式
//        mDfuConfig.setOtaWorkMode(DfuConstants.OTA_MODE_SILENT_FUNCTION);//静默升级
        mDfuConfig.setBufferCheckLevel(DfuConfig.BUFFER_CHECK_ORIGINAL);//分区检查
        mDfuConfig.setSpeedControlEnabled(false);//是否开启限速
        mDfuConfig.setControlSpeed(0);//速度
        mDfuConfig.setConParamUpdateLatencyEnabled(false);//是否超时检查
        mDfuConfig.setLatencyTimeout(0);//超时
        boolean ret = mDfuAdapter.startOtaProcedure(mDfuConfig, true);
        if (ret) {
            //开始发送成功

        } else {
            //开始发送失败
        }
    }

    /**
     * 关闭升级的通到
     */
    public void close() {
        if (mDfuAdapter != null) {
            mDfuAdapter.abort();
            mDfuAdapter.close();

        }
    }


    private BaseDfuAdapter.DfuHelperCallback mDfuHelperCallback = new BaseDfuAdapter.DfuHelperCallback() {

        @Override
        public void onStateChanged(int state) {
            super.onStateChanged(state);
            if (state == DfuAdapter.STATE_INIT_OK) {
                if (!TextUtils.isEmpty(mMac)) {
                    mConnectParams = new ConnectParams.Builder().address(mMac).hid(false).reconnectTimes(3).localName(mDfuConfig.getLocalName()).build();
                    boolean b = mDfuAdapter.connectDevice(mConnectParams);
                }
            } else if (state == DfuAdapter.STATE_PREPARED) {
                mOtaDeviceInfo = mDfuAdapter.getOtaDeviceInfo();
                List<OtaModeInfo> supportedModes = mDfuAdapter.getSupportedModes();
                if (mOnBleOTAListener != null && mOnBleOTAListener instanceof OnRtkOtaInfoListener) {
                    ((OnRtkOtaInfoListener) mOnBleOTAListener).OnOtaType(supportedModes);
                }
                if (!TextUtils.isEmpty(mMac) && !TextUtils.isEmpty(mFilePath) && mFileLocation != -1 && mStartOta) {
                    startOta();
                }
            } else if (state == DfuAdapter.STATE_DISCONNECTED || state == DfuAdapter.STATE_CONNECT_FAILED) {
                mOtaDeviceInfo = null;
            }
        }

        @Override
        public void onTargetInfoChanged(OtaDeviceInfo otaDeviceInfo) {
            super.onTargetInfoChanged(otaDeviceInfo);
        }

        @Override
        public void onError(int i, int i1) {
            super.onError(i, i1);
            runOnMainThread(() -> {
                close();
                if (mOnBleOTAListener != null) {
                    mOnBleOTAListener.onOtaFailure(i, "");
                }
            });
        }

        @Override
        public void onProcessStateChanged(int state, Throughput throughput) {
            super.onProcessStateChanged(state, throughput);
            runOnMainThread(() -> {
                if (state == DfuConstants.PROGRESS_IMAGE_ACTIVE_SUCCESS) {
                    if (mOnBleOTAListener != null) {
                        mOnBleOTAListener.onOtaSuccess();
                    }
                    close();
                } else if (state == DfuConstants.PROGRESS_PROCESSING_ERROR || state == DfuConstants.PROGRESS_ABORTED) {
                    close();
                    if (mOnBleOTAListener != null) {
                        mOnBleOTAListener.onOtaFailure(state, "");
                    }
                } else if (state == DfuConstants.PROGRESS_PENDING_ACTIVE_IMAGE) {
                    // 升级包发送完成,等待激活
//                     mDfuAdapter.activeImage(true);//set true to active image and reset

                } else {
                    if (mOnBleOTAListener != null) {
                        mOnBleOTAListener.onOtaStatus(state);
                    }
                }
            });

        }

        @Override
        public void onProgressChanged(DfuProgressInfo dfuProgressInfo) {
            super.onProgressChanged(dfuProgressInfo);
            int progress = dfuProgressInfo.getProgress();
            runOnMainThread(() -> {
                if (mOnBleOTAListener != null) {
                    mOnBleOTAListener.onOtaProgress(progress, Math.min(dfuProgressInfo.getCurrentFileIndex() + 1, dfuProgressInfo.getMaxFileCount()), dfuProgressInfo.getMaxFileCount());
                }
            });
        }
    };

//    public static int getProgressStateResId(int var0) {
//        if (var0 != 527) {
//            switch (var0) {
//                case 257://准备升级
//                    return R.string.rtk_dfu_progress_state_origin;
//                case 258://固件激活成功
//                    return R.string.rtk_dfu_state_image_active_success;
//                case 259://已取消
//                    return R.string.rtk_dfu_state_aborted;
//                case 260://处理错误
//                    return R.string.rtk_dfu_state_error_processing;
//                default:
//                    switch (var0) {
//                        case 513://初始化
//                            return R.string.rtk_dfu_state_initialize;
//                        case 514://启动中
//                            return R.string.rtk_dfu_state_start;
//                        case 515:
//                        case 519://搜索设备
//                            return R.string.rtk_dfu_state_find_ota_remote;
//                        case 516:
//                        case 520://连接设备
//                            return R.string.rtk_dfu_state_connect_ota_remote;
//                        case 517://准备升级环境
//                            return R.string.rtk_dfu_state_prepare_dfu_processing;
//                        case 518://进入升级模式
//                            return R.string.rtk_dfu_state_remote_enter_ota;
//                        case 521://正在升级
//                            return R.string.rtk_dfu_state_start_ota_processing;
//                        case 522://正在HandOver
//                            return R.string.rtk_dfu_state_hand_over_processing;
//                        case 523://等待激活固件
//                            return R.string.rtk_dfu_state_pending_active_image;
//                        case 524://固件激活中
//                            return R.string.rtk_dfu_state_start_active_image;
//                        case 525://等待取消
//                            return R.string.rtk_dfu_state_abort_processing;
//                        default://未知
//                            return R.string.rtk_dfu_state_known;
//                    }
//            }
//        } else {
//            return R.string.rtk_dfu_state_scan_secondary_bud;
//        }
//    }


    public interface OnRtkOtaInfoListener extends OnBleOTAListener {

        default void OnOtaType(List<OtaModeInfo> otaModeInfoList) {
        }

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
