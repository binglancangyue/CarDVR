package com.bx.carDVR.bylym.myaidl;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import java.util.Map;

/**
 * @author Altair
 * @date :2019.12.27 下午 12:06
 * @description:
 */
public class AIDLService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    private IBinder iBinder = new FileListInterface.Stub() {
        @Override
        public Map getVideoList() throws RemoteException {
            return null;
        }

        @Override
        public Map getPictureList() throws RemoteException {

            return null;
        }
    };

}
