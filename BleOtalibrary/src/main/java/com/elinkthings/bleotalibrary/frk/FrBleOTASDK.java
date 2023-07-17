//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.elinkthings.bleotalibrary.frk;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;


import com.pingwang.bluetoothlib.utils.BleLog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

public class FrBleOTASDK {
    private static final String TAG = "OtaUpate";

    private final int TIME_OUT=1;
    private final int SEND_DATA_TIME_OUT=2;
    /**
     * 发送数据时间
     */
    private final int SEND_DATA_TIME=1000;
    private final int RESEND_NUMBER_MAX=100;

    private static final String UUID_SERVICE_DATA_H = "02f00000-0000-0000-0000-00000000fe00";
    private static final String UUID_SEND_DATA_H = "02f00000-0000-0000-0000-00000000ff01";
    private static final String UUID_RECV_DATA_H = "02f00000-0000-0000-0000-00000000ff02";
    private static final String UUID_DES = "00002902-0000-1000-8000-00805f9b34fb";
    private final BluetoothDevice mDevice;
    private Context mContext;
    private BluetoothGatt mBluetoothGatt;
    private final FRBleCallBack mCallback;
    private BluetoothGattDescriptor mDescriptor;
    private BluetoothGattCharacteristic mGattCharacteristic;
    private int connectState = 0;
    private byte[] recvValue = null;
    private int mRecvData;
    private int mtuSize = 235;
    private int mDelayNum;
    private boolean writeStatus = false;
    private int writePrecent;
    private int mResendNumber;


    private final static int OTA_CMD_NVDS_TYPE = 0;
    private final static int OTA_CMD_GET_STR_BASE = 1;
    private final static int OTA_CMD_PAGE_ERASE = 3;
    private final static int OTA_CMD_CHIP_ERASE = 4;
    private final static int OTA_CMD_WRITE_DATA = 5;
    private final static int OTA_CMD_READ_DATA = 6;
    private final static int OTA_CMD_WRITE_MEM = 7;
    private final static int OTA_CMD_READ_MEM  = 8;
    private final static int OTA_CMD_REBOOT = 9;
    private final static int OTA_CMD_NULL = 10;
    byte [] context = new  byte[256];

    private Handler mHandler=new Handler(Looper.myLooper()){
        @Override
        public void handleMessage(Message msg) {
            if (msg.what==TIME_OUT) {
                mHandler.removeMessages(TIME_OUT);
                FrBleOTASDK.this.mCallback.onBleOtaReady();
            }else if (msg.what==SEND_DATA_TIME_OUT){
                //发送数据超时
                mResendNumber++;
                if (mResendNumber>RESEND_NUMBER_MAX){
                    otaFailure("Handler:重发已达最大值:"+RESEND_NUMBER_MAX);
                    return;
                }
                mHandler.removeMessages(SEND_DATA_TIME_OUT);
                BleLog.iw("超时重发数据:"+mResendNumber);
                sendCmd(mOldSendData);
                mHandler.sendEmptyMessageDelayed(SEND_DATA_TIME_OUT,SEND_DATA_TIME);
            }
        }
    };

    BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @SuppressLint({"MissingPermission"})
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mResendNumber=0;
                FrBleOTASDK.this.mBluetoothGatt=gatt;
                UUID UUID_SERVICE_H = UUID.fromString(UUID_SERVICE_DATA_H);
                UUID UUID_SEND_H = UUID.fromString(UUID_SEND_DATA_H);
                UUID UUID_RECV_H = UUID.fromString(UUID_RECV_DATA_H);

