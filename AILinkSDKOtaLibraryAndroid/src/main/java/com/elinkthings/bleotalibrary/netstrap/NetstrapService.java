package com.elinkthings.bleotalibrary.netstrap;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


public class NetstrapService extends Thread {
    private String Tag = NetstrapService.this.getName();

    private final static int OTA_MAX_RX_OCTETS = 5;

    private OPLOtaManager bleService;

    private OtaService otaService;

    private boolean isOtaStarted;

    private BlockingQueue<NetstrapTask> taskQueue = new LinkedBlockingDeque<>();

    private int otaIndex = 0;

    private boolean isOtaPaused;



    public void setBleService(OPLOtaManager bleService) {
        this.bleService = bleService;
    }

    public OPLOtaManager getBleService() {
        return this.bleService;
    }

    public void setOtaService(OtaService otaService) {
        this.otaService = otaService;
    }

    public boolean isOtaStarted() {
        return isOtaStarted;
    }


    public void addTask(NetstrapTask task) {
        try {
            taskQueue.put(task);
        } catch (InterruptedException e) {
            Log.e(NetstrapService.class.getName(), e.getMessage(), e);
        }
    }


    private void otaStart() {
        OtaImage otaImage = otaService.getOtaImage();

        if (otaImage != null) {
            byte[] otaHeader = otaImage.getHeader();
            NetstrapPacket pkt = NetstrapPacket.createOtaUpgradeReqPacket(OTA_MAX_RX_OCTETS, otaHeader);
            bleService.send(pkt.getBytes());
        } else {

        }
    }

    int cnt = 0;

    public void otaSend() {
        OtaImage otaImage = otaService.getOtaImage();
        byte[] rawData = otaImage.getRawData();

        if (rawData != null) {
            cnt = cnt + rawData.length;
            NetstrapPacket pkt = NetstrapPacket.createOtaRawDataReqPacket(rawData);
            bleService.send(pkt.getBytes());
            bleService.onOtaProgress((int) (1.0f*cnt/otaImage.getSize()*100f));
            isOtaStarted = true;
            otaIndex++;
            if (otaIndex == OTA_MAX_RX_OCTETS) {
                isOtaPaused = true;
            }
        } else {
            isOtaStarted = false;
            otaService.resetOtaImage();
            otaEnd();
        }
    }

    private void otaEnd() {
        ;
        NetstrapPacket pkt = NetstrapPacket.createOtaEndReqPacket(0);
        bleService.send(pkt.getBytes());
    }

    private void processRxPacket(final NetstrapPacket packet) {
        switch (packet.getCmdId()) {

            case NetstrapPacket.PDU_TYPE_EVT_OTA_VERSION_RSP:

                break;

            case NetstrapPacket.PDU_TYPE_EVT_OTA_UPGRADE_RSP:
                if (packet.getStatus() == 0x00) {
                    //发送开始包
                    isOtaStarted = true;
                    isOtaPaused = false;
                    otaIndex = 0;
                    otaSend();
                }
                break;

            case NetstrapPacket.PDU_TYPE_EVT_OTA_RAW_DATA_RSP:
                otaIndex = 0;
                isOtaPaused = false;
                otaSend();
                break;

            case NetstrapPacket.PDU_TYPE_EVT_OTA_END_RSP:
                bleService.ReqConnBle2BalPriority();
//                bleService.disconnect();
                if (packet.getReason() != 0x00) {
                    isOtaStarted = false;
                    bleService.onFail();
                } else {
                    bleService.onSuccess();
                }
                break;
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                NetstrapTask task = taskQueue.take();
                switch (task.getState()) {
                    case OTA_START:
                        otaStart();
                        break;

                    case OTA_SEND:
                        if (!isOtaPaused) {
                            otaSend();
                        }
                        break;

                    case OTA_END:

                        break;

                    case TO_PROCESS_RX_PACKET:
                        NetstrapPacket packet = (NetstrapPacket) task.getData("netstrapPacket");
                        processRxPacket(packet);
                        break;


                }
            }
        } catch (InterruptedException e) {
            Log.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    public void end() {
        addTask(new NetstrapTask(NetstrapState.TO_TERMINATE));
    }

}
