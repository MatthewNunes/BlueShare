package com.example.c1009692.shareblue;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created by Matthew on 16/07/15.
 */
public class MyBluetoothService extends IntentService {

    public static final String SERVICE_NAME = "BluetoothService";

    public MyBluetoothService() {
        super(SERVICE_NAME);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }
}
