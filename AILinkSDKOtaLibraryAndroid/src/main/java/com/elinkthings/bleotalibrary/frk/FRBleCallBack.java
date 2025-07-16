//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.elinkthings.bleotalibrary.frk;

public interface FRBleCallBack {
    void onBleOtaConnect();

    void onBleOtaDisConnect();

    void onBleOtaReady();
    void onBleOtaFailure(String msg);

    void onBleOtaUUIDError();

    void onBleOtaStateChange(int var1, int var2);
}
