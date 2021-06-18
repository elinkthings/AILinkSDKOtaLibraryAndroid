package com.elinkthings.bleotalibrary.netstrap;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class NetstrapPacket {

    public final static int PDU_TYPE_CMD_SCAN_REQ = 0x0000;

    public final static int PDU_TYPE_CMD_CONNECT_REQ = 0x0001;

    public final static int PDU_TYPE_CMD_READ_DEVICE_INFO_REQ = 0x0004;

    public final static int PDU_TYPE_CMD_WRITE_DEVICE_INFO_REQ = 0x0005;

    public final static int PDU_TYPE_CMD_BLEWIFI_REQ_WIFI_STATUS = 0x0006;

    public final static int PDU_TYPE_CMD_OTA_VERSION_REQ = 0x0100;

    public final static int PDU_TYPE_CMD_OTA_UPGRADE_REQ = 0x0101;

    public final static int PDU_TYPE_CMD_OTA_RAW_DATA_REQ = 0x0102;

    public final static int PDU_TYPE_CMD_OTA_END_REQ = 0x0103;


    public final static int PDU_TYPE_EVT_SCAN_RSP = 0x1000;

    public final static int PDU_TYPE_EVT_SCAN_END = 0x1001;

    public final static int PDU_TYPE_EVT_CONNECT_RSP = 0x1002;

    public final static int PDU_TYPE_EVT_READ_DEVICE_INFO_RSP = 0x1005;

    public final static int PDU_TYPE_EVT_WRITE_DEVICE_INFO_RSP = 0x1006;

    public final static int PDU_TYPE_BLEWIFI_RSP_WIFI_STATUS = 0x1007;

    public final static int PDU_TYPE_EVT_OTA_VERSION_RSP = 0x1100;

    public final static int PDU_TYPE_EVT_OTA_UPGRADE_RSP = 0x1101;

    public final static int PDU_TYPE_EVT_OTA_RAW_DATA_RSP = 0x1102;

    public final static int PDU_TYPE_EVT_OTA_END_RSP = 0x1103;

    public final static int PDU_TYPE_VBATT_CAL = 0x0401;

    public final static int PDU_TYPE_IO_VOL_CAL = 0x0402;

    public final static int PDU_TYPE_TEMP_CAL = 0x0403;

    public final static int PDU_TYPE_SET_DEVICE_MODE = 0x0404;

    public final static int PDU_TYPE_READ_DEVICE_MODE = 0x0405;

    public final static int PDU_TYPE_VBATT_CAL_RSP = 0x1401;

    public final static int PDU_TYPE_IO_VOL_CAL_RSP = 0x1402;

    public final static int PDU_TYPE_TEMP_CAL_RSP = 0x1403;

    public final static int PDU_TYPE_SET_DEVICE_MODE_RSP = 0x1404;

    public final static int PDU_TYPE_READ_DEVICE_MODE_RSP = 0x1405;

    public final static int PDU_TYPE_RESET_REQ = 0x0601;

    public final static int PDU_TYPE_WRITE_WIFI_MAC_REQ = 0x0602;

    public final static int PDU_TYPE_READ_WIFI_MAC_REQ = 0x0603;

    public final static int PDU_TYPE_WRITE_BLE_MAC_REQ = 0x0604;

    public final static int PDU_TYPE_READ_BLE_MAC_REQ = 0x0605;

    public final static int PDU_TYPE_SEND_BLE_STR_REQ = 0x0606;

    public final static int PDU_TYPE_SEND_SINGLE_TONE_REQ = 0x0607;

    public final static int PDU_TYPE_RESET_RSP = 0x1601;

    public final static int PDU_TYPE_WRITE_WIFI_MAC_RSP = 0x1602;

    public final static int PDU_TYPE_READ_WIFI_MAC_RSP = 0x1603;

    public final static int PDU_TYPE_WRITE_BLE_MAC_RSP = 0x1604;

    public final static int PDU_TYPE_READ_BLE_MAC_RSP = 0x1605;

    public final static int PDU_TYPE_SEND_BLE_STR_RSP = 0x1606;

    public final static int PDU_TYPE_SEND_SINGLE_TONE_RSP= 0x1607;


    public final static int PDU_TYPE_GOTER_SINGLE_TONE_REQ = 0x1900;


    public final static int SCAN_TYPE_ACTIVE = 0;

    public final static int SCAN_TYPE_PASSIVE = 1;

    public final static int SCAN_TYPE_MIX = 2;

    public final static int AUTH_MODE_OPEN = 0;

    public final static int AUTH_MODE_WEP = 1;

    public final static int AUTH_MODE_WPA_PSK = 2;

    public final static int AUTH_MODE_WPA2_PSK = 3;

    public final static int AUTH_MODE_WPA_WPA2_PSK = 4;

    public final static int AUTH_MODE_WPA2_ENTERPRISE_PSK = 5;

    public final static int CONNECT_STATUS_SUCCESS = 0;

    public int ApConnectStatus;

    private static ByteBuffer rxBuffer;

    private static int currentLength = 0;

    /******************** header ********************/
    private int cmdId;

    private int length;

    /******************** payload of PDU_TYPE_CMD_SCAN_REQ ********************/
    private boolean showHidden;

    private int scanType;

    /******************** payload of PDU_TYPE_CMD_CONNECT_REQ ********************/
    private byte[] bssid;

    private String password;

    private int Ap_ConnectStatus;

    private byte devicemode;

    /******************** payload of PDU_TYPE_CMD_OTA_UPGRADE_REQ ********************/
    private int maxRxOctet;

    /******************** payload of PDU_TYPE_CMD_OTA_RAW_DATA_REQ ********************/
    private byte[] rawData;

    /******************** payload of PDU_TYPE_CMD_OTA_END_REQ ********************/
    private int reason;

    /******************** payload of PDU_TYPE_EVT_SCAN_RSP ********************/
    private String ssid;

    private int authMode;

    private int rssi;

    private String ipaddr;

    private String maskaddr;

    private String gatewayaddr;
    /******************** payload of PDU_TYPE_EVT_CONNECT_RSP ********************/
    private int connectStatus;

    /******************** payload of PDU_TYPE_EVT_CONNECT_RSP ********************/
    private byte[] deviceId;

    private String manufactureName;

    private int writeStatus;

    /******************** payload of PDU_TYPE_CMD_OTA_VERSION_RSP ********************/
    private int status;

    private long projectId;

    private long chipId;

    private long fwId;

    /******************** BLE/WIFI MAC ********************/

    private byte[] bleMac = new byte[6];

    private byte[] WiFiMac = new byte[6];

    private String ReadbackStr;


    public String getWiFiMac() {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X", WiFiMac[0], WiFiMac[1], WiFiMac[2], WiFiMac[3], WiFiMac[4], WiFiMac[5]);
    }

    public String getBleMac() {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X", bleMac[0], bleMac[1], bleMac[2], bleMac[3], bleMac[4], bleMac[5]);
    }


    public String getReadbackStr() {
        return ReadbackStr;
    }

    public int getCmdId() {
        return cmdId;
    }

    public byte[] getBssid() {
        return bssid;
    }

    public String getSsid() {
        return ssid;
    }

    public String getIpaddr() {
        return ipaddr;
    }
    public String getMaskddr() {
        return maskaddr;
    }

    public String getGateway() {
        return gatewayaddr;
    }

    public int getAuthMode() {
        return authMode;
    }

    public int getRssi() {
        return rssi;
    }

    public int getConnectStatus() {
        return connectStatus;
    }

    public int getReason() {
        return reason;
    }

    public int getStatus() {
        return status;
    }

    public long getProjectId() {
        return projectId;
    }

    public long getChipId() {
        return chipId;
    }

    public long getFwId() {
        return fwId;
    }

    public byte[] getBytes() {
        ByteBuffer buf = null;

        switch (cmdId) {
            case PDU_TYPE_VBATT_CAL:
                buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 4)
                        .put(rawData);
                break;
            case PDU_TYPE_IO_VOL_CAL:
                buf = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 5)
                        .put(rawData);
                break;
            case PDU_TYPE_TEMP_CAL:
                buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 4)
                        .put(rawData);
                break;

            case PDU_TYPE_RESET_REQ:
                buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 0);
                break;
            case PDU_TYPE_SEND_BLE_STR_REQ:
                buf = ByteBuffer.allocate(4+rawData.length).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) rawData.length)
                        .put(rawData);
                break;

            case PDU_TYPE_SEND_SINGLE_TONE_REQ:
                buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) rawData.length)
                        .put(rawData);
                break;

            case PDU_TYPE_SET_DEVICE_MODE:
                buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 1)
                        .put(devicemode);
                break;

            case PDU_TYPE_READ_DEVICE_MODE:
                buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 0);
                break;

            case PDU_TYPE_CMD_BLEWIFI_REQ_WIFI_STATUS:
                buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 0);
                break;
            case PDU_TYPE_WRITE_WIFI_MAC_REQ:
                buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 6)
                        .put(rawData);
                break;
            case PDU_TYPE_READ_WIFI_MAC_REQ:
                buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 0);
                break;
            case PDU_TYPE_WRITE_BLE_MAC_REQ:
                buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 6)
                        .put(rawData);
                break;

            case PDU_TYPE_READ_BLE_MAC_REQ:
                buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 0);
                break;

            case PDU_TYPE_CMD_SCAN_REQ:
                buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 2)
                        .put((byte) (showHidden ? 1 : 0))
                        .put((byte) scanType);
                break;

            case PDU_TYPE_CMD_CONNECT_REQ:
                int temp_len = password.length();
                buf = ByteBuffer.allocate(12 + password.length()).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) (8 + password.length()))
                        .put(bssid)
                        .put((byte) Ap_ConnectStatus)
                        .put((byte) password.length())
                        .put(password.getBytes());
                break;

            case PDU_TYPE_CMD_READ_DEVICE_INFO_REQ:
            case PDU_TYPE_CMD_OTA_VERSION_REQ:
                buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 0);
                break;

            case PDU_TYPE_CMD_WRITE_DEVICE_INFO_REQ:
                buf = ByteBuffer.allocate(11 + manufactureName.length()).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) (7 + manufactureName.length()))
                        .put(deviceId)
                        .put((byte) manufactureName.length())
                        .put(manufactureName.getBytes());
                break;

            case PDU_TYPE_CMD_OTA_UPGRADE_REQ:
                buf = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 26)
                        .putShort((short) maxRxOctet)
                        .put(rawData);
                break;

            case PDU_TYPE_CMD_OTA_RAW_DATA_REQ:
                buf = ByteBuffer.allocate(4 + rawData.length).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) rawData.length)
                        .put(rawData);
                break;

            case PDU_TYPE_CMD_OTA_END_REQ:
                buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) cmdId)
                        .putShort((short) 1)
                        .put((byte) reason);
                break;

        }
        return buf.array();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        switch (cmdId) {
            case PDU_TYPE_CMD_SCAN_REQ:
                str.append("[SCAN_REQ]\n")
                        .append("showHidden: " + showHidden + "\n")
                        .append("scanType: " + scanType + "\n");
                break;

            case PDU_TYPE_EVT_SCAN_RSP:
                str.append("[SCAN_RSP]\n")
                        .append("ssid: " + ssid + "\n")
                        .append("bssid: " + getMacAddress(bssid) + "\n")
                        .append("authMode: " + getAuthModeDescription(authMode) + "\n")
                        .append("rssi: " + rssi + "\n");
                break;

            case PDU_TYPE_EVT_SCAN_END:
                str.append("[SCAN_END]\n");
                break;

            case PDU_TYPE_CMD_CONNECT_REQ:
                str.append("[CONNECT_REQ]\n")
                        .append("bssid: " + getMacAddress(bssid) + "\n")
                        .append("password: " + password + "\n");
                break;

            case PDU_TYPE_EVT_CONNECT_RSP:
                str.append("[CONNECT_RSP]\n")
                        .append("status: " + (connectStatus == CONNECT_STATUS_SUCCESS ? "SUCCESS" : "FAIL") + "\n");
                break;

            case PDU_TYPE_CMD_READ_DEVICE_INFO_REQ:
                str.append("[READ_DEVICE_REQ]\n");
                break;

            case PDU_TYPE_EVT_READ_DEVICE_INFO_RSP:
                str.append("[READ_DEVICE_RSP]\n")
                        .append("deviceId: " + getMacAddress(deviceId) + "\n")
                        .append("manufactureName: " + manufactureName + "\n");
                break;

            case PDU_TYPE_CMD_WRITE_DEVICE_INFO_REQ:
                str.append("[WRITE_DEVICE_REQ]\n")
                        .append("deviceId: " + getMacAddress(deviceId) + "\n")
                        .append("manufactureName: " + manufactureName + "\n");
                break;

            case PDU_TYPE_EVT_WRITE_DEVICE_INFO_RSP:
                str.append("[WRITE_DEVICE_RSP]\n")
                        .append("status: " + writeStatus + "\n");
                break;

            case PDU_TYPE_CMD_OTA_VERSION_REQ:
                str.append("[OTA_VERSION_REQ]\n");
                break;

            case PDU_TYPE_EVT_OTA_VERSION_RSP:
                str.append("[OTA_VERSION_RSP]\n")
                        .append("status: " + status + "\n")
                        .append("projectId: " + String.format("0x%04X", projectId) + "\n")
                        .append("chipId: " + String.format("0x%04X", chipId) + "\n")
                        .append("fwId: " + String.format("0x%04X", fwId) + "\n");
                break;

            case PDU_TYPE_CMD_OTA_UPGRADE_REQ:
                str.append("[OTA_UPGRADE_REQ]\n")
                        .append("maxRxOctet: " + maxRxOctet + "\n");
                break;

            case PDU_TYPE_EVT_OTA_UPGRADE_RSP:
                str.append("[OTA_UPGRADE_RSP]\n")
                        .append("status: " + status + "\n");
                break;

            case PDU_TYPE_CMD_OTA_RAW_DATA_REQ:

                str.append("[OTA_RAW_DATA_REQ]\n")
                        .append("rawData.length: " + rawData.length + "\n");
                break;

            case PDU_TYPE_EVT_OTA_RAW_DATA_RSP:
                str.append("[OTA_RAW_DATA_RSP]\n");
                break;

            case PDU_TYPE_CMD_OTA_END_REQ:
                str.append("[OTA_END_REQ]\n")
                        .append("reason: " + reason + "\n");
                break;

            case PDU_TYPE_EVT_OTA_END_RSP:
                str.append("[OTA_END_RSP]\n")
                        .append("reason: " + reason + "\n");
                break;
            case PDU_TYPE_VBATT_CAL_RSP:
                str.append("[VBATT_CAL_END_RSP]\n")
                        .append("reason: " + reason + "\n");
                break;

            case PDU_TYPE_IO_VOL_CAL_RSP:
                str.append("[IO_VOL_CAL_END_RSP]\n")
                        .append("reason: " + reason + "\n");
                break;

            case PDU_TYPE_TEMP_CAL_RSP:
                str.append("[TEMP_CAL_END_RSP]\n")
                        .append("reason: " + reason + "\n");
                break;
            case PDU_TYPE_RESET_RSP:
                str.append("[PDU_TYPE_RESET_RSP]\n")
                        .append("reason: " + reason + "\n");
                break;
            case PDU_TYPE_WRITE_WIFI_MAC_RSP:
                str.append("[PDU_TYPE_WRITE_WIFI_MAC_RSP]\n")
                        .append("reason: " + reason + "\n");
                break;
            case PDU_TYPE_WRITE_BLE_MAC_RSP:
                str.append("[PDU_TYPE_WRITE_BLE_MAC_RSP]\n")
                        .append("reason: " + reason + "\n");
                break;
            case PDU_TYPE_READ_WIFI_MAC_RSP:
                str.append("[PDU_TYPE_READ_WIFI_MAC_RSP]\n")
                        .append("Wifi Mac: " + String.format("%02X-%02X-%02X-%02X-%02X-%02X", WiFiMac[0], WiFiMac[1], WiFiMac[2], WiFiMac[3], WiFiMac[4], WiFiMac[5]) + "\n");

                break;
            case PDU_TYPE_READ_BLE_MAC_RSP:
                str.append("[PDU_TYPE_READ_BLE_MAC_RSP]\n")
                        .append("Ble Mac: " + String.format("%02X-%02X-%02X-%02X-%02X-%02X", bleMac[0], bleMac[1], bleMac[2], bleMac[3], bleMac[4], bleMac[5]) + "\n");
                break;
            case PDU_TYPE_SEND_BLE_STR_RSP:
                str.append("[PDU_TYPE_SEND_BLE_STR_RSP]\n")
                        .append("status: " + reason + "\n");
                break;
            case PDU_TYPE_SET_DEVICE_MODE_RSP:
                str.append("[PDU_TYPE_SET_DEVICE_MODE_RSP]\n")
                        .append("status: " + reason + "\n");
                break;
            case PDU_TYPE_READ_DEVICE_MODE_RSP:
                str.append("[PDU_TYPE_READ_DEVICE_MODE_RSP]\n")
                        .append("Mode: " + reason + "\n");
                break;
            case PDU_TYPE_SEND_SINGLE_TONE_RSP:
                str.append("[PDU_TYPE_SEND_SINGLE_TONE_RSP]\n")
                        .append("status: " + reason + "\n");
                break;
        }

        return str.toString();
    }

    public static NetstrapPacket createResetPacket() {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_RESET_REQ;
        return packet;
    }

    public static NetstrapPacket createWriteWiFiMacPacket(byte[] bMac) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_WRITE_WIFI_MAC_REQ;
        packet.rawData = bMac;
        return packet;
    }

    public static NetstrapPacket createBleStringPacket(byte[] bInputStr){
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_SEND_BLE_STR_REQ;
        packet.rawData = bInputStr;
        return packet;
    }

    public static NetstrapPacket createDeviceModePacket(byte iMode){
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_SET_DEVICE_MODE;
        packet.devicemode = iMode;
        return packet;
    }

    public static NetstrapPacket createReadDeviceModePacket(){
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_READ_DEVICE_MODE;
        return packet;
    }

    public static NetstrapPacket createReadWiFiMacPacket() {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_READ_WIFI_MAC_REQ;
        return packet;
    }

    public static NetstrapPacket createWriteBleMacPacket(byte[] bMac){
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_WRITE_BLE_MAC_REQ;
        packet.rawData = bMac;
        return packet;
    }

    public static NetstrapPacket createReadBleMacPacket() {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_READ_BLE_MAC_REQ;
        return packet;
    }

    public static NetstrapPacket createReadDeviceWiFiInfoPacket(){
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_CMD_BLEWIFI_REQ_WIFI_STATUS;
        return packet;

    }


    public static NetstrapPacket createcalvbattPacket(float fvbatt) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_VBATT_CAL;
        packet.rawData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(fvbatt).array();
        return packet;
    }

    public static NetstrapPacket createcaliovolPacket(byte IOpin, float fiovol) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_IO_VOL_CAL;
        packet.rawData = new byte[5];
        packet.rawData[0] = IOpin;

        byte[] ttemp;
        ttemp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(fiovol).array();
        packet.rawData[1] = ttemp[0];
        packet.rawData[2] = ttemp[1];
        packet.rawData[3] = ttemp[2];
        packet.rawData[4] = ttemp[3];
        return packet;
    }


    public static NetstrapPacket createSingleTonePacket(short nMode, int nFreq) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_SEND_SINGLE_TONE_REQ;
        packet.rawData = new byte[6];
        byte[] ttemp1;
        ttemp1 = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(nMode).array();
        packet.rawData[0] = ttemp1[0];
        packet.rawData[1] = ttemp1[1];

        byte[] ttemp;
        ttemp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(nFreq).array();
        packet.rawData[2] = ttemp[0];
        packet.rawData[3] = ttemp[1];
        packet.rawData[4] = ttemp[2];
        packet.rawData[5] = ttemp[3];
        return packet;
    }

    public static NetstrapPacket createcaltempPacket(float ftemp) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_TEMP_CAL;
        packet.rawData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(ftemp).array();
        return packet;
    }

    public static NetstrapPacket createScanReqPacket(boolean showHidden, int scanType) {
        NetstrapPacket packet = new NetstrapPacket();

        packet.cmdId = PDU_TYPE_CMD_SCAN_REQ;
        packet.showHidden = showHidden;
        packet.scanType = scanType;

        return packet;
    }

    public static NetstrapPacket createConnectReqPacket(byte[] bssid, String password, int ConnectStatus) {
        NetstrapPacket packet = new NetstrapPacket();

        packet.cmdId = PDU_TYPE_CMD_CONNECT_REQ;
        packet.bssid = bssid;
        packet.password = password;
        packet.Ap_ConnectStatus = ConnectStatus;

        return packet;
    }

    public static NetstrapPacket createReadDeviceInfoReqPacket() {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_CMD_READ_DEVICE_INFO_REQ;
        return packet;
    }

    public static NetstrapPacket createWriteDeviceInfoReqPacket(byte[] deviceId, String manufactureName) {
        NetstrapPacket packet = new NetstrapPacket();

        packet.cmdId = PDU_TYPE_CMD_WRITE_DEVICE_INFO_REQ;
        packet.deviceId = deviceId;
        packet.manufactureName = manufactureName;

        return packet;
    }

    public static NetstrapPacket createOtaVersionReqPacket() {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_CMD_OTA_VERSION_REQ;
        return packet;
    }

    public static NetstrapPacket createOtaUpgradeReqPacket(int maxRxOctet, byte[] header) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_CMD_OTA_UPGRADE_REQ;
        packet.maxRxOctet = maxRxOctet;
        packet.rawData = header;
        return packet;
    }

    public static NetstrapPacket createOtaRawDataReqPacket(byte[] rawData) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_CMD_OTA_RAW_DATA_REQ;
        packet.rawData = rawData;
        return packet;
    }

    public static NetstrapPacket createOtaEndReqPacket(int reason) {
        NetstrapPacket packet = new NetstrapPacket();
        packet.cmdId = PDU_TYPE_CMD_OTA_END_REQ;
        packet.reason = reason;
        return packet;
    }

    public static synchronized List<NetstrapPacket> decodePacket(byte[] data)
    {
        StringBuilder log = new StringBuilder();
        List<NetstrapPacket> ls = new ArrayList<>();


        // full buffer
        if( rxBuffer == null )
            rxBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);

        rxBuffer.put(data);
        currentLength += data.length;
        log.append("in: " + data.length + ", all: " + currentLength + ", ");

        // check whether PDU is complete
        while(true)
        {
            int allPacketLength = rxBuffer.getShort(2) + 4;
            if( currentLength >= allPacketLength )
            {
                NetstrapPacket rxPacket = new NetstrapPacket();
                rxPacket.cmdId = rxBuffer.getShort(0);

                // decode by PDU_TYPE
                switch (rxPacket.cmdId) {
                    case PDU_TYPE_EVT_SCAN_RSP:
                        decodeScanRsp(rxPacket, rxBuffer);
                        break;
                    case PDU_TYPE_EVT_SCAN_END:
                        break;
                    case PDU_TYPE_EVT_CONNECT_RSP:
                        rxPacket.connectStatus = rxBuffer.get(4);
                        break;
                    case PDU_TYPE_EVT_READ_DEVICE_INFO_RSP:
                        decodeReadDeviceInfoRsp(rxPacket, rxBuffer);
                        break;
                    case PDU_TYPE_EVT_WRITE_DEVICE_INFO_RSP:
                        rxPacket.writeStatus = rxBuffer.get(4);
                        break;
                    case PDU_TYPE_EVT_OTA_VERSION_RSP:
                        decodeOtaVersionRsp(rxPacket, rxBuffer);
                        break;
                    case PDU_TYPE_EVT_OTA_UPGRADE_RSP:
                        rxPacket.status = rxBuffer.get(4);
                        break;
                    case PDU_TYPE_VBATT_CAL_RSP:
                        rxPacket.reason = rxBuffer.get(6);
                        break;
                    case PDU_TYPE_IO_VOL_CAL_RSP:
                        rxPacket.reason = rxBuffer.get(6);
                        break;
                    case PDU_TYPE_TEMP_CAL_RSP:
                        rxPacket.reason = rxBuffer.get(6);
                        break;
                    case PDU_TYPE_RESET_RSP:
                        rxPacket.reason = rxBuffer.get(6);
                        break;
                    case PDU_TYPE_READ_WIFI_MAC_RSP:
                        decodeReadwifiMacRsp(rxPacket, rxBuffer);
                        break;


                    case PDU_TYPE_READ_BLE_MAC_RSP:
                        decodeReadbleMacRsp(rxPacket, rxBuffer);
                        break;

                    case PDU_TYPE_BLEWIFI_RSP_WIFI_STATUS:
                        decodeDeviceWifiStatusRsp(rxPacket, rxBuffer);
                        break;

                    case PDU_TYPE_SEND_BLE_STR_RSP:
                        rxPacket.reason = rxBuffer.get(4);
                        break;

                    case PDU_TYPE_SET_DEVICE_MODE_RSP:
                        rxPacket.reason = rxBuffer.get(4);
                        break;
                    case PDU_TYPE_READ_DEVICE_MODE_RSP:
                        rxPacket.reason = rxBuffer.get(4);
                        break;
                    case PDU_TYPE_SEND_SINGLE_TONE_RSP:
                        rxPacket.reason = rxBuffer.get(4);
                        break;
                }

                // reset buffer
                currentLength -= allPacketLength;
                byte[] rxBytes = new byte[1024];
                for (int i = 0; i < currentLength; i++) {
                    rxBytes[i] = rxBuffer.get(allPacketLength + i);
                }

                ByteBuffer newBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                newBuffer.put(rxBytes, 0, currentLength);
                rxBuffer = newBuffer;

                // Log.e("aaaaaaaaaaaaaaaaaaa = " + currentLength, "<NetstrapPacket> decodePacket = " + ls.size() + " allPacketLength = " + allPacketLength );

                ls.add(rxPacket);
            }
            else
            {
                currentLength = 0;
                // Log.e("bbbbbbbbbbbbbbbbbbbbbb = " + currentLength, "<NetstrapPacket> decodePacket = " + ls.size() + " allPacketLength = " + allPacketLength );
               break;
            }
        }
        log.append("rx_pkt: " + ls.size());
        //Log.i("OPL1000", log.toString());

        rxBuffer.clear();

        return ls;
    }

    private static void decodeReadwifiMacRsp(NetstrapPacket pkt, ByteBuffer buf) {
        int deviceWifiMacOffset = 4;
        int deviceWiFiMACLen = 6;


        // deviceId
        byte[] deviceId = new byte[deviceWiFiMACLen];
        for (int i = 0; i < deviceWiFiMACLen; i++)
            deviceId[i] = buf.order(ByteOrder.BIG_ENDIAN).get(deviceWifiMacOffset + i);
        pkt.WiFiMac = deviceId;


    }

    private static void decodeReadbleMacRsp(NetstrapPacket pkt, ByteBuffer buf) {
        int devicebleMacOffset = 4;
        int devicebleMACLen = 6;


        // deviceId
        byte[] deviceId = new byte[devicebleMACLen];
        for (int i = 0; i < devicebleMACLen; i++)
            deviceId[i] = buf.order(ByteOrder.BIG_ENDIAN).get(devicebleMacOffset + i);
        pkt.bleMac = deviceId;


    }

    private static void decodeDeviceWifiStatusRsp(NetstrapPacket pkt, ByteBuffer buf) {

        int ssidLength = buf.get(5);
        byte[] ssid = new byte[ssidLength];
        for (int i = 0; i < ssidLength; i++)
            ssid[i] = buf.get(i + 6);
        pkt.ssid = new String(ssid);


        byte[] bssid = new byte[6];
        for (int i = 0; i < 6; i++)
            bssid[i] = buf.get(i + 6 + ssidLength);

        byte[] ip=new byte[12];
        int[] IPaddr = new int[12];
        for (int i = 0; i < 12; i++){
            ip[i]=buf.get(i + 12 + ssidLength);
            if(ip[i]<0) {
                IPaddr[i] = ip[i] + 256;
            }else{
                IPaddr[i] = ip[i];
            }
        }



        pkt.ipaddr = String.format("%d.%d.%d.%d", IPaddr[0], IPaddr[1], IPaddr[2], IPaddr[3]);
        pkt.maskaddr = String.format("%d.%d.%d.%d", IPaddr[4], IPaddr[5], IPaddr[6], IPaddr[7]);
        pkt.gatewayaddr = String.format("%d.%d.%d.%d", IPaddr[8], IPaddr[9], IPaddr[10], IPaddr[11]);


    }


    private static void decodeScanRsp(NetstrapPacket pkt, ByteBuffer buf) {

        try{
            String filename = "output.txt";
            File myFile = new File(Environment.getExternalStorageDirectory(), filename);
            if (!myFile.exists()) {
                myFile.createNewFile();
            }


            FileChannel channel = new FileOutputStream(myFile, true).getChannel();
            // Flips this buffer.  The limit is set to the current position and then
            // the position is set to zero.  If the mark is defined then it is discarded.
            buf.flip();

            // Writes a sequence of bytes to this channel from the given buffer.
            channel.write(buf);



            // close the channel
            channel.close();

        }catch(IOException e){
            e.printStackTrace();
        }


        // ssid
        int ssidLength = buf.get(4);
        byte[] ssid = new byte[ssidLength];
        for (int i = 0; i < ssidLength; i++)
            ssid[i] = buf.get(i + 5);
        pkt.ssid = new String(ssid);

        // bssid
        byte[] bssid = new byte[6];
        for (int i = 0; i < 6; i++)
            bssid[i] = buf.get(i + 5 + ssidLength);
        pkt.bssid = bssid;

        // authMode
        pkt.authMode = buf.get(11 + ssidLength);

        // rssi
        pkt.rssi = buf.get(12 + ssidLength);
    }

    private static void decodeReadDeviceInfoRsp(NetstrapPacket pkt, ByteBuffer buf) {
        int deviceIdOffset = 4;
        int deviceIdLen = 6;
        int manufactureNameLengthOffset = 10;
        int manufactureNameOffset = 11;

        // deviceId
        byte[] deviceId = new byte[deviceIdLen];
        for (int i = 0; i < deviceIdLen; i++)
            deviceId[i] = buf.get(deviceIdOffset + i);
        pkt.deviceId = deviceId;

        // manufactureName
        int manufactureNameLength = buf.get(manufactureNameLengthOffset);
        byte[] manufactureName = new byte[manufactureNameLength];
        for (int i = 0; i < manufactureNameLength; i++)
            manufactureName[i] = buf.get(manufactureNameOffset + i);
        pkt.manufactureName = new String(manufactureName);
    }

    private static void decodeOtaVersionRsp(NetstrapPacket pkt, ByteBuffer buf) {
        int statusOffset = 4;
        int projectIdOffset = 5;
        int chipIdOffset = 7;
        int fwIdOffset = 9;

        pkt.status = buf.get(statusOffset);
        pkt.projectId = buf.getInt(projectIdOffset);
        pkt.chipId = buf.getInt(chipIdOffset);
        pkt.fwId = buf.getInt(fwIdOffset);
    }

    public static String getAuthModeDescription(int authMode) {
        String description = "";

        switch (authMode) {
            case AUTH_MODE_OPEN:
                description = "OPEN";
                break;
            case AUTH_MODE_WEP:
                description = "WEP";
                break;
            case AUTH_MODE_WPA_PSK:
                description = "WPA";
                break;
            case AUTH_MODE_WPA2_PSK:
                description = "WPA2";
                break;
            case AUTH_MODE_WPA_WPA2_PSK:
                description = "WPA/WPA2";
                break;
            case AUTH_MODE_WPA2_ENTERPRISE_PSK:
                description = "WPA2-Enterprise";
                break;
        }

        return description;
    }

    public static String getMacAddress(byte[] bssid) {
        return String.format("%02X-%02X-%02X-%02X-%02X-%02X", bssid[0], bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]);
    }

    static void dump(byte[] buf) {
        for (int i = 0; i < buf.length; i++) {
            System.out.printf("%02X ", buf[i]);
        }
        System.out.println("\n\n");
    }

    public static void main(String[] args) {
        NetstrapPacket otaVerReq = NetstrapPacket.createOtaVersionReqPacket();
        System.out.println(otaVerReq);
        dump(otaVerReq.getBytes());

        List<NetstrapPacket> ls = NetstrapPacket.decodePacket(new byte[] {0x00, 0x11, 0x09, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00});
        for (NetstrapPacket pkt : ls) {
            System.out.println(pkt);
        }

        NetstrapPacket otaUpgradeReq = NetstrapPacket.createOtaUpgradeReqPacket(300, new byte[] {1,2,3,4});
        System.out.println(otaUpgradeReq);
        dump(otaUpgradeReq.getBytes());

        ls = NetstrapPacket.decodePacket(new byte[] {0x01, 0x11, 0x01, 0x00, 0x00});
        for (NetstrapPacket pkt : ls) {
            System.out.println(pkt);
        }

        NetstrapPacket otaRawDataReq = NetstrapPacket.createOtaRawDataReqPacket(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        System.out.println(otaRawDataReq);
        dump(otaRawDataReq.getBytes());

        NetstrapPacket otaEndReq = NetstrapPacket.createOtaEndReqPacket(0);
        System.out.println(otaEndReq);
        dump(otaEndReq.getBytes());

        ls = NetstrapPacket.decodePacket(new byte[] {0x02, 0x11, 0x01, 0x00, 0x00});
        for (NetstrapPacket pkt : ls) {
            System.out.println(pkt);
        }
    }

}