                try {
                    BluetoothGattCharacteristic gattCharacteristic = gatt.getService(UUID_SERVICE_H).getCharacteristic(UUID_SEND_H);
                    String uuidString = gattCharacteristic.getUuid().toString();
                    if (uuidString.equals(UUID_SEND_DATA_H)) {
                        FrBleOTASDK.this.mGattCharacteristic = gattCharacteristic;
                    }

                    gattCharacteristic = gatt.getService(UUID_SERVICE_H).getCharacteristic(UUID_RECV_H);
                    uuidString = gattCharacteristic.getUuid().toString();
                    if (uuidString.equals(UUID_RECV_DATA_H)) {
                        FrBleOTASDK.this.mDescriptor = gattCharacteristic.getDescriptor(UUID.fromString(UUID_DES));
                        if (FrBleOTASDK.this.mDescriptor != null) {
                            FrBleOTASDK.this.mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);
                            FrBleOTASDK.this.mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            FrBleOTASDK.this.mBluetoothGatt.writeDescriptor(FrBleOTASDK.this.mDescriptor);

                        }
                    }
                } catch (Exception var8) {
                    FrBleOTASDK.this.mCallback.onBleOtaUUIDError();
                }
            } else {
                FrBleOTASDK.this.mCallback.onBleOtaUUIDError();
            }

        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String uuid = descriptor.getUuid().toString();
                if (uuid.equalsIgnoreCase(UUID_DES)) {
                    FrBleOTASDK.this.mBluetoothGatt.requestMtu(512);
                    mHandler.removeMessages(TIME_OUT);
                    mHandler.sendEmptyMessageDelayed(TIME_OUT,1000);
                }

            }

        }

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (mHandler.hasMessages(SEND_DATA_TIME_OUT)) {
                mHandler.removeMessages(SEND_DATA_TIME_OUT);
            }
            FrBleOTASDK.this.recvValue = characteristic.getValue();
            FrBleOTASDK.this.setRecvData(1);
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == 0) {
                FrBleOTASDK.this.writeStatus = true;
            }

        }

        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            mHandler.removeMessages(TIME_OUT);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                FrBleOTASDK.this.mtuSize = mtu;
                BleLog.i("BleServiceonMtuChanged success MTU = " + mtu);
            } else {
                FrBleOTASDK.this.mtuSize = 235;
                BleLog.i( "onMtuChanged fail ");
            }
            FrBleOTASDK.this.mCallback.onBleOtaReady();
        }

        @SuppressLint({"MissingPermission"})
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BleLog.i("status="+status+"  newState="+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                FrBleOTASDK.this.mBluetoothGatt.discoverServices();
                FrBleOTASDK.this.mCallback.onBleOtaConnect();
                FrBleOTASDK.this.connectState = 1;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mHandler.removeMessages(TIME_OUT);
                if (FrBleOTASDK.this.mBluetoothGatt != null) {
                    FrBleOTASDK.this.mBluetoothGatt.close();
                }
                FrBleOTASDK.this.connectState = 0;
                FrBleOTASDK.this.mCallback.onBleOtaDisConnect();
            }

        }
    };

    private final int crc_ta_8[]= new int[]{
            0x00000000, 0x77073096, 0xee0e612c, 0x990951ba,
            0x076dc419, 0x706af48f, 0xe963a535, 0x9e6495a3, 0x0edb8832,
            0x79dcb8a4, 0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd,
            0xe7b82d07, 0x90bf1d91, 0x1db71064, 0x6ab020f2, 0xf3b97148,
            0x84be41de, 0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7,
            0x136c9856, 0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f,
            0x63066cd9, 0xfa0f3d63, 0x8d080df5, 0x3b6e20c8, 0x4c69105e,
            0xd56041e4, 0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd,
            0xa50ab56b, 0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940,
            0x32d86ce3, 0x45df5c75, 0xdcd60dcf, 0xabd13d59, 0x26d930ac,
            0x51de003a, 0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423,
            0xcfba9599, 0xb8bda50f, 0x2802b89e, 0x5f058808, 0xc60cd9b2,
            0xb10be924, 0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d,
            0x76dc4190, 0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589,
            0x06b6b51f, 0x9fbfe4a5, 0xe8b8d433, 0x7807c9a2, 0x0f00f934,
            0x9609a88e, 0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97,
            0xe6635c01, 0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e,
            0x6c0695ed, 0x1b01a57b, 0x8208f4c1, 0xf50fc457, 0x65b0d9c6,
            0x12b7e950, 0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49,
            0x8cd37cf3, 0xfbd44c65, 0x4db26158, 0x3ab551ce, 0xa3bc0074,
            0xd4bb30e2, 0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb,
            0x4369e96a, 0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73,
            0x33031de5, 0xaa0a4c5f, 0xdd0d7cc9, 0x5005713c, 0x270241aa,
            0xbe0b1010, 0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409,
            0xce61e49f, 0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4,
            0x59b33d17, 0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad, 0xedb88320,
            0x9abfb3b6, 0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af,
            0x04db2615, 0x73dc1683, 0xe3630b12, 0x94643b84, 0x0d6d6a3e,
            0x7a6a5aa8, 0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1,
            0xf00f9344, 0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d,
            0x806567cb, 0x196c3671, 0x6e6b06e7, 0xfed41b76, 0x89d32be0,
            0x10da7a5a, 0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43,
            0x60b08ed5, 0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252,
            0xd1bb67f1, 0xa6bc5767, 0x3fb506dd, 0x48b2364b, 0xd80d2bda,
            0xaf0a1b4c, 0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55,
            0x316e8eef, 0x4669be79, 0xcb61b38c, 0xbc66831a, 0x256fd2a0,
            0x5268e236, 0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f,
            0xc5ba3bbe, 0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7,
            0xb5d0cf31, 0x2cd99e8b, 0x5bdeae1d, 0x9b64c2b0, 0xec63f226,
            0x756aa39c, 0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785,
            0x05005713, 0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38,
            0x92d28e9b, 0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21, 0x86d3d2d4,
            0xf1d4e242, 0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b,
            0x6fb077e1, 0x18b74777, 0x88085ae6, 0xff0f6a70, 0x66063bca,
            0x11010b5c, 0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45,
            0xa00ae278, 0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661,
            0xd06016f7, 0x4969474d, 0x3e6e77db, 0xaed16a4a, 0xd9d65adc,
            0x40df0b66, 0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f,
            0x30b5ffe9, 0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6,
            0xbad03605, 0xcdd70693, 0x54de5729, 0x23d967bf, 0xb3667a2e,
            0xc4614ab8, 0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1,
            0x5a05df1b, 0x2d02ef8d,
    };

    @SuppressLint({"MissingPermission"})
    public FrBleOTASDK(Context context, BluetoothDevice device, FRBleCallBack callBack) {
        this.mContext = context;
        this.mCallback = callBack;
        this.mDevice = device;
        this.mBluetoothGatt = device.connectGatt(this.mContext, false, this.mBluetoothGattCallback);
    }

    @SuppressLint({"MissingPermission"})
    public void FrBleOTAUpate(String filePath) throws FileNotFoundException {
        if (filePath != null) {
            int lastReadCount = 0;
            int send_data_count = 0;
            int fileCRC = 0;
            File file = new File(filePath);
            FileInputStream isfile = new FileInputStream(file);
            long leng = file.length();
            BufferedInputStream input = new BufferedInputStream(isfile);

            try {
                fileCRC = this.getCRC32new(filePath);
                BleLog.i("getCRC32new="+fileCRC);
            } catch (Exception var17) {
                var17.printStackTrace();
                otaFailure("CRC32 err:"+var17.toString());
                return;
            }
            this.setRecvData(0);
            int packageSize = this.mtuSize - 3 - 9;
            byte[] inputBuffer = new byte[packageSize];
            this.sendData(1, 0, (byte[]) null, 0);
            while (this.getRecvData() != 1) {
                if (this.checkDisconnect()) {
                    BleLog.e("连接断开");
                    otaFailure("disconnect");
                    return;
                }
                SystemClock.sleep(10L);
            }

            int addr = this.bytetoint(this.recvValue);
            this.setRecvData(0);
            this.page_erase(addr, leng);

            try {
                while (true) {
                    int read_count;
                    if ((read_count = input.read(inputBuffer, 0, packageSize)) == -1) {
                        while (this.bytetoint(this.recvValue) != addr - lastReadCount) {
                            if (this.checkDisconnect()) {
                                BleLog.e("连接断开");
                                otaFailure("disconnect");
                                return;
                            }
                        }
                        this.send_data_long(OTA_CMD_REBOOT, fileCRC, (byte[]) null, leng);
                        this.mCallback.onBleOtaStateChange(2, 0);
                        break;
                    }
                    sendData(OTA_CMD_WRITE_DATA, addr, inputBuffer, read_count);
                    this.mDelayNum = 0;

                    while (!this.writeStatus) {
                        ++this.mDelayNum;
                        if (this.mDelayNum % 8000 == 0) {
                            this.sendData(OTA_CMD_WRITE_DATA, addr, inputBuffer, read_count);
                        }
                    }
                    this.writeStatus = false;
                    addr += read_count;
                    lastReadCount = read_count;
                    send_data_count += read_count;
                    if (this.writePrecent != (int) ((float) send_data_count / (float) leng * 100.0F)) {
                        this.writePrecent = (int) ((float) send_data_count / (float) leng * 100.0F);
                        this.mCallback.onBleOtaStateChange(1, this.writePrecent);
                    }
                    while (this.getRecvData() != 1) {
                        if (this.checkDisconnect()) {
                            BleLog.e("连接断开");
                            otaFailure("disconnect");
                            return;
                        }
                        SystemClock.sleep(10L);
                    }
                    this.setRecvData(0);
                }
            } catch (IOException var18) {
                var18.printStackTrace();
                BleLog.e("OTA异常:"+var18.toString());
            }
        }else {
            otaFailure("file==null");
        }

    }

    
    
    private void otaFailure(String msg){
        mHandler.removeMessages(SEND_DATA_TIME_OUT);
        if (FrBleOTASDK.this.mCallback!=null) {
            FrBleOTASDK.this.mCallback.onBleOtaFailure(msg);
        }
    }

    private void setRecvData(int recv_data) {
        this.mRecvData = recv_data;
    }

    private int getRecvData() {
        return this.mRecvData;
    }

    @SuppressLint({"MissingPermission"})
    private boolean checkDisconnect() {
        return this.connectState == 0;
    }

    private int page_erase(int addr, long length) {
        this.mCallback.onBleOtaStateChange(0, 0);
        long count = length / 4096L;
        if (length % 4096L != 0L) {
            ++count;
        }

        for (int i = 0; (long) i < count; ++i) {
            sendData(OTA_CMD_PAGE_ERASE, addr, (byte[]) null, 0);
            this.mDelayNum = 0;
            while (!this.writeStatus) {
                ++this.mDelayNum;
                if (this.mDelayNum % 8000 == 0) {
                    this.sendData(OTA_CMD_PAGE_ERASE, addr, (byte[]) null, 0);
                }
            }

            while (this.getRecvData() != 1) {
                SystemClock.sleep(10L);
            }

            this.setRecvData(0);
            addr += 0x1000;
        }

        this.mCallback.onBleOtaStateChange(0, 100);
        return 0;
    }

    private byte[] cmd_write_op(int opcode,int length,int addr,int datalenth){
        byte [] cmd;
        if(opcode == OTA_CMD_PAGE_ERASE){
            cmd = new byte[7];
        }else{
            cmd = new byte[9];
        }
        cmd[0] = (byte) (opcode&0xff);
        cmd[1] = (byte) (length&0xff);
        cmd[2] = (byte) ((length&0xff) >> 8);
        cmd[3] = (byte) (addr&0xff);
        cmd[4] = (byte) ((addr&0xff00) >> 8);
        cmd[5] = (byte) ((addr&0xff0000) >> 16);
        cmd[6] = (byte) ((addr&0xff000000) >> 24);
        if(opcode != OTA_CMD_PAGE_ERASE){
            cmd[7] = (byte) (datalenth&0xff);
            cmd[8] = (byte) ((datalenth&0xff00)>>8);
        }
        return cmd;
    }

    public byte[] cmd_operation(int type,int lenth,int addr){
        byte[] cmd = null;
        if((type == OTA_CMD_WRITE_MEM) || (type == OTA_CMD_WRITE_DATA)){
            cmd = cmd_write_op(type,9,addr,lenth);
        }else if((type  == OTA_CMD_GET_STR_BASE) || (type  == OTA_CMD_NVDS_TYPE)){
            cmd = cmd_write_op(type,3,0,0);
        }else if(type == OTA_CMD_PAGE_ERASE){
            cmd = cmd_write_op(type,7,addr,0);
        }
        return cmd;
    }

    private static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
        byte[] byte_3 = new byte[byte_1.length + byte_2.length];
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
        return byte_3;
    }

    @SuppressLint({"MissingPermission"})
    private boolean send_data_long(int type, int addr, byte[] buffer, long length) {
        byte[] cmd = new byte[11];
        byte[] result_cmd = null;
        if(type  == OTA_CMD_REBOOT) {
            cmd[0] = (byte) (type & 0xff);
            cmd[1] = 0xa;
            cmd[2] = 0x00;
            cmd[3] = (byte) (length & 0xff);
            cmd[4] = (byte) ((length & 0xff00) >> 8);
            cmd[5] = (byte) ((length & 0xff0000) >> 16);
            cmd[6] = (byte) ((length & 0xff000000) >> 24);
            cmd[7] = (byte) (addr & 0xff);
            cmd[8] = (byte) ((addr & 0xff00) >> 8);
            cmd[9] = (byte) ((addr & 0xff0000) >> 16);
            cmd[10] = (byte) ((addr & 0xff000000) >> 24);

            result_cmd = cmd;
        }

        this.mGattCharacteristic.setWriteType(1);
        this.mGattCharacteristic.setValue(result_cmd);
        return this.mBluetoothGatt.writeCharacteristic(this.mGattCharacteristic);
    }


    private boolean sendData(int type, int addr, byte[] buffer, int length){
        mHandler.removeMessages(SEND_DATA_TIME_OUT);
        while (!send_data(type, addr, buffer, length)){
            mResendNumber++;
            if (mResendNumber>RESEND_NUMBER_MAX){
                otaFailure("sendData:重发已达最大值:"+RESEND_NUMBER_MAX);
                break;
            }
            BleLog.i("OTA发送数据失败,重发:"+mResendNumber);
            SystemClock.sleep(50L);
        }
        mHandler.sendEmptyMessageDelayed(SEND_DATA_TIME_OUT,SEND_DATA_TIME);
        return true;
    }



    private volatile byte[] mOldSendData;

    private boolean send_data(int type, int addr, byte[] buffer, int length) {
        byte[] cmd_write = null;
        byte[] resultCmd = null;
        byte[] cmd = new byte[1];
        cmd_write = cmd_operation(type,length,addr);
        if((type  == OTA_CMD_GET_STR_BASE) || (type  == OTA_CMD_PAGE_ERASE) || (type  == OTA_CMD_NVDS_TYPE)){
            resultCmd = cmd_write;
        }else if(type  == OTA_CMD_REBOOT){
            cmd[0] = (byte) (type&0xff);
            resultCmd = cmd;
        }else{
            resultCmd = byteMerger(cmd_write,buffer);
        }
        mOldSendData=resultCmd;
        return sendCmd(resultCmd);
    }

    @SuppressLint("MissingPermission")
    private boolean sendCmd(byte[] value){
        this.mGattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        this.mGattCharacteristic.setValue(value);
        return this.mBluetoothGatt.writeCharacteristic(this.mGattCharacteristic);
    }

    public int bytetoint(byte[] data){
        int addr;
        addr = ((int)data[4] & 0x000000ff);
        addr |= (((int)data[5] & 0x0000ff) << 8);
        addr |= (((int)data[6] & 0x000000ff) << 16);
        addr |= (((int)data[7] & 0x000000ff) << 24);
        return addr;
    }

    public int bytetochar(byte[] data){
        int value;
        value = ((int)data[4] & 0x000000ff);
        return value;
    }

    private int Crc32CalByByte(int oldcrc, byte[] ptr, int offset, int len) {
        int crc = oldcrc;
        int i = offset;
        while (len-- != 0) {
            //取CRC高8位
            int high = crc / 256;
            crc <<= 8;
            crc ^= crc_ta_8[(high ^ ptr[i]) & 0xff];
            crc &= 0xFFFFFFFF;
            i++;
        }
        return crc & 0xFFFFFFFF;
    }

    public int getCRC32new(String fp) throws IOException {

        File file = new File(fp);// 成文件路径中获取文件
        FileInputStream isfile = null;
        try {
            isfile = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int read_count = 0;
        BufferedInputStream input = new BufferedInputStream(isfile);
        byte[] inputBuffer = new byte[256];
        int crcInit = 0;
        int couts = 0;
        while (((read_count = input.read(inputBuffer, 0, 256)) != -1)) {
            if (couts != 0) {
                crcInit = Crc32CalByByte(crcInit, inputBuffer, 0, read_count);
            }
            couts++;
        }
        return crcInit;
    }
}
