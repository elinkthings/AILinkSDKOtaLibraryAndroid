package com.elinkthings.bleotalibrary.netstrap;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

public class OtaService {

    private final int BOOT_AGENT_LENGTH = 1024 * 12;

    private final int IMAGE_HEADER_LENGTH = 24;

    private final int IMAGE_HEADER_RESERVED_LENGTH = 1024 * 4 * 2 - IMAGE_HEADER_LENGTH;

    private Uri otaImagePath;

    private OtaImage otaImage = null;

    private Context mContext;

    public OtaService(Context context) {
        mContext = context;
    }

    public void setOtaImagePath(Uri otaImagePath) {
        this.otaImagePath = otaImagePath;
    }

    public OtaImage getOtaImage() {
        if (otaImage == null) {
            try {
                //Why API 26 occurs permission denied?????
                ParcelFileDescriptor parcelFileDescriptor =mContext.getContentResolver().openFileDescriptor(otaImagePath, "r");

                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileDescriptor));

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int len = 0;
                int b;
                byte[] header = new byte[IMAGE_HEADER_LENGTH];
                byte[] buf = new byte[1024];
                long projectId;
                long chipId;
                long fwId;
                long imageSize;
                long checksum;

                // skip boot agent
                try {
                    in.skip(BOOT_AGENT_LENGTH);
                } catch (IOException e) {
                    throw new Exception("OTA Image broken, boot agent size is not correct.");
                }

                // fetch header
                try {
                    b = in.read(header, 0, IMAGE_HEADER_LENGTH);

                    projectId = (header[4] & 0xFF) | ((header[5] & 0xFF) << 8);
                    chipId = (header[6] & 0xFF) | ((header[7] & 0xFF) << 8);
                    fwId = (header[8] & 0xFF) | ((header[9] & 0xFF) << 8);
                    imageSize = (header[12] & 0xFF) | ((header[13] & 0xFF) << 8) | ((header[14] & 0xFF) << 16) | ((header[15] & 0xFF) << 24);
                    checksum = (header[16] & 0xFF) | ((header[17] & 0xFF) << 8) | ((header[18] & 0xFF) << 16) | ((header[19] & 0xFF) << 24);

                } catch (IOException e) {
                    throw new Exception("OTA Image broken, image header size is not correct.");
                }

                // skip header reserved parts
                try {
                    in.skip(IMAGE_HEADER_RESERVED_LENGTH);
                } catch (IOException e) {
                    throw new Exception("OTA Image broken, image header reserved  size is not correct.");
                }

                // fetch image content
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                in.close();
                byte[] allImage = out.toByteArray();
                if (imageSize != allImage.length) {
                    throw new Exception("OTA Image broken, imageSize: " + imageSize + ", allSize: " + allImage.length + ", projectId: " + projectId + ", chipId: " + chipId + ", fwId: " + fwId + ", checksum: " + checksum);
                }

                otaImage = new OtaImage(projectId, chipId, fwId, checksum, header, allImage);

            } catch (Exception e) {
                e.printStackTrace();

                return null;
            }
        }

        return otaImage;
    }

    public void resetOtaImage() {
        otaImage = null;
    }

}
