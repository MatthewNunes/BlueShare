package com.example.c1009692.shareblue;

import android.content.Context;
import android.widget.ArrayAdapter;

/**
 * Created by Matthew on 02/07/15.
 */
public class DeviceAdapter extends ArrayAdapter {

    Context context;
    String[] values;

    public DeviceAdapter(Context context, String[] values) {
        super(context, -1, values);
        this.context = context;
        this.values = values;
    }
}
