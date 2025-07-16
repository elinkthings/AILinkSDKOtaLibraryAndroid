package com.elinkthings.bleotalibrary.netstrap;

public enum NetstrapState {

    /* establish ble connection for netstrap */

    TO_SCAN_DEVICE,

    TO_CONNECT_DEVICE,

    TO_EXCHANGE_MTU,

    TO_DISCOVER_SERVICE,

    TO_READ_CHARACTERISTIC,

    /*  start netstrap application behavior */

    TO_INDICATE_DEVICE_SCAN_AP,

    TO_INDICATE_DEVICE_CONNECT_AP,

    TO_READ_DEVICE_INFO,

    TO_WRITE_DEVICE_INFO,

    TO_READ_FIRMWARE_VERSION,

    OTA_START,

    OTA_SEND,

    OTA_END,

    TO_PROCESS_RX_PACKET,

    TO_CAL_VBATT,

    TO_CAL_IO_VOL,

    TO_CAL_TEMP,

    TO_RESET_DEVICE,

    TO_WRITE_WIFI_MAC,

    TO_READ_WIFI_MAC,

    TO_WRITE_BLE_MAC,

    TO_READ_BLE_MAC,

    TO_READ_DEVICE_WIFI_INFO,

    TO_SET_INIT_MODE,

    TO_SET_USER_MODE,

    TO_READ_DEVICE_MODE,

    TO_SEND_INPUTTING_STRING,

    TO_SEND_SINGLE_TONE,


    /* finalize netstrap application */

    TO_TERMINATE

}
