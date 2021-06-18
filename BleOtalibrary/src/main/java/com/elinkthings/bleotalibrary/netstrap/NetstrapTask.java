package com.elinkthings.bleotalibrary.netstrap;



import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class NetstrapTask {

    private NetstrapState state;

    private Map<String, Object> data = new HashMap<>();

    public NetstrapTask(NetstrapState state) {
        this.state = state;
    }

    public NetstrapState getState() {
        return state;
    }

    public void setData(String name, Object value) {
        data.put(name, value);
    }

    @Nullable
    public Object getData(String name) {
        return data.get(name);
    }

}
