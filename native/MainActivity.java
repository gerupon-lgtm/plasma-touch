package com.sikumilab.plasmatouch;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(HapticLabPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
