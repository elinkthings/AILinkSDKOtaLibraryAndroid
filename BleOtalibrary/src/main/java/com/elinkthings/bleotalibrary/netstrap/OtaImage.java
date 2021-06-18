package com.elinkthings.bleotalibrary.netstrap;

import java.nio.ByteBuffer;

public class OtaImage {

    public final static int OTA_IMAGE_BLOCK_LENGTH = 240;

    private long projectId;

    private long chipId;

    private long firmwareId;

    private long checksum;

    private byte[] header;

    private byte[] content;

    private int cursor;

    public OtaImage(long projectId, long chipId, long firmwareId, long checksum, byte[] header, byte[] content) {
        this.projectId = projectId;
        this.chipId = chipId;
        this.firmwareId = firmwareId;
        this.checksum = checksum;
        this.header = header;
        this.content = content;
    }

    public long getProjectId() {
        return projectId;
    }

    public long getChipId() {
        return chipId;
    }

    public long getFirmwareId() {
        return firmwareId;
    }

    public long getChecksum() {
        return checksum;
    }

    public int getSize() {
        return content.length;
    }

    public byte[] getHeader() {
        return header;
    }

    public byte[] getRawData() {
        if (cursor < content.length) {
            int len = OTA_IMAGE_BLOCK_LENGTH;
            if (cursor + len >= content.length) {
                len = content.length - cursor;
            }
            byte[] buf = ByteBuffer.allocate(len).put(content, cursor, len).array();
            cursor += len;
            return buf;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "OtaImage{" +
                "projectId=" + String.format("0x%04X", projectId) +
                ", chipId=" + String.format("0x%04X", chipId) +
                ", firmwareId=" + String.format("0x%04X", firmwareId) +
                ", checksum=" + String.format("0x%08X", checksum) +
                ", cursor=" + cursor +
                '}';
    }
}
