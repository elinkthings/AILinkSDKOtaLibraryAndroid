package com.elinkthings.bleotalibrary.zhlx;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class OtaCommandGenerator {

    private static final String TAG = OtaCommandGenerator.class.getSimpleName();

    private static final byte[] CMD_OTA_IDENTIFICATION = { (byte) 0xCC, (byte) 0xAA, 0x55, (byte) 0xEE, 0x12, 0x19, (byte) 0xE4 };

    public static final byte CMD_OTA_INFO       = (byte)0xA0;
    public static final byte CMD_SEND_DATA      = (byte)0x20;
    public static final byte CMD_GET_INFO       = (byte)0x91;
    public static final byte CMD_NOTIFY_STATUS  = (byte)0x90;
    public static final byte CMD_GET_INFO_TLV   = (byte)0x92;

    public static final byte STATE_OK               = (byte)0x00;
    public static final byte STATE_TWS_DISCONNECTED = (byte)0x80;
    public static final byte STATE_DONE             = (byte)0xFF;
    public static final byte STATE_PAUSE            = (byte)0xFD;
    public static final byte STATE_CONTINUE         = (byte)0xFE;

    public static final byte CMD_GET_INFO_TYPE_VERSION      = (byte)0x01;
    public static final byte CMD_GET_INFO_TYPE_UPDATE       = (byte)0x02;

    public static final byte CMD_GET_INFO_TYPE_CAPABILITIES = (byte)0x03;
    public static final int INFO_CAPABILITIES_TWS               = 0x0001;

    public static final byte CMD_GET_INFO_TYPE_STATUS       = (byte)0x04;
    public static final int INFO_STATUS_TWS_CONNECTED           = 0x0001;

    public static final byte CMD_GET_INFO_TYPE_ADDRESS      = (byte)0x05;

    public static final byte CMD_GET_INFO_TYPE_CHANNEL      = (byte)0x06;
    public static final byte INFO_CHANNEL_RIGHT             = (byte)0x00;
    public static final byte INFO_CHANNEL_LEFT              = (byte)0x01;


    private OtaDataProvider dataProvider;

    private byte seqNum = 0;

    /**
     * 重置内部数据，之后可用于下一次升级。
     */
    public void reset() {
        seqNum = 0;
    }

    public OtaCommandGenerator() {
    }

    /**
     * 构造器
     * @param dataProvider {@link OtaDataProvider}
     */
    public OtaCommandGenerator(OtaDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    /* Getter & Setter */

    /**
     * 设置{@link OtaDataProvider}
     * @param dataProvider {@link OtaDataProvider}
     */
    public void setDataProvider(OtaDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    /* TLV format Command Data Generator */

    private byte[] generateCmdGetInfoData(byte cmd, String value) {
        byte[] valueData = value.getBytes();
        ByteBuffer bb = ByteBuffer.allocate(2 + valueData.length);
        bb.put(cmd);
        bb.put((byte)valueData.length);
        bb.put(valueData);
        return bb.array();
    }

    private byte[] generateCmdGetInfoData(byte cmd, byte[] value) {
        ByteBuffer bb = ByteBuffer.allocate(2 + value.length);
        bb.put(cmd);
        bb.put((byte)value.length);
        bb.put(value);
        return bb.array();
    }

    private byte[] generateCmdGetInfoData(byte cmd, int value) {
        ByteBuffer bb = ByteBuffer.allocate(6)
                .order(ByteOrder.LITTLE_ENDIAN);
        bb.put(cmd);
        bb.put((byte)4);
        bb.putInt(value);
        return bb.array();
    }

    private byte[] generateCmdGetInfoData(byte cmd, short value) {
        ByteBuffer bb = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN);
        bb.put(cmd);
        bb.put((byte)2);
        bb.putShort(value);
        return bb.array();
    }

    private byte[] generateCmdGetInfoData(byte cmd, byte value) {
        ByteBuffer bb = ByteBuffer.allocate(3);
        bb.put(cmd);
        bb.put((byte)1);
        bb.put(value);
        return bb.array();
    }

    private byte[] generateCmdGetInfoData(byte cmd) {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.put(cmd);
        bb.put((byte)0);
        return bb.array();
    }

    /* Info Command Data, used by TLV */

    /**
     * 获取版本信息的命令（TLV格式）
     * @return 命令数据
     */
    public byte[] cmdDataGetInfoVersion() {
        return generateCmdGetInfoData(CMD_GET_INFO_TYPE_VERSION);
    }

    /**
     * 获取升级信息的命令（TLV格式）
     * @param version 当前固件版本
     * @param hashData 当前固件文件的HASH
     * @return 命令数据
     */
    public byte[] cmdDataGetInfoUpdate(int version, byte[] hashData) {
        ByteBuffer bb = ByteBuffer.allocate(6)
                .order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short)version);

        if (hashData != null) {
            bb.put(hashData, 0, 4);
        } else {
            bb.putInt(0xFFFFFFFF);
        }

        return generateCmdGetInfoData(CMD_GET_INFO_TYPE_UPDATE, bb.array());
    }

    /**
     * 获取TWS信息的命令（TLV格式）
     * @return 命令数据
     */
    public byte[] cmdDataGetInfoTWS() {
        return generateCmdGetInfoData(CMD_GET_INFO_TYPE_CAPABILITIES);
    }

    /**
     * 获取状态的命令，TWS连接状态（TLV格式）
     * @return 命令数据
     */
    public byte[] cmdDataGetInfoStatus() {
        return generateCmdGetInfoData(CMD_GET_INFO_TYPE_STATUS);
    }

    /**
     * 获取蓝牙地址的命令，用于BLE升级使用（TLV格式）
     * @return 命令数据
     */
    public byte[] cmdDataGetInfoAddress() {
        return generateCmdGetInfoData(CMD_GET_INFO_TYPE_ADDRESS);
    }

    /**
     * 获取TWS通道信息的命令（TLV格式）
     * @return 命令数据
     */
    public byte[] cmdDataGetInfoChannel() {
        return generateCmdGetInfoData(CMD_GET_INFO_TYPE_CHANNEL);
    }

    /* Command with TLV format */

    /**
     * 一次性获取全部信息
     * @return 命令数据
     */
    public byte[] cmdGetAllInfo() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(CMD_GET_INFO_TLV);
        os.write(seqNum++);

        try {
            os.write(cmdDataGetInfoVersion());
            os.write(cmdDataGetInfoTWS());
            os.write(cmdDataGetInfoStatus());
            os.write(cmdDataGetInfoAddress());
            os.write(cmdDataGetInfoChannel());
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate Command", e);
            e.printStackTrace();
        }

        return os.toByteArray();
    }

    /* Command */

    /**
     * 供固件识别OTA的命令（固件接收到后用于准备OTA环境）
     * @return 命令数据
     */
    public byte[] cmdOtaIdentification() {
        return CMD_OTA_IDENTIFICATION;
    }

    /**
     * 获取版本信息的命令
     * @return 命令数据
     */
    public byte[] cmdGetInfoVersion() {
        ByteBuffer bb = ByteBuffer.allocate(3);
        bb.put(CMD_GET_INFO);
        bb.put(seqNum++);
        bb.put(CMD_GET_INFO_TYPE_VERSION);
        return bb.array();
    }

    /**
     * 获取升级信息的命令
     * @param version 当前固件版本
     * @param hashData 当前固件文件的HASH
     * @return 命令数据
     */
    public byte[] cmdGetInfoUpdate(int version, byte[] hashData) {
        ByteBuffer bb = ByteBuffer.allocate(9)
                .order(ByteOrder.LITTLE_ENDIAN);
        bb.put(CMD_GET_INFO);
        bb.put(seqNum++);
        bb.put(CMD_GET_INFO_TYPE_UPDATE);
        bb.putShort((short)version);

        if (hashData != null) {
            bb.put(hashData, 0, 4);
        } else {
            bb.putInt(0xFFFFFFFF);
        }

        return bb.array();
    }

    /* OTA Data */

    /**
     * 返回当前块头一包数据的命令
     * @return 命令数据
     */
    public byte[] cmdStartSend() {
        ByteBuffer bb = ByteBuffer.allocate(OtaConstants.MAX_MTU_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        bb.put(CMD_OTA_INFO);
        bb.put(seqNum++);
        // 这里必须严格按照getStartAddress、getTotalLengthToBeSent和
        // getStartData的顺序使用，因为调用后会有一些值会被改变
        // start address
        bb.putInt(dataProvider.getStartAddress());
        // data length
        bb.putInt(dataProvider.getTotalLengthToBeSent());
        // copy data
        bb.put(dataProvider.getStartData(bb.position()));

        // 获取命令数据
        byte[] cmd = new byte[bb.position()];
        bb.rewind();
        bb.get(cmd);
        return cmd;
    }

    /**
     * 发送当前块接下来的数据的命令
     * @return 命令数据
     */
    public byte[] cmdSendData() {
        ByteBuffer bb = ByteBuffer.allocate(OtaConstants.MAX_MTU_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        bb.put(CMD_SEND_DATA);
        bb.put(seqNum++);
        // copy data
        bb.put(dataProvider.getDataToBeSent(bb.position()));

        // 获取命令数据
        byte[] cmd = new byte[bb.position()];
        bb.rewind();
        bb.get(cmd);
        return cmd;
    }

}
