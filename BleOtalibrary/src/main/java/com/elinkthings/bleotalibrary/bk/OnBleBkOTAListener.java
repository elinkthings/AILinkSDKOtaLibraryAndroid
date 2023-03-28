package com.elinkthings.bleotalibrary.bk;

import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;

/**
 * xing<br>
 * 2020/5/15<br>
 * OTA升级接口
 */
public interface OnBleBkOTAListener extends OnBleOTAListener {


    /**
     * 设备版本
     * @param deviceVersion 设备版本
     * @param romVersion    rom版本
     */
    default void onDeviceVersion(String deviceVersion,String romVersion){

    }


}
