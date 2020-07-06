package com.bx.carDVR.bylym.model;

import com.bx.carDVR.bylym.model.listener.OnDialogCallBackListener;
import com.bx.carDVR.bylym.model.listener.OnLocationListener;

/**
 * @author Altair
 * @date :2020.03.26 下午 04:28
 * @description: 回调管理类
 */
public class NotifyMessageManager {
    private OnDialogCallBackListener mDialogCallBackListener;
    private OnDialogCallBackListener.OnShowFormatDialogListener mShowFormatDialogListener;
    private OnLocationListener mOnLocationListener;

    public static NotifyMessageManager getInstance() {
        return SingletonHolder.sInstance;
    }

    private static class SingletonHolder {
        private static final NotifyMessageManager sInstance = new NotifyMessageManager();
    }

    public void setDialogCallBackListener(OnDialogCallBackListener listener) {
        this.mDialogCallBackListener = listener;
    }

    public void setShowFormatDialogListener(OnDialogCallBackListener.OnShowFormatDialogListener listener) {
        this.mShowFormatDialogListener = listener;
    }

    public void updateDVRUI(int type) {
        if (mDialogCallBackListener != null) {
            mDialogCallBackListener.updateDVRUI(type);
        }
    }

    public void showFormatDialog() {
        if (mShowFormatDialogListener != null) {
            mShowFormatDialogListener.showFormatDialog();
        }
    }

    public boolean showFormatDialogListener() {
        if (mShowFormatDialogListener == null) {
            return true;
        }
        return false;
    }

    public void setOnLocationListener(OnLocationListener listener) {
        this.mOnLocationListener = listener;
    }

    public void gpsSpeedChange() {
        if (mOnLocationListener == null) {
            return;
        }
        mOnLocationListener.gpsSpeedChanged();
    }

}
