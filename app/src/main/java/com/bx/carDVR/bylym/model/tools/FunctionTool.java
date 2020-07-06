package com.bx.carDVR.bylym.model.tools;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.util.Log;

import com.bx.carDVR.DvrApplication;

/**
 * @author Altair
 * @date :2020.04.15 下午 06:55
 * @description:
 */
public class FunctionTool {
    private AudioManager audioManager;
    private Context mContext;

    public FunctionTool() {
        this.mContext = DvrApplication.getInstance();
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mStorage = DvrApplication.getInstance().getSystemService(StorageManager.class);
    }

    public void closeOrOpen(boolean b) {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.STREAM_MUSIC);
            Log.d("test", "closeOrOpen: "+b);
            audioManager.setMicrophoneMute(b);
        }
    }

    public boolean isMicrophoneMute() {
        return audioManager.isMicrophoneMute();
    }

    protected StorageManager mStorage;
    protected DiskInfo mDisk;
    public static final String EXTRA_FORMAT_PRIVATE = "format_private";
    public static final String EXTRA_FORGET_UUID = "forget_uuid";
//    public void aa() {
//        mDisk = mStorage.findDiskById("disk:179,64");
//        final Intent intent = new Intent(this, StorageWizardFormatProgress.class);
//        intent.putExtra(DiskInfo.EXTRA_DISK_ID, mDisk.getId());
//        intent.putExtra(EXTRA_FORMAT_PRIVATE, false);
//        intent.putExtra(EXTRA_FORGET_UUID, getIntent().getStringExtra(EXTRA_FORGET_UUID));
//        DvrApplication.getInstance().startActivity(intent);
//    }
}
