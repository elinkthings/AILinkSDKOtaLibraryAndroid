package com.elinkthings.bleotalibrary.listener;

/**
 * xing<br>
 * 2020/5/15<br>
 * OTA升级接口
 */
public interface OnBleOTAListener {


    /**
     * ota升级成功
     */
    default void onOtaSuccess(){}

    /**
     * ota升级失败
     */
    default void onOtaFailure(int cmd,String err){}

    /**
     * 进度
     * @param progress 当前数据块进度
     * @param currentCount 当前数据块
     * @param maxCount 总数据块
     */
    default void onOtaProgress(float progress,int currentCount,int maxCount){}


    /**
     * OTA状态
     * @param status 状态
     */
    default void onOtaStatus(int status){}

}
