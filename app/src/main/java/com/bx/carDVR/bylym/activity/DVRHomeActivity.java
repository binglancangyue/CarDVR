package com.bx.carDVR.bylym.activity;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bx.carDVR.Configuration;
import com.bx.carDVR.DVRFileList;
import com.bx.carDVR.DVRService;
import com.bx.carDVR.DvrApplication;
import com.bx.carDVR.R;
import com.bx.carDVR.Recorder;
import com.bx.carDVR.SettingInfo;
import com.bx.carDVR.bylym.model.NotifyMessageManager;
import com.bx.carDVR.bylym.model.listener.OnDialogCallBackListener;
import com.bx.carDVR.bylym.model.tools.CheckCamera;
import com.bx.carDVR.bylym.model.tools.DialogTool;
import com.bx.carDVR.bylym.model.tools.FunctionTool;
import com.bx.carDVR.bylym.model.tools.RequestPermissionTool;
import com.bx.carDVR.bylym.model.tools.ToastTool;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Altair
 * @date :2019.12.28 下午 03:23
 * @description:
 */
public class DVRHomeActivity extends Activity implements View.OnClickListener,
        OnDialogCallBackListener, OnDialogCallBackListener.OnShowFormatDialogListener {
    private FrameLayout mFrameLayout;
    public static final String TAG = "DVRHomeActivity";
    private Context mContext;

    private static final int SUB_PAGE_PICTURE = 0;
    private static final int SUB_PAGE_VIDEO = 1;
    private static final int SUB_PAGE_PLAY = 2;

    private static final int VIDEO_NUM = Configuration.CAMERA_NUM;
    //    private Handler mHandler = new Handler();
    private InnerHandler mHandler;
    private ServiceConnection mSrvConnection;
    private DVRService.DVRSrvBinder mDVRSrvBinder;
    private DVRService.SettingInterface mSetting;
    private DVRService.RecorderInterface mRecorder;
    private DVRService.OnAutoRecordingStateChangeListener mAutoRecordingStateListener;
    private DVRService.OnCameraSignalStateChangeListener mCameraSignalStateListener;
    private Recorder.OnCameraConnectionStateChangeListener mCameraConnectionStateListener;
    private BroadcastReceiver mStorageDeviceConnectionReceiver;

    private int curSubPage;
    private boolean isRecording;
    private int mCurVideoId;
    private int otherVideoId;

    private DVRFileList mList;
    private Map<String, List<String>> mVideoList;
    private Map<String, List<String>> mPicList;
    private ArrayList<String> mVideoListArray;
    private ArrayList<String> mPicListArray;
    private ArrayList<String> mFileListArray;

    private int curRecordingTime;
    private Timer mRecordingTimer;
    private SurfaceView mVideoView;
    private SurfaceView mBackVideoView;
    private View mVideoBlackView;
    private View mNoSignalView;

    private View mRecordingDispView;
    private TextView mRecordingTimeView;

    private MediaPlayer mMediaPlayer;
    private Bitmap mBitmap;
    private ImageView imgBtnLock;
    private ImageView imgBtnTakePicture;
    private ImageView imgBtnRecord;
    private ImageView imgBtnADAS;
    private ImageView imgBtnMicrophone;
    private ImageView imgBtnSettings;

    private ImageButton imgBtnOperator;
    private ImageView ivRecordingIcon;
    private TextView tvRecordingTime;
    private FunctionTool mFunctionTool;
    private static final String ACTION_SHOW_NAVIGATION = "com.bx.carDVR.show.navigation";
    private DialogTool mDialogTool;
    private boolean isSOSEnable = false;
    private String[] permissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: ");
        setContentView(R.layout.activity_car_dvr_test);
        this.mContext = this;
        mHandler = new InnerHandler(this);
        mFunctionTool = new FunctionTool();
        mDialogTool = new DialogTool();

        NotifyMessageManager.getInstance().setDialogCallBackListener(this);
        initView();
    }

    private void initView() {
        curSubPage = SUB_PAGE_VIDEO;

//        mFrameLayout = findViewById(R.id.fl_camera);
//        if (mVideoPageParent == null) {
//            View parent = getLayoutInflater().inflate(R.layout.camera_layout,null );
//            View parent = getLayoutInflater().inflate(R.layout.camera_layout, mFrameLayout, true);
//            mVideoPageParent = (ViewGroup) parent;
        initVideoView();

        //        }
//        mFrameLayout.addView(mVideoPageParent);

        if (mRecorder != null && mRecorder.getCameraConnectionState(mCurVideoId) == Recorder.CAMERA_CONNECTED) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    addPreviewVideoView();
                }
            }, 200);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_lock:
                if (isSOSEnable) {
                    mRecorder.lockFile();
                } else {
                    ToastTool.showToast(R.string.please_open_record);
                }
                break;
            case R.id.btn_take_picture:
                if (Configuration.ONLY_TAKE_PHOTOS_WHILE_RECORDING && isRecording) {
                    onClickTakePicture();
                }
                break;
            case R.id.btn_record:
                onClickRecordingVideo();
                break;
            case R.id.btn_adas:
                boolean isOpen = imgBtnADAS.isSelected();
                isOpen(isOpen, imgBtnADAS);
                break;
            case R.id.btn_microphone:
                boolean open = imgBtnMicrophone.isSelected();
                isOpen(open, imgBtnMicrophone);
                mFunctionTool.closeOrOpen(open);

                break;
            case R.id.btn_settings:
                if (isRecording && mRecorder.isCurrentlyAutoRecording()) {
                    mDialogTool.showSettingDialog(mContext);
                } else {
                    sendBroadcastForHideNavigationBar();
                }
                break;
