package com.elinkthings.bleotalibrary.zhlx;

class OtaConstants {

    private OtaConstants() {}

    /**
     * 默认的MTU大小（BLE默认）
     */
    public static final int DEFAULT_MTU_SIZE = 23;
    /**
     * 最大MTU大小（BLE使用）
     */
    public static final int MAX_MTU_SIZE     = 517;

    /**
     * 固件要求接收的每次分包的大小
     */
    public static final int DEFAULT_PACKET_SIZE = 240;

    /**
     * 默认数据块大小（固件收到每一块后会回复确认）
     */
    public static final int DEFAULT_BLOCK_SIZE   = 4 * 1024;
    /**
     * 未定义块大小，取-1（0xFFFFFFFF），至发送完毕都不需要固件确认数据接收
     */
    public static final int UNDEFINED_BLOCK_SIZE = 0xFFFFFFFF;

}
