package com.bx.carDVR.bylym.model.tools;

import android.content.Context;
import android.content.SharedPreferences;

import com.bx.carDVR.Configuration;
import com.bx.carDVR.DvrApplication;
import com.bx.carDVR.SettingInfo;

/**
 * @author Altair
 * @date :2019.12.31 上午 10:13
 * @description:
 */
public class SharePreferenceTool {
    private static SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;

    private static class SingletonHolder {
        private static final SharePreferenceTool INSTANCE = new SharePreferenceTool();
    }

    public static SharePreferenceTool getInstance() {
        return SingletonHolder.INSTANCE;
    }


    public SharePreferenceTool() {
        mSharedPreferences = DvrApplication.getInstance().getSharedPreferences("UTOPS-DVR-Service-Preferences",
                Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
    }

    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    public void saveString(String value) {
        mEditor.putString("SD_PATH", value);
        mEditor.apply();
    }

    public void saveString(String key, String value) {
        mEditor.putString(key, value);
        mEditor.apply();
    }

    public int getRecordTime() {
        return mSharedPreferences.getInt(SettingInfo.ITEM_RECORDING_TIME, 60);
    }

    public void saveGSensorLevel(int level) {
        mEditor.putInt(Configuration.DVR_COLLISION, level);
        mEditor.apply();
    }

    public int getGSensorLevel() {
        int level = mSharedPreferences.getInt(Configuration.DVR_COLLISION, 2);
        return level;
    }

    public int getADASLevel() {
        int level = mSharedPreferences.getInt(Configuration.DVR_ADAS, 2);
        return level;
    }

    public void saveADASLevel(int level) {
        mEditor.putInt(Configuration.DVR_ADAS, level);
        mEditor.apply();
    }
}
