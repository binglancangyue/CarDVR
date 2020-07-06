package com.bx.carDVR;

public class SettingInfo {
    public boolean isAutoRecording;
    public int autoRecordingTime;
    public boolean isRecordingBackstage;
    public int savingPath;

    public static final String ITEM_AUTO_RECORDING = "Is-Auto-Recording";
    public static final String ITEM_RECORDING_TIME = "Auto-Recording-Time";
    public static final String ITEM_RECORDING_BACKSTAGE = "Is-Recording-Backstage";
    public static final String ITEM_FILE_PATH = "File-Path";

    public static final int TIME_INTERVAL_1_MINUTE = 60;
    public static final int TIME_INTERVAL_3_MINUTE = 60 * 3;
    public static final int TIME_INTERVAL_5_MINUTE = 60 * 5;
    public static final int TIME_INTERVAL_10_MINUTE = 60 * 10;
    public static final int TIME_INTERVAL_MAX = TIME_INTERVAL_10_MINUTE;

    public static final int PATH_SD = 0;
    public static final int PATH_USB = 1;
    public static final int PATH_OTHER = 2;
}
