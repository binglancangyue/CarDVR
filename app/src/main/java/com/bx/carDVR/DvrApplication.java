package com.bx.carDVR;

import android.app.Application;

/**
 * @author Altair
 * @date :2019.12.28 下午 02:27
 * @description:
 */
public class DvrApplication extends Application {
    private static DvrApplication application;
    private DVRService.RecorderInterface mRecorder;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
    }

    public static DvrApplication getInstance() {
        return application;
    }

    public void setRecorderInterface(DVRService.RecorderInterface recorderInterface) {
        this.mRecorder = recorderInterface;
    }
    public DVRService.RecorderInterface getRecorderInterface() {
        return mRecorder;
    }

}
