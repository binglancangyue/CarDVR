package com.bx.carDVR;

public class Configuration {
    public static final boolean CAMERA2 = true;

    public static final int CAMERA_NUM = 1;
    //    public static final int[] CAMERA_IDS = {1, 0, 6, 7};
    public static final int[] CAMERA_IDS = {0, 1, 6, 7};

    public static final boolean[] IS_USB_CAMERA = {false, false, false, false}; //是否是USB摄像头

    public static final boolean ENABLE_CAR_REVERSE = false;

    public static final boolean ONLY_TAKE_PHOTOS_WHILE_RECORDING = true;

    public static final String ACTION_CLOSE_DVR = "com.bx.carDVR.action_close";
    public static final String ACTION_SET_DVR_RECORD_TIME = "com.android.systemui.SET_DVR_RECORD_TIME";
    public static final String ACTION_SHOW_SETTING_WINDOW = "com.android.systemui.show_setting_window";
    public static final String ACTION_SET_G_SENSOR_LEVEL = "com.android.systemui.SET_G_SENSOR_LEVEL";
    public static final String ACTION_SET_ADAS_LEVEL = "com.android.systemui.SET_ADAS_LEVEL";
    public static final String ACTION_UPLOAD_VIDEO = "com.bx.carDVR.action.UPLOAD_VIDEO";
    public static final String ACTION_FORMAT_SD_CARD = "com.android.systemui.FORMAT_SD_CARD";

    public static final String DVR_COLLISION = "Dvr_collision";
    public static final String DVR_ADAS = "Dvr_ADAS";

    /*
    public static final int CAMERA_NUM = 4; //sofar 4 avin camera
    public static final int[] CAMERA_IDS = {4, 5, 6, 7};

    public static final boolean[] IS_USB_CAMERA = {false, false, false, false}; //是否是USB摄像头

    public static final boolean ENABLE_CAR_REVERSE = true;

     */

    /*
    public static final int CAMERA_NUM = 1; //tianyu 1 usb camera
    public static final int[] CAMERA_IDS = {0, 5, 6, 7};

    public static final boolean[] IS_USB_CAMERA = {true, false, false, false}; //是否是USB摄像头

    public static final boolean ENABLE_CAR_REVERSE = false;
    */
    private Configuration() {
    }
}
