package com.elinkthings.bleotalibrary.listener;

/**
 * xing<br>
 * 2022/6/22<br>
 * Flash升级接口
 */
public interface OnBleFlashListener {


    /**
     * Flash写入成功
     */
    default void onFlashSuccess(){}

    /**
     * Flash写入失败
     *
     * @param cmd {FlashConfig}
     * @param err
     */
    default void onFlashFailure(int cmd, String err){}

    /**
     * 进度
     * @param progress 当前数据块进度
     * @param currentCount 当前数据块
     * @param maxCount 总数据块
     */
    default void onFlashProgress(float progress,int currentCount,int maxCount){}


    /**
     * Flash状态
     * @param status 状态 {FlashConfig}
     */
    default void onFlashStatus(int status){}

}
