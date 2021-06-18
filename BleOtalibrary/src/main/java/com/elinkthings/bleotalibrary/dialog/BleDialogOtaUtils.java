package com.elinkthings.bleotalibrary.dialog;

import android.content.Context;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * xing<br>
 * 2020/5/15<br>
 * OTA升级文件处理工具
 */
public class BleDialogOtaUtils {
    private static final int fileChunkSize = 20;
    private InputStream inputStream;
    private byte crc;
    private byte[] bytes;

    /**
     * 1,第几组(每组240byte);2,第几小组(240中的小组);3,内容(20byte)
     */
    private byte[][][] blocks;

    private int fileBlockSize = 0;
    /**
     * 升级包的大小(字节数)
     */
    private int bytesAvailable;
    private int numberOfBlocks = -1;
    private int chunksPerBlockCount;
    private int totalChunkCount;

    public static BleDialogOtaUtils getByFileName(String filePath) throws IOException {
        // Get the file and store it in fileStream
        InputStream is = new FileInputStream(filePath);
        return new BleDialogOtaUtils(is);
    }

    private BleDialogOtaUtils(InputStream inputStream) throws IOException {
        this.inputStream = inputStream;
        this.bytesAvailable = this.inputStream.available();
        init();
    }

    public void init() throws IOException {
        // Reserve 1 extra byte to the total array for the CRC code
        this.bytes = new byte[this.bytesAvailable + 1];
        this.inputStream.read(this.bytes);
        this.crc = getCrc();
        this.bytes[this.bytesAvailable] = this.crc;
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
        this.initBlocksSuota();
    }

    private void initBlocksSuota() {
        int totalChunkCounter = 0;
        blocks = new byte[this.numberOfBlocks][][];
        int byteOffset = 0;
        // Loop through all the bytes and split them into pieces the size of the default chunk size
        for (int i = 0; i < this.numberOfBlocks; i++) {
            int blockSize = this.fileBlockSize;
            if (i + 1 == this.numberOfBlocks) {
                blockSize = this.bytes.length % this.fileBlockSize;
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

    private byte getCrc() throws IOException {
        byte crc_code = 0;
        for (int i = 0; i < this.bytesAvailable; i++) {
            Byte byteValue = this.bytes[i];
            int intVal = byteValue.intValue();
            crc_code ^= intVal;
        }
        return crc_code;
    }


    public static InputStream get(Context c, int resourceID) {
        InputStream inputStream = c.getResources().openRawResource(resourceID);
        return inputStream;
    }


}
