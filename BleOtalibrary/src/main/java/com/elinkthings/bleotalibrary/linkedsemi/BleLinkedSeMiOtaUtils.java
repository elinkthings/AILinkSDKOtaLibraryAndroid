package com.elinkthings.bleotalibrary.linkedsemi;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * @author ljl
 * on 2023/12/8
 */
public class BleLinkedSeMiOtaUtils {

    /**
     * 数据包最大长度
     */
    private static final int fileChunkSize = 513;
    private InputStream inputStream;
    /**
     * 校验和
     */
    private byte crc;
    /**
     * 固件包数据字节数组
     */
    private byte[] bytes;

    /**
     * 1,第几组(每组4096byte);2,第几小组(4096中的小组);3,内容(byte)
     */
    private byte[][][] blocks;

    /**
     * 每个sector能存储的数据大小
     */
    private int fileBlockSize = 0;
    /**
     * 升级包的大小(字节数)
     */
    private int bytesAvailable;
    /**
     * 存储固件包所有数据需要的sector数量
     */
    private int numberOfBlocks = -1;
    /**
     * 一个sector需要发的数据的数量
     */
    private int chunksPerBlockCount;
    /**
     * 发完整个数据包的数据数量
     */
    private int totalChunkCount;

    /**
     * 摘要数据,32个字节,分两包
     */
    private byte[] summaryBytes = new byte[32];
    /**
     * 摘要数据第一包
     */
    private byte[] summaryBytes1 = new byte[18];
    /**
     * 摘要数据第二包
     */
    private byte[] summaryBytes2 = new byte[18];


    /**
     * 获取固件包输入流
     *
     * @param filePath 固件包文件路径
     */
    public static BleLinkedSeMiOtaUtils getByFileName(String filePath) throws IOException {
        // Get the file and store it in fileStream
        InputStream is = new FileInputStream(filePath);
        return new BleLinkedSeMiOtaUtils(is);
    }

    /**
     * 固件包输入流转换为byte数据
     *
     * @param inputStream 固件包文件输入流
     */
    private BleLinkedSeMiOtaUtils(InputStream inputStream) throws IOException {
        this.inputStream = inputStream;
        this.bytesAvailable = this.inputStream.available();
        init();
    }

    /**
     * 初始化，获取固件摘要信息、校验和
     */
    public void init() throws IOException {
        this.bytes = new byte[this.bytesAvailable];
        this.inputStream.read(this.bytes);
        this.crc = getCrc();
        summaryBytes = getSummaryBytes();
        splitSummaryBytes();
    }

    public int getFileBlockSize() {
        return this.fileBlockSize;
    }

    public int getNumberOfBytes() {
        return this.bytes.length;
    }

    public void setFileBlockSize(int fileBlockSize) {
        this.fileBlockSize = fileBlockSize;
        this.chunksPerBlockCount = (int) Math.ceil((double) fileBlockSize / (double) fileChunkSize);
        this.numberOfBlocks = (int) Math
                .ceil((double) this.bytes.length / (double) this.fileBlockSize);
        this.initBlocksOta();
    }

    private void initBlocksOta() {
        int totalChunkCounter = 0;
        blocks = new byte[this.numberOfBlocks][][];
        int byteOffset = 0;
        // Loop through all the bytes and split them into pieces the size of the default chunk size
        for (int i = 0; i < this.numberOfBlocks; i++) {
            int blockSize = this.fileBlockSize;
            if (i + 1 == this.numberOfBlocks) {
                blockSize = this.bytes.length % this.fileBlockSize == 0 ? this.fileBlockSize : this.bytes.length % this.fileBlockSize;
            }
            int numberOfChunksInBlock = (int) Math.ceil((double) blockSize / fileChunkSize);
            int chunkNumber = 0;
            blocks[i] = new byte[numberOfChunksInBlock][];
            for (int j = 0; j < blockSize; j += fileChunkSize) {
                // Default chunk size
                int chunkSize = fileChunkSize;
                // Last chunk of all
                if (byteOffset + fileChunkSize > this.bytes.length) {
                    chunkSize = this.bytes.length - byteOffset;
                }
                // Last chunk in block
                else if (j + fileChunkSize > blockSize) {
                    chunkSize = this.fileBlockSize % fileChunkSize;
                }

                //L.d("chunk", "total bytes: " + this.bytes.length + ", offset: " + byteOffset +
                // ", block: " + i + ", chunk: " + (chunkNumber + 1) + ", blocksize: " +
                // blockSize + ", chunksize: " + chunkSize);
                byte[] chunk = Arrays.copyOfRange(this.bytes, byteOffset, byteOffset + chunkSize);
                blocks[i][chunkNumber] = chunk;
                byteOffset += chunkSize;
                chunkNumber++;
                totalChunkCounter++;
            }
        }
        // Keep track of the total chunks amount, this is used in the UI
        this.totalChunkCount = totalChunkCounter;
    }

    public byte[][] getBlock(int index) {
        return blocks[index];
    }

    public void close() {
        if (this.inputStream != null) {
            try {
                this.inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int getNumberOfBlocks() {
        return this.numberOfBlocks;
    }

    public int getChunksPerBlockCount() {
        return chunksPerBlockCount;
    }

    public int getTotalChunkCount() {
        return this.totalChunkCount;
    }

    /**
     * 获取固件的消息摘要数据
     *
     * @return
     */
    private byte[] getSummaryBytes() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取数据摘要信息第一组
     *
     * @return
     */
    public byte[] getDigestBytes1() {
        return this.summaryBytes1;
    }

    /**
     * 获取数据摘要信息第二组
     *
     * @return
     */
    public byte[] getDigestBytes2() {
        return this.summaryBytes2;
    }

    /**
     * 摘要数据分成两包
     */
    private void splitSummaryBytes() {
        if (summaryBytes != null && summaryBytes.length >= 32) {
            System.arraycopy(summaryBytes, 0, summaryBytes1, 2, 16);
            System.arraycopy(summaryBytes, 16, summaryBytes2, 2, 16);
        }
        summaryBytes1[0] = 0x01;
        summaryBytes1[1] = 0x00;
        summaryBytes2[0] = 0x01;
        summaryBytes2[1] = 0x01;
    }

    /**
     * 固件包校验和
     *
     * @return
     */
    private byte getCrc() throws IOException {
        byte crc_code = 0;
        for (int i = 0; i < this.bytesAvailable; i++) {
            Byte byteValue = this.bytes[i];
            int intVal = byteValue.intValue();
            crc_code ^= intVal;
        }
        return crc_code;
    }

}
