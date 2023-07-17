package com.elinkthings.bleotalibrary.config;

/**
 * xing<br>
 * 2022/6/22<br>
 * Flash状态配置类
 */
public class FlashConfig {

    public final static String WATCH_NAME = "watch009";
    public final static String CUSTOMIZE_WATCH_NAME = "bgp_w005";
    public final static int MTU_MAX = 517;
    /**
     * 开始
     */
    public final static int START_FLASH = 1;
    /**
     * 暂停
     */
    public final static int PAUSE_FLASH = 2;
    /**
     * 停止
     */
    public final static int STOP_FLASH = 3;
    /**
     * Flash中
     */
    public final static int ING_FLASH= 4;
    /**
     * Flash 成功
     */
    public final static int FLASH_SUCCESS = 5;
    /**
     * Flash失败
     */
    public final static int FLASH_FAIL = 6;
    /**
     * Flash取消
     */
    public final static int FLASH_CANCEL = 7;

    /**
     * Flash失败,空间不足
     */
    public final static int FLASH_NOT_ENOUGH_SPACE = 8;

}
