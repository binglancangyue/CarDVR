package com.bx.carDVR.bylym.model.listener;

/**
 * @author Altair
 * @date :2020.05.29 下午 05:20
 * @description:
 */
public interface OnDialogCallBackListener {
    void updateDVRUI(int type);

    interface OnShowFormatDialogListener {
        void showFormatDialog();
    }
}
