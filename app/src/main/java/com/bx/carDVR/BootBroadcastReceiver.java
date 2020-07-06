package com.bx.carDVR;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("BootBroadcastReceiver", "boot complete, so start DVR service.");
//        context.startService(new Intent(context, DVRService.class));
    }
}
