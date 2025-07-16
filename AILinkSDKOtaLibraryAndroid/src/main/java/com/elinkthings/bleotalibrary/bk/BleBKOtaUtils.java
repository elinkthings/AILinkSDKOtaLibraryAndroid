package com.elinkthings.bleotalibrary.bk;

import android.content.Context;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * xing<br>
 * 2020/5/15<br>
 * OTA升级文件处理工具
 */
public class BleBKOtaUtils {
    /**
     * 每包数据的大小
     */
    private static final int fileChunkSize = 18;
    private InputStream inputStream;
    private static final int FILE_BUFFER_SIZE = 0x40000;
    private byte[] mFileBytes;
    private long mDeviceVersion;
    private long mRomVersion;
    private long mBinLen;
    private long nBlocks;

    private Map<Integer, byte[]> mMapBytes = new HashMap<>();

    /**
     * 升级包的大小(字节数)
     */
    private int bytesAvailable;
    private int chunksPerBlockCount;

    public static BleBKOtaUtils getByFileName(String filePath) throws IOException {
        // Get the file and store it in fileStream
        InputStream is = new FileInputStream(filePath);
        return new BleBKOtaUtils(is);
    }


    private BleBKOtaUtils(InputStream inputStream) throws IOException {
        this.inputStream = inputStream;
        this.bytesAvailable = this.inputStream.available();
        init();
    }

    public void init() throws IOException {
        // Reserve 1 extra byte to the total array for the CRC code
        this.mFileBytes = new byte[FILE_BUFFER_SIZE];
        this.inputStream.read(this.mFileBytes);

        mDeviceVersion = buildUint16(mFileBytes[5], mFileBytes[4]);
        mBinLen = buildUint16(mFileBytes[7], mFileBytes[6]);
        nBlocks = (short) (mBinLen / ((fileChunkSize - 2) / 4));
        mRomVersion = buildUint16(mFileBytes[15], mFileBytes[14]);
//        initBlocksSuota();
    }


    public int getNumberOfBytes() {
        return this.mFileBytes.length;
    }


    private void initBlocksSuota() {
        //每包数据的结构：2个字节的序号 + 16个字节的有效数据
        int dataSize = fileChunkSize - 2;
        this.chunksPerBlockCount = (int) Math.ceil((double) getNumberOfBytes() / (double) dataSize);
        for (int i = 0; i < this.mBinLen; i++) {
            byte[] bytes = new byte[fileChunkSize];
            bytes[0] = (byte) (i & 0xFF);
            bytes[1] = (byte) (i >> 8);
            int index = dataSize * (i);
            System.arraycopy(mFileBytes, index, bytes, 2, dataSize);
            mMapBytes.put(i, bytes);
        }
    }

    public byte[] getHeadBlock() {
        int dataSize = fileChunkSize - 2;
        byte[] bytes = new byte[dataSize];
        System.arraycopy(mFileBytes, 0, bytes, 0, bytes.length);
        return bytes;
    }

    public byte[] getBlock(int index) {
        int dataSize = fileChunkSize - 2;
        byte[] bytes = new byte[fileChunkSize];
        bytes[0] = (byte) (index & 0xFF);
        bytes[1] = (byte) (index >> 8);
        int startIndex = dataSize * (index);
        System.arraycopy(mFileBytes, startIndex, bytes, 2, dataSize);
        return bytes;
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

    public long getDeviceVersion() {
        return mDeviceVersion;
    }

    public long getRomVersion() {
        return mRomVersion;
    }

    public long getBinLen() {
        return mBinLen;
    }


    public long getBlocks() {
        return nBlocks;
    }

    public static InputStream get(Context c, int resourceID) {
        InputStream inputStream = c.getResources().openRawResource(resourceID);
        return inputStream;
    }


    public static long buildUint16(byte hi, byte lo) {
        if ((hi & 0x80) == 0) {
            return ((((long) hi) << 8) | (((long) lo) & 0xff));
        } else {
            long temphi = (long) hi & 0x7f;
            long returnvalue = (temphi << 8) | (lo & 0xff) | 0x8000;
            return returnvalue;
        }
    }

}
