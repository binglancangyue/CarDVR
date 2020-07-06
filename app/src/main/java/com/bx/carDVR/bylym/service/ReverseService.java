package com.bx.carDVR.bylym.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.bx.carDVR.DvrApplication;
import com.bx.carDVR.R;

/**
 * @author Altair
 * @date :2020.01.08 上午 09:32
 * @description:
 */
public class ReverseService extends Service {
    private static final String TAG = "ReverseService";
    public static final String ACTION_STATE_ACC_CHANGED = "android.car.action.STATE_ACC_CHANGED";
    public static final String ACTION_STATE_CAR_REVERSE_CHANGED =
            "android.car.action.STATE_CAR_REVERSE_CHANGED";
    public static final String ACTION_STATE_HAND_BRAKE_CHANGED =
            "android.car.action.STATE_HAND_BRAKE_CHANGED";
    private View reverseView;
    private WindowManager mWindowManager;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ReverseService");
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerDVRReceiver();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                Log.e(TAG, "onReceive: action=null");
                return;
            }
            if (action.equals(ACTION_STATE_ACC_CHANGED)) {
                Log.d(TAG, "onReceive: ACTION_STATE_ACC_CHANGED");
            }
            if (action.equals(ACTION_STATE_CAR_REVERSE_CHANGED)) {
                Log.d(TAG, "onReceive: ACTION_STATE_CAR_REVERSE_CHANGED");
            }
            if (action.equals(ACTION_STATE_HAND_BRAKE_CHANGED)) {
                Log.d(TAG, "onReceive: ACTION_STATE_HAND_BRAKE_CHANGED");
            }
        }
    };

    private void registerDVRReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_STATE_ACC_CHANGED);
        intentFilter.addAction(ACTION_STATE_CAR_REVERSE_CHANGED);
        intentFilter.addAction(ACTION_STATE_HAND_BRAKE_CHANGED);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    public void unRegisterDVRReceiver() {
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ReverseService");
        unRegisterDVRReceiver();
    }

    private void createDVRReverseView() {
        LayoutInflater layoutInflater = LayoutInflater.from(DvrApplication.getInstance());
        reverseView = layoutInflater.inflate(R.layout.layout_reverse_view, null);
        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_PRIORITY_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING,
                        PixelFormat.RGBX_8888);
        params.token = new Binder();
        if (mWindowManager != null) {
            mWindowManager.addView(reverseView, params);
        }
    }

    private void showWindow() {

    }

    private void removeWindow() {
        if (mWindowManager != null) {
            mWindowManager.removeView(reverseView);
        }
    }
}