//            default:
//                Log.d("aaa", "onClick:SUB_PAGE_SET ");
//                switchToSubPage(SUB_PAGE_SET);
//                break;
        }
    }

    private void isOpen(boolean isOpen, ImageView imageView) {
        imageView.setSelected(!isOpen);
        Log.d(TAG, "isOpen:!isOpen "+!isOpen);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");
        imgBtnMicrophone.setSelected(!mFunctionTool.isMicrophoneMute());
        Log.d(TAG, "onStart: mFunctionTool.isMicrophoneMute() "+mFunctionTool.isMicrophoneMute());
        hideNavigationBar();
//        getWindow().getAttributes().systemUiVisibility=View.SYSTEM_UI_FLAG_IMMERSIVE;
        Intent intent = new Intent(this, DVRService.class);
        mSrvConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mDVRSrvBinder = (DVRService.DVRSrvBinder) service;

                if (mDVRSrvBinder == null) {
                    Log.w(TAG, "binder is null !");
                    return;
                }
                onDVRServiceConnected();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                onDVRServiceDisconnected();
            }
        };

        startService(intent);
        bindService(intent, mSrvConnection, Context.BIND_AUTO_CREATE);
        registerReceiver();
//        hideNavigationBar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: ");
        if (mRecorder != null && mRecorder.isCurrentlyAutoRecording()) {
            onAutoRecordingStart();
        }
        if (mRecorder != null) {
            imgBtnRecord.setSelected(mRecorder.isCurrentlyAutoRecording());
        }
        NotifyMessageManager.getInstance().setShowFormatDialogListener(this);
        Log.d(TAG, "onResume: " + getApplicationContext().getResources().getDisplayMetrics().densityDpi);
    }

    @Override
    protected void onPause() {
//        stopRecording();
//        stopRecordingTimer();
        super.onPause();
        Log.d(TAG, "onPause: ");
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        mStorageDeviceConnectionReceiver = new StorageDeviceConnectionReceiver();
        registerReceiver(mStorageDeviceConnectionReceiver, filter);
    }

    private void showNavigationBar(boolean show) {
        Intent intent = new Intent();
        intent.setAction(ACTION_SHOW_NAVIGATION);
        intent.putExtra("show", show);
//        intent.setComponent(new ComponentName("com.android.systemui",
//                "com.android.systemui.navigationbar.NavigationBar"));
        Log.d(TAG, "showNavigationBar: show " + show);
//        sendBroadcast(intent);
    }

    private void hideNavigationBar() {
        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        uiFlags |= 0x00001000;
        getWindow().getDecorView().setSystemUiVisibility(uiFlags);
    }

    private void showNavigationBar() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void onDVRServiceConnected() {
        mDVRSrvBinder.notifyRecordingComponent(this);
        mSetting = mDVRSrvBinder.getSettingInterface();
        mRecorder = mDVRSrvBinder.getRecorderInterface();
        DvrApplication.getInstance().setRecorderInterface(mRecorder);
        mAutoRecordingStateListener = new DVRService.OnAutoRecordingStateChangeListener() {
            @Override
            public void onStart() {
                onAutoRecordingStart();
            }

            @Override
            public void onStop() {
                onAutoRecordingStop();
            }
        };
        mCameraSignalStateListener = new DVRService.OnCameraSignalStateChangeListener() {
            @Override
            public void onSignalGot() {
                onCameraSignalGot();
            }

            @Override
            public void onSignalLost() {
                onCameraSignalLost();
            }
        };
        mCameraConnectionStateListener = new Recorder.OnCameraConnectionStateChangeListener() {
            @Override
            public void onConnect() {
                onCameraConnected();
            }

            @Override
            public void onDisconnect() {
                onCameraDisconnected();
            }
        };

        mRecorder.registerOnAutoRecordingStateChangeListener(mAutoRecordingStateListener);
        mRecorder.registerOnCameraConnectionStateChangeListener(mCurVideoId,
                mCameraConnectionStateListener);
        mRecorder.registerOnCameraSignalStateChangeListener(mCurVideoId,
                mCameraSignalStateListener);

        //add camera0 start
        mRecorder.registerOnCameraConnectionStateChangeListener(0,
                mCameraConnectionStateListener);
        mRecorder.registerOnCameraSignalStateChangeListener(0,
                mCameraSignalStateListener);
        //end

        if (mRecorder.getCameraConnectionState(mCurVideoId) == Recorder.CAMERA_CONNECTED) {
            onCameraConnected();
        }
    }

    private void onDVRServiceDisconnected() {
        if (mRecorder != null) {
            mRecorder.unregisterOnAutoRecordingStateChangeListener(mAutoRecordingStateListener);
            mRecorder.unregisterOnCameraConnectionStateChangeListener(mCurVideoId,
                    mCameraConnectionStateListener);
            mRecorder.unregisterOnCameraSignalStateChangeListener(mCurVideoId,
                    mCameraSignalStateListener);
            if (mRecorder.getCameraConnectionState(mCurVideoId) == Recorder.CAMERA_CONNECTED) {
                onCameraDisconnected();
                Log.d(TAG, "onDVRServiceDisconnected: ");
            }
            //add start
            if (Configuration.CAMERA_NUM == 2) {
                int otherCameraId;
                if (mCurVideoId == 0) {
                    otherCameraId = 1;
                } else {
                    otherCameraId = 0;
                }
                mRecorder.unregisterOnCameraConnectionStateChangeListener(otherCameraId,
                        mCameraConnectionStateListener);
                mRecorder.unregisterOnCameraSignalStateChangeListener(otherCameraId,
                        mCameraSignalStateListener);
            }
            //end
        }

        mDVRSrvBinder.cancelRecordingComponent(this);
        mDVRSrvBinder = null;
        mSetting = null;
        mRecorder = null;
    }

    private void addPreviewVideoView() {
        if (curSubPage != SUB_PAGE_PICTURE && curSubPage != SUB_PAGE_VIDEO) {
            return;
        }

        if (mVideoView != null) {
            mVideoView.setVisibility(View.VISIBLE);
            //add back camera start
            if (Configuration.CAMERA_NUM == 2) {
                mBackVideoView.setVisibility(View.VISIBLE);
            } else {
                mBackVideoView.setVisibility(View.GONE);
            }
            //end
        }
    }

    private void onCameraConnected() {
        if (curSubPage == SUB_PAGE_PICTURE || curSubPage == SUB_PAGE_VIDEO) {
            if (mVideoView != null) {
                mVideoView.setVisibility(View.INVISIBLE);
                if (Configuration.CAMERA_NUM == 2) {
                    mBackVideoView.setVisibility(View.INVISIBLE);
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        addPreviewVideoView();
                    }
                }, 200);
            }
        }

        if (mRecorder != null && mRecorder.isCurrentlyAutoRecording()) {
            onAutoRecordingStart();
        }
    }

    private void onCameraDisconnected() {
        if (curSubPage == SUB_PAGE_PICTURE || curSubPage == SUB_PAGE_VIDEO) {
            showBlackVideo();
            if (mVideoView != null) {
                mVideoView.setVisibility(View.INVISIBLE);
                if (Configuration.CAMERA_NUM == 2) {
                    mBackVideoView.setVisibility(View.INVISIBLE);
                }
            }

            mRecordingDispView.setVisibility(View.INVISIBLE);
//            mRecordingBtn.setImageResource(R.drawable.recording_selector);
        }

        stopRecording();
        stopRecordingTimer();
    }

    private void onCameraSignalGot() {
        if (curSubPage == SUB_PAGE_PICTURE || curSubPage == SUB_PAGE_VIDEO) {
            showBlackVideo();
            mNoSignalView.setVisibility(View.INVISIBLE);
            mHandler.postDelayed(removeBlackVideo, 500);
        }
    }

    private void onCameraSignalLost() {
        if (curSubPage == SUB_PAGE_PICTURE || curSubPage == SUB_PAGE_VIDEO) {
            showBlackVideo();
            mNoSignalView.setVisibility(View.VISIBLE);
        }
    }

    private void onAutoRecordingStart() {
//        isRecording = false;
        isRecording = true;
        if (curSubPage == SUB_PAGE_PICTURE || curSubPage == SUB_PAGE_VIDEO) {
            mRecordingDispView.setVisibility(View.VISIBLE);
//            mRecordingBtn.setImageResource(R.drawable.stop_record_selector);
            Log.d(TAG, "onAutoRecordingStart: ");
            mHandler.sendEmptyMessage(2);
            startRecordingTimer();
        }
    }

    private void onAutoRecordingStop() {
        isRecording = false;
        stopRecordingTimer();

        if (curSubPage == SUB_PAGE_PICTURE || curSubPage == SUB_PAGE_VIDEO) {
            mRecordingDispView.setVisibility(View.INVISIBLE);
            imgBtnRecord.setSelected(false);
        }
    }

    private void showBlackVideo() {
        mHandler.removeCallbacks(removeBlackVideo);
//        mVideoBlackView.setVisibility(View.VISIBLE);
    }

    private Runnable removeBlackVideo = new Runnable() {
        @Override
        public void run() {
            if (curSubPage != SUB_PAGE_PICTURE && curSubPage != SUB_PAGE_VIDEO) {
                return;
            }

            mVideoBlackView.setVisibility(View.INVISIBLE);
        }
    };

    private void onStorageDeviceUnmounted(final String devPath) {
        if (mSetting != null && mSetting.isDeviceMatchingSetPath(devPath)) {
            stopRecording();
            stopRecordingTimer();

            if (curSubPage == SUB_PAGE_PLAY) {
                updateListInfo();
            }
        }
    }

    private void updateListInfo() {
        boolean hasList = true;
        if (mRecorder != null) {
            mList = mRecorder.getFileList(mCurVideoId);
            if (mList == null) {
                hasList = false;
            }
        } else {
            mList = null;
            hasList = false;
        }
//        mListSwitcher.removeAllViews();
        if (hasList) {
            mVideoList = mList.getVideoList();
            mPicList = mList.getPictureList();
        }
    }

    private void formatCard() {
        if (mSetting != null) {
            int strId = -1;
            int result = mSetting.formatStorage();
            Log.d(TAG, "formatCard:result " + result);
            if (result == DVRService.FORMATTING_RST_FAIL_CURRENTLY_RECORDING) {
                strId = R.string.cannot_formatting;
            } else if (result == DVRService.FORMATTING_RST_FAIL_CARD_INVALID) {
                strId = R.string.card_not_exist;
            } else if (result == DVRService.FORMATTING_RST_FAIL) {
                strId = R.string.formatting_fail;
            }

            if (strId != -1) {
                ToastTool.showToast(strId);
            }
        }
    }

    private void onClickTakePicture() {
        if (mRecorder != null) {
            Log.d(TAG, "onClickTakePicture:mCurVideoId " + mCurVideoId);
            mRecorder.cameraTakePicture(mCurVideoId, new Recorder.OnTakePictureFinishListener() {
                @Override
                public void onFinish(int result, String photoPath) {
                    int strId = -1;

                    if (result == Recorder.TAKE_PIC_RST_SUCCESSFUL) {
                        strId = R.string.photo_saved;
                    } else if (result == Recorder.TAKE_PIC_RST_FAIL_NO_CAMERA) {
                        strId = R.string.camera_not_found;
                    } else if (result == Recorder.TAKE_PIC_RST_FAIL_CARD_INVALID) {
                        strId = R.string.card_not_exist;
                    } else if (result == Recorder.TAKE_PIC_RST_FAIL_CURRENTLY_RECORDING) {
                        strId = R.string.cannot_take_pic;
                    } else if (result == Recorder.TAKE_PIC_RST_FAIL) {
                        strId = R.string.take_pic_fail;
                    }
                    Log.d(TAG, "onFinish: strId： " + result);
                    if (strId != -1) {
                        ToastTool.showToast(strId);

                    }
                }
            });

            if (Configuration.CAMERA_NUM == 2) {
                int cameraBackId;
                if (mCurVideoId == 1) {
                    cameraBackId = 0;
                } else {
                    cameraBackId = 1;
                }
                mRecorder.cameraTakePicture(cameraBackId,
                        new Recorder.OnTakePictureFinishListener() {
                            @Override
                            public void onFinish(int result, String photoPath) {

                            }
                        });
            }
        } else {
            Log.d(TAG, "onClickTakePicture:mRecorder != null ");
        }
    }

    private void onClickRecordingVideo() {
        if (mRecorder != null) {
            Log.d(TAG, "onClickRecordingVideo: " + mRecorder.isCurrentlyAutoRecording());
            if (mRecorder.isCurrentlyAutoRecording()) {
                mDialogTool.showStopRecordingDialog(mContext);
//                mSetting.stopAutoRecording();
//                Toast.makeText(this, R.string.auto_recording, Toast.LENGTH_SHORT).show();
            } else {
                if (isRecording) {
                    if (curRecordingTime >= 2) {
//                        stopRecording();
                        mDialogTool.showStopRecordingDialog(mContext);
                    }
                } else {
                    startRecording();
                }
            }
        } else {
            ToastTool.showToast(R.string.camera_not_found);
        }
    }

    private void startRecording() {
        if (!isRecording && mRecorder != null) {
            int strId = -1;
            int result = mRecorder.cameraStartRecording(mCurVideoId);
            //add start
            int cameraBackId;
            if (mCurVideoId == 1) {
                cameraBackId = 0;
            } else {
                cameraBackId = 1;
            }
            if (Configuration.CAMERA_NUM == 2) {
                mRecorder.cameraStartRecording(cameraBackId);
            }
            //end
            if (result == Recorder.RECORDING_RST_SUCCESSFUL) {
                isRecording = true;
                curRecordingTime = 0;
                mRecordingDispView.setVisibility(View.VISIBLE);
//                mRecordingBtn.setImageResource(R.drawable.stop_record_selector);
                mHandler.sendEmptyMessage(2);
                startRecordingTimer();
            } else if (result == Recorder.RECORDING_RST_FAIL_NO_CAMERA) {
                strId = R.string.camera_not_found;
            } else if (result == Recorder.RECORDING_RST_FAIL_CARD_INVALID) {
                strId = R.string.card_not_exist;
            } else if (result == Recorder.RECORDING_RST_FAIL_CURRENTLY_RECORDING) {
                strId = R.string.cannot_recording;
            } else if (result == Recorder.RECORDING_RST_FAIL) {
                strId = R.string.recording_fail;
            }

            if (strId != -1) {
                ToastTool.showToast(strId);
            }
        }
    }

    private void stopRecording() {
        if (isRecording) {
            if (mRecorder != null) {
                mRecorder.cameraStopRecording(mCurVideoId);
                //add start
                int cameraBackId;
                if (mCurVideoId == 1) {
                    cameraBackId = 0;
                } else {
                    cameraBackId = 1;
                }
                if (Configuration.CAMERA_NUM == 2) {
                    mRecorder.cameraStopRecording(cameraBackId);
                }
                //emd
            }
            imgBtnRecord.setSelected(false);
            isRecording = false;
            mRecordingDispView.setVisibility(View.INVISIBLE);
//            mRecordingBtn.setImageResource(R.drawable.recording_selector);
            stopRecordingTimer();
        }
    }

    private void startRecordingTimer() {
        mHandler.sendEmptyMessage(2);
        Log.d(TAG, "startRecordingTimer: ");
        if (mRecordingTimer == null) {
            imgBtnRecord.setSelected(true);
            mRecordingTimer = new Timer();
            mRecordingTimer.schedule(new TimerTask() {
                private Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        updateRecordingTime();
//                        refreshRecordingIcon();
                    }
                };

                @Override
                public void run() {
                    mHandler.removeCallbacks(runnable);
                    mHandler.post(runnable);
                }
            }, 0, 1000);
            if (!mRecorder.isCurrentlyAutoRecording()) {
                mRecordingTimer.schedule(new TimerTask() {
                    private Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            if (isRecording) {
                                curRecordingTime++;

                                if (mSetting != null && mRecorder != null) {
                                    if (curRecordingTime >= (mSetting.settingGetRecordingTime() / SettingInfo.TIME_INTERVAL_1_MINUTE * 60)) {
                                        //mRecorder.cameraStopRecording(mCurVideoId);

                                        curRecordingTime = 0;

                                        if (mRecorder.cameraStartRecording(mCurVideoId) != Recorder.RECORDING_RST_SUCCESSFUL) {
                                            stopRecording();
                                        }
                                    }
                                }
                            }
                        }
                    };

                    @Override
                    public void run() {
                        mHandler.removeCallbacks(runnable);
                        mHandler.post(runnable);
                    }
                }, 300, 1000);
            }
        }
    }

    private void stopRecordingTimer() {
        if (mRecordingTimer != null) {
            mRecordingTimer.cancel();
            mRecordingTimer = null;
        }
    }

    private void updateRecordingTime() {
       /* if (isRecording) {
            String time = String.format("%02d:%02d", curRecordingTime / 60 % 60,
                    curRecordingTime % 60);
            mRecordingTimeView.setText(time);
        } else if (mRecorder != null && mRecorder.isCurrentlyAutoRecording()) {
            int time = mRecorder.getCurrentRecordingTime(mCurVideoId);
            String times = String.format("%02d:%02d", time / 60 % 60, time % 60);
            mRecordingTimeView.setText(times);
        }*/


        if (mRecorder != null && mRecorder.isCurrentlyAutoRecording()) {
            int time = mRecorder.getCurrentRecordingTime(mCurVideoId);
            String times = String.format("%02d:%02d", time / 60 % 60, time % 60);
            mRecordingTimeView.setText(times);
//            Log.d(TAG, "updateRecordingTime:isCurrentlyAutoRecording ");
        } else {
            if (isRecording) {
                String time = String.format("%02d:%02d", curRecordingTime / 60 % 60,
                        curRecordingTime % 60);
                mRecordingTimeView.setText(time);
            }
        }
    }

    private void refreshRecordingIcon() {
        if (isRecording) {
            /*if ((curRecordingTime % 2) == 0) {
                mRecordingBtn.setImageResource(R.drawable.stop_record_selector);
            } else {
                mRecordingBtn.setImageResource(R.drawable.recording_selector);
            }*/
        } else if (mRecorder != null && mRecorder.isCurrentlyAutoRecording()) {
           /* int time = mRecorder.getCurrentRecordingTime(mCurVideoId);
            if ((time % 2) == 0) {
                mRecordingBtn.setImageResource(R.drawable.stop_record_selector);
            } else {
                mRecordingBtn.setImageResource(R.drawable.recording_selector);
            }*/
        }
    }

    private boolean switchVideo(int idx) {
        if (idx == mCurVideoId || idx < 0 || idx >= VIDEO_NUM) {
            return false;
        }

        if (isRecording) {
            return false;
        }

        if (mRecorder != null && mRecorder.isCurrentlyAutoRecording()) {
            return false;
        }

        if (curSubPage == SUB_PAGE_PICTURE || curSubPage == SUB_PAGE_VIDEO) {
            showBlackVideo();
            if (mVideoView != null) {
                mVideoView.setVisibility(View.INVISIBLE);
                mBackVideoView.setVisibility(View.INVISIBLE);
                if (mRecorder != null && mRecorder.getCameraConnectionState(idx) == Recorder.CAMERA_CONNECTED) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            addPreviewVideoView();
                        }
                    }, 10);
                }
            }
        }

        Log.d(TAG, "switchVideo idx: " + idx);
        if (mRecorder != null) {
            mRecorder.unregisterOnCameraConnectionStateChangeListener(mCurVideoId,
                    mCameraConnectionStateListener);
            mRecorder.unregisterOnCameraSignalStateChangeListener(mCurVideoId,
                    mCameraSignalStateListener);

            //add by lym
            if (Configuration.CAMERA_NUM == 2) {
                mRecorder.unregisterOnCameraConnectionStateChangeListener(idx,
                        mCameraConnectionStateListener);
                mRecorder.unregisterOnCameraSignalStateChangeListener(idx,
                        mCameraSignalStateListener);
                mRecorder.registerOnCameraConnectionStateChangeListener(mCurVideoId,
                        mCameraConnectionStateListener);
                mRecorder.registerOnCameraSignalStateChangeListener(mCurVideoId,
                        mCameraSignalStateListener);
            }
            //end

            mRecorder.registerOnCameraConnectionStateChangeListener(idx,
                    mCameraConnectionStateListener);
            mRecorder.registerOnCameraSignalStateChangeListener(idx, mCameraSignalStateListener);
        }

        mCurVideoId = idx;
        return true;
    }

    private void initVideoView() {
//        View parent = mVideoPageParent;
        mVideoView = findViewById(R.id.video_preview);
        mBackVideoView = findViewById(R.id.video_back_preview);
        mVideoView.setVisibility(View.INVISIBLE);
        mBackVideoView.setVisibility(View.INVISIBLE);
        initCameraBtn();
        mVideoView.getHolder().setKeepScreenOn(true);
        mBackVideoView.getHolder().setKeepScreenOn(true);
        mVideoView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surfaceChanged:mCurVideoId " + mCurVideoId);
                if (mRecorder != null) {
                    if (width > 0 && height > 0) {
                        mRecorder.cameraStartPreview(mCurVideoId, holder);
                        showBlackVideo();
                        if (mRecorder.isOpenCamera(mCurVideoId)) {
                            mHandler.postDelayed(removeBlackVideo, 500);
                            mNoSignalView.setVisibility(View.INVISIBLE);
                        } else {
                            mNoSignalView.setVisibility(View.VISIBLE);
                        }
//                        if (mRecorder.cameraIsSignalNormal(mCurVideoId)) {
//                            mHandler.postDelayed(removeBlackVideo, 500);
//                            mNoSignalView.setVisibility(View.INVISIBLE);
//                        } else {
//                            mNoSignalView.setVisibility(View.VISIBLE);
//                        }
                    }
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mRecorder != null) {
                    Log.d(TAG, "surfaceDestroyed:mCurVideoId " + mCurVideoId);
                    mRecorder.cameraStopPreview(mCurVideoId);
                }
                showBlackVideo();
            }
        });

        if (Configuration.CAMERA_NUM == 2) {
            mBackVideoView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {

                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                           int height) {
                    if (mRecorder != null) {
                        if (width > 0 && height > 0) {
                            if (mCurVideoId == 0) {
                                otherVideoId = 1;
                            } else {
                                otherVideoId = 0;
                            }
                            Log.d(TAG, "surfaceChanged:otherVideoId " + otherVideoId);
                            mRecorder.cameraStartPreview(otherVideoId, holder);

                            showBlackVideo();
//                        Log.d(TAG, "surfaceChanged:mCurVideoId: " +
//                                mRecorder.cameraIsSignalNormal(mCurVideoId) +
//                                " cameraId: " + mRecorder.cameraIsSignalNormal(otherVideoId));
                            if (mRecorder.cameraIsSignalNormal(otherVideoId)) {
                                mBackVideoView.setVisibility(View.VISIBLE);
                            } else {
                                mBackVideoView.setVisibility(View.GONE);
                            }
                        }
                    }
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    Log.d(TAG, "surfaceDestroyed:otherVideoId " + otherVideoId);
                    if (mRecorder != null) {
                        mRecorder.cameraStopPreview(otherVideoId);
                    }
                    showBlackVideo();
                }
            });
        }

        mVideoBlackView = findViewById(R.id.video_black);
        mNoSignalView = findViewById(R.id.no_signal_str);
        mVideoBlackView.setVisibility(View.INVISIBLE);

        mRecordingDispView = findViewById(R.id.recording_disp);
        mRecordingTimeView = findViewById(R.id.recording_time);

        if (VIDEO_NUM > 1) {
/*            int[] btn_ids = {R.id.button_ch1, R.id.button_ch2, R.id.button_ch3, R.id.button_ch4};
            for (int i = 0; i < VIDEO_NUM; i++) {
                final int idx = i;
                mSelectVideoBtns[i] = parent.findViewById(btn_ids[i]);
                mSelectVideoBtns[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCurVideoId != idx) {
                            int lastId = mCurVideoId;
                            if (switchVideo(idx)) {
                                mSelectVideoBtns[lastId].setSelected(false);
                                mSelectVideoBtns[idx].setSelected(true);
                            }
                        }
                    }
                });
                mSelectVideoBtns[i].setVisibility(View.VISIBLE);
            }
            parent.findViewById(R.id.select_video_key).setVisibility(View.VISIBLE);
            mSelectVideoBtns[mCurVideoId].setSelected(true);*/

            mBackVideoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int id;
                    if (mCurVideoId != 0) {
                        id = 0;
                    } else {
                        id = 1;
                    }
                    switchVideo(id);
                }
            });
        }
    }

    private void initCameraBtn() {
        imgBtnLock = findViewById(R.id.btn_lock);
        imgBtnTakePicture = findViewById(R.id.btn_take_picture);
        imgBtnRecord = findViewById(R.id.btn_record);
        imgBtnADAS = findViewById(R.id.btn_adas);
        imgBtnMicrophone = findViewById(R.id.btn_microphone);
        imgBtnSettings = findViewById(R.id.btn_settings);

//        ivRecordingIcon = findViewById(R.id.iv_recording_icon);
//        tvRecordingTime = findViewById(R.id.tv_recording_time);
        imgBtnLock.setOnClickListener(this);
        imgBtnTakePicture.setOnClickListener(this);
        imgBtnRecord.setOnClickListener(this);
        imgBtnADAS.setOnClickListener(this);
        imgBtnMicrophone.setOnClickListener(this);
        imgBtnSettings.setOnClickListener(this);
        closeSOS();
    }

    @Override
    public void updateDVRUI(int type) {
        if (type == 0 || type == 2) {
            if (mRecorder != null) {
                if (mRecorder.isCurrentlyAutoRecording()) {
                    mSetting.stopAutoRecording();
                } else {
                    if (isRecording) {
                        if (curRecordingTime >= 2) {
                            stopRecording();
                        }
                    }
                }
            }
            mHandler.sendEmptyMessage(1);
            if (type == 2) {
                sendBroadcastForHideNavigationBar();
            }
        } else {
            formatCard();
        }
    }

    private void sendBroadcastForHideNavigationBar() {
        Intent intent = new Intent(Configuration.ACTION_SHOW_SETTING_WINDOW);
        intent.putExtra("isHideNavigationBar", true);
        sendBroadcast(intent);
    }

    @Override
    public void showFormatDialog() {
        mDialogTool.showFormatDialog(mContext);
    }

    private class StorageDeviceConnectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_REMOVED)
                    || action.equals(Intent.ACTION_MEDIA_EJECT)) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String path = uri.getPath();
                    onStorageDeviceUnmounted(path);
                }
            }
        }
    }

    private void openSOS() {
        isSOSEnable = true;
        imgBtnLock.setImageResource(R.drawable.selector_sos);
    }

    private void closeSOS() {
        isSOSEnable = false;
        imgBtnLock.setImageResource(R.drawable.icon_dvr_sos_close);
    }

    private static class InnerHandler extends Handler {
        private final WeakReference<DVRHomeActivity> activityWeakReference;
        private DVRHomeActivity mDvrHomeActivity;

        private InnerHandler(DVRHomeActivity popupWindow) {
            this.activityWeakReference = new WeakReference<>(popupWindow);
            mDvrHomeActivity = activityWeakReference.get();
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1) {
                mDvrHomeActivity.closeSOS();
            }
            if (msg.what == 2) {
                mDvrHomeActivity.openSOS();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        NotifyMessageManager.getInstance().setShowFormatDialogListener(null);
        showNavigationBar();
        Log.d(TAG, "onStop: ");
        stopRecording();
        stopRecordingTimer();
        unregisterReceiver(mStorageDeviceConnectionReceiver);
        unbindService(mSrvConnection);
        onDVRServiceDisconnected();
        Log.d(TAG, "onStop: after");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        super.onDestroy();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        if (mDialogTool != null) {
            mDialogTool.dismissDialog();
        }
    }

}
