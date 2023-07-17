package com.elinkthings.bleotalibrary.zhlx;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 提供OTA数据的类。
 * 获取升级数据时，每一个块第一包必须严格按照getStartAddress、getTotalLengthToBeSent和
 * getStartData的顺序使用，因为调用后会有一些值会被改变。
 */
final class OtaDataProvider {

    private final byte[] otaData;

    private int blockSize = OtaConstants.UNDEFINED_BLOCK_SIZE; // 不需要分包确认
    private int blockOffset;
    private int packetSize = OtaConstants.DEFAULT_MTU_SIZE - 3;
    private int fileOffset = 0;

    /**
     * 使用OTA数据初始化类
     * @param otaData OTA数据
     */
    public OtaDataProvider(byte[] otaData) {
        this.otaData = otaData;
    }

    /**
     * 重置，重置后可用于下一次升级。
     */
    public void reset() {
        fileOffset = 0;
        packetSize = OtaConstants.DEFAULT_MTU_SIZE - 3;
        blockSize = OtaConstants.UNDEFINED_BLOCK_SIZE;
    }

    /**
     * 获取开始升级的地址
     * @return 升级地址
     */
    public int getStartAddress() {
        return fileOffset;
    }

    /**
     * 设置开始升级的地址
     * @param address 升级地址
     */
    public void setStartAddress(int address) {
        fileOffset = address;
    }

    /**
     * 设置块大小，固件接收一个块后会进行校验并做下一步处理，随后会回复状态到App。
     * @param size 块大小
     */
    public void setBlockSize(int size) {
        blockSize = size;
    }

    /**
     * 设置发送一包的最大大小（被MTU大小限制）。
     * @param packetSize 最大包大小
     */
    public void setPacketSize(int packetSize) {
        this.packetSize = packetSize;
    }

    /**
     * 获取升级进度
     * @return 升级进度
     */
    public int getProgress() {
        return fileOffset * 100 / otaData.length;
    }

    /**
     * 块是否已经完成发送
     * @return 阶段是否已完成发送
     */
    public boolean isBlockSendFinish() {
        if (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) {
            return isAllDataSent();
        } else {
            return (fileOffset - blockOffset == blockSize) || isAllDataSent();
        }
    }

    /**
     * 是否已经发送完所有数据，即文件指针已到达文件末端。
     * @return 是否已经发送完所有数据
     */
    public boolean isAllDataSent() {
        return fileOffset == otaData.length;
    }



    /* Command Data */

    // 每一个块第一包必须严格按照getStartAddress、getTotalLengthToBeSent和
    // getStartData的顺序使用，因为调用后会有一些值会被改变

    /**
     * 返回需要升级的这个块一共要发送的数据长度
     * @return 需要发送的数据长度
     */
    public int getTotalLengthToBeSent() {
        // data length
        int sendLen;
        // 如果不需要分块发送
        if (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) {
            sendLen = otaData.length - fileOffset;
        }
        // 如果需要分块发送
        else {
            // 剩下还没发送的长度
            int leftLen = otaData.length - fileOffset;
            sendLen = Math.min(leftLen, blockSize);
            blockOffset = fileOffset;
        }

        return sendLen;
    }

    /**
     * 返回需要升级的这个块头一包中固件数据
     * @param headerSize 已经使用的包头长度，数据长度=包长度-包头长度
     * @return 固件数据
     */
    byte[] getStartData(int headerSize) {
        // 限定发送数据的长度
        int dataLen = (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) ? otaData.length : blockSize;
        dataLen = Math.min(dataLen, packetSize - headerSize);
        // 判断整个文件有没有越界
        if (fileOffset + dataLen > otaData.length) {
            dataLen = otaData.length - fileOffset;
        }

        byte[] data = new byte[dataLen];
        System.arraycopy(otaData, fileOffset, data, 0, dataLen);
        fileOffset += dataLen;

        return data;
    }

    /**
     * 返回下一包中固件数据
     * @param headerSize 已经使用的包头长度，数据长度=包长度-包头长度
     * @return 固件数据
     */
    byte[] getDataToBeSent(int headerSize) {
        // 限定发送数据的长度
        int dataLen = (blockSize == OtaConstants.UNDEFINED_BLOCK_SIZE) ? otaData.length : blockSize;
        dataLen = Math.min(dataLen, packetSize - headerSize);
        // 如果需要分块发送
        if (blockSize != OtaConstants.UNDEFINED_BLOCK_SIZE) {
            // 判断是否超出blockSize
            if ((fileOffset - blockOffset) + dataLen > blockSize) {
                dataLen = blockSize - (fileOffset - blockOffset);
            }
        }
        // 判断整个文件有没有越界
        if (fileOffset + dataLen > otaData.length) {
            dataLen = otaData.length - fileOffset;
        }

        byte[] data = new byte[dataLen];
        System.arraycopy(otaData, fileOffset, data, 0, dataLen);
        fileOffset += dataLen;

        return data;
    }


    /**
     * 获取OTA文件的HASH值，供固件端用来对比文件。
     * @return 返回HASH值字节数组。
     */
    
    public byte[] getHash() {
        return getHash(otaData);
    }

    /* Private */

    
    private byte[] getHash(byte[] data) {
        try {
            // 使用MD5
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

}
