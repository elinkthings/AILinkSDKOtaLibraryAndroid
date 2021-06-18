package com.elinkthings.bleotalibrary.netstrap;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.elinkthings.bleotalibrary.listener.OnBleOTAListener;
import com.pingwang.bluetoothlib.device.BleDevice;
import com.pingwang.bluetoothlib.listener.OnBleMtuListener;
import com.pingwang.bluetoothlib.listener.OnCharacteristicListener;
import com.pingwang.bluetoothlib.utils.BleStrUtils;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

public class OPLOtaManager implements OnCharacteristicListener {
    private String Tag = OPLOtaManager.this.getClass().getName();
    private BluetoothGatt mBluetoothGatt;
    private byte[] characteristicData;
    private int characteristicCursor;
    private BluetoothGattCharacteristic characteristicTx;
    private BluetoothGattCharacteristic characteristicRx;
    private NetstrapService netstrapService;
    public final static int BLE_MTU_EXTENTED = 243;
    private int mMtu = 23;
    private OnBleOTAListener mOnBleOTAListener;
    private final static UUID SERVICE_UUID = UUID.fromString("0000AAAA-0000-1000-8000-00805F9B34FB");

    private final static UUID CHARACTERISTIC_TX_UUID = UUID.fromString("0000BBB0-0000-1000-8000-00805F9B34FB");

    private final static UUID CHARACTERISTIC_RX_UUID = UUID.fromString("0000BBB1-0000-1000-8000-00805F9B34FB");
    private Handler mHandler = new Handler(Looper.getMainLooper());
    /**
     * 是否支持OTA
     */
    private boolean mVerifySupport = false;
    private BleDevice mBleDevice;

    private OPLOtaManager(Builder builder) {
        Context context = builder.mContext;
        OtaService otaService = new OtaService(context);
        otaService.setOtaImagePath(builder.mFilePath);
        netstrapService = new NetstrapService();
        netstrapService.setOtaService(otaService);
        netstrapService.setBleService(this);
        this.mOnBleOTAListener = builder.mOnBleOTAListener;
        mBleDevice = builder.mBleDevice;
        if (mBleDevice != null) {
            this.mBluetoothGatt = mBleDevice.getBluetoothGatt();
            BluetoothGattService gatt = mBluetoothGatt.getService(SERVICE_UUID);
            if (gatt != null) {
                characteristicTx = gatt.getCharacteristic(CHARACTERISTIC_TX_UUID);
                characteristicRx = gatt.getCharacteristic(CHARACTERISTIC_RX_UUID);
                mBleDevice.setNotify(SERVICE_UUID, CHARACTERISTIC_RX_UUID);
                mVerifySupport = true;
            } else {
                Log.e(Tag, "不支持Dialog OTA");
                mVerifySupport = false;
            }
        }

        if (mVerifySupport && mBleDevice != null) {
            mBleDevice.setOnBleMtuListener(new OnBleMtuListener() {
                @Override
                public void OnMtu(int mtu) {
                    mMtu = mtu;
                }
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBleDevice.setConnectPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
                mBleDevice.setMtu(BLE_MTU_EXTENTED);
            }
        }
    }

    /**
     * 开始OTA
     */
    public void startOta() {
        if (!mVerifySupport || mBleDevice == null) {
            return;
        }
        mBleDevice.setOnCharacteristicListener(this);
        if (netstrapService != null) {
            netstrapService.start();
        }

    }

    public static Builder newBuilder(Context context) {
        return new Builder(context);
    }

    public final static class Builder {
        private Context mContext;
        private BleDevice mBleDevice;
        private OnBleOTAListener mOnBleOTAListener;
        private Uri mFilePath;

        public Builder(Context context) {
            mContext = context;
        }


        public Builder setOnBleOTAListener(OnBleOTAListener onBleOTAListener) {
            mOnBleOTAListener = onBleOTAListener;
            return this;
        }

        public Builder setFilePath(Uri filePath) {
            mFilePath = filePath;
            return this;
        }


        public OPLOtaManager build(BleDevice bleDevice) {
            mBleDevice = bleDevice;
            return new OPLOtaManager(this);
        }
    }


    @Override
    public void onCharacteristicReadOK(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onCharacteristicWriteOK(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(CHARACTERISTIC_TX_UUID))
            writeBleCharacteristicIfRemained();
    }

    @Override
    public void onDescriptorWriteOK(BluetoothGattDescriptor descriptor) {
        UUID uuid = descriptor.getUuid();
        if (uuid.equals(CHARACTERISTIC_RX_UUID)) {
            netstrapService.addTask(new NetstrapTask(NetstrapState.OTA_START));
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(CHARACTERISTIC_RX_UUID)) {
            for (NetstrapPacket packet : NetstrapPacket.decodePacket(characteristic.getValue())) {
                NetstrapTask task = new NetstrapTask(NetstrapState.TO_PROCESS_RX_PACKET);
                task.setData("netstrapPacket", packet);
                netstrapService.addTask(task);
            }
        }
    }


    void ReqConnBle2BalPriority() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
        }
    }

    void ReqConnBle2HighPriority() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            mBluetoothGatt.requestMtu(BLE_MTU_EXTENTED);
        }
    }

    void send(byte[] data) {
        characteristicData = data;
        characteristicCursor = 0;
        mHandler.postDelayed(() -> {
            writeBleCharacteristicIfRemained();
        }, 150);
    }

    private void writeBleCharacteristicIfRemained() {
        if (characteristicTx == null) {
            return;
        }

        synchronized (OPLOtaManager.class) {
            if (characteristicData != null) {
                try {
                    int availableTransmitLength = this.mMtu - 3;
                    int lastCursor = characteristicCursor;
                    int fragLength =
                            (characteristicCursor + availableTransmitLength <= characteristicData.length)
                                    ? availableTransmitLength : (characteristicData.length - characteristicCursor);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();

                    out.write(characteristicData, lastCursor, fragLength);
                    Log.e(Tag, BleStrUtils.byte2HexStr(out.toByteArray()));
                    characteristicTx.setValue(out.toByteArray());
                    characteristicTx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    boolean b = mBluetoothGatt.writeCharacteristic(characteristicTx);
                    characteristicCursor += fragLength;
                    if (characteristicCursor == characteristicData.length) {
                        characteristicData = null;
                        characteristicCursor = 0;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                sendCompletedEvent();
            }
        }
    }

    /**
     * 是否支持OTA
     */
    public boolean isVerifySupport() {
        return mVerifySupport;
    }


    private void sendCompletedEvent() {
        if (netstrapService.isOtaStarted()) {
            netstrapService.addTask(new NetstrapTask(NetstrapState.OTA_SEND));
        }
    }

    void onFail() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mOnBleOTAListener.onOtaFailure(0, "");
            }
        });
    }

    void onSuccess() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mOnBleOTAListener.onOtaSuccess();
            }
        });
    }

    void onOtaProgress(int progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mOnBleOTAListener.onOtaProgress(progress, 0, 0);

            }
        });
    }
}
