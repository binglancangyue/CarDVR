package com.bx.carDVR;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
//import android.os.UEventObserver;
import android.os.IBinder;
//import com.android.internal.os.storage.ExternalStorageFormatter;

import android.os.Looper;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageVolume;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.bx.carDVR.bylym.model.NotifyMessageManager;
import com.bx.carDVR.bylym.model.tools.CheckCamera;
import com.bx.carDVR.bylym.model.tools.DialogTool;
import com.bx.carDVR.bylym.model.tools.LocationManagerTool;
import com.bx.carDVR.bylym.model.tools.SharePreferenceTool;
import com.bx.carDVR.bylym.model.tools.ToastTool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class DVRService extends Service {
    public static final String LOG_TAG = "DVRService";

    public static final int FORMATTING_RST_SUCCESSFUL = 1;
    public static final int FORMATTING_RST_FAIL = -1;
    public static final int FORMATTING_RST_FAIL_CARD_INVALID = -2;
    public static final int FORMATTING_RST_FAIL_CURRENTLY_RECORDING = -3;

    private static final String mSignalStatePath = "/devices/virtual/switch/tvd_signal";//信号检测
    private static final String mSignalFormatPath = "/devices/virtual/switch/tvd_ispal";//制式检测
    private static final String mVideo0Path = "/video4linux/video0";//video0

    private DVRSrvBinder mBinder;
    private Thread mThread;
    private Handler mHandler;
    private HandlerThread mRecorderThread;
    private SharedPreferences mPreferences;
    private SettingInfo mSettingInfo;
    private int mSavingPathSet;
    private String mOutputFileRootSet;
    private StorageManager mStorageManager;
    private List<StorageDevice> mDeviceList;
    private List<Recorder> mRecorderList;
    private Timer mRecordingTimer;
    private boolean isAutoRecordingStarted;
    private int mRecordingCounter;
    private int mCurRecordingTime;
    private int[] mCurRecordingTimes = new int[Configuration.CAMERA_NUM];
    private List<OnAutoRecordingStateChangeListener> mAutoRecordingStateListeners;
    private List<OnCameraSignalStateChangeListener>[] mCameraSignalStateListeners;
    private List<Recorder.OnCameraConnectionStateChangeListener>[] mCameraConnectionStateListeners;
    private BroadcastReceiver mStorageDeviceConnectionReceiver;
    private BroadcastReceiver mCameraConnectionReceiver;
    private boolean isSetPathValid;
    private boolean isCameraStartup;
    private int mCameraSignalState;
    private List<Context> mRecordingComponents;
    public long lockTime;
    private SensorManager mSensorManager;
    public static final int SENSOR_LEVEL_HIGH = 28;// 20;
    public static final int SENSOR_LEVEL_MIDDLE = 38;// 30;
    public static final int SENSOR_LEVEL_LOW = 55;// 47;
    public static final int SENSOR_LEVEL_CLOSE = 100;// 47;
    private int timeInterval;
    protected StorageManager mStorage;
    protected DiskInfo mDisk;
    public static final String EXTRA_FORMAT_PRIVATE = "format_private";
    public static final String EXTRA_FORGET_UUID = "forget_uuid";
    private AlertDialog formatSDCardDialog;
    private Handler mainHandle;
    private DialogTool mDialogTool;
    private LocationManagerTool locationManagerTool;

    @Override
    public void onCreate() {
        mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        mThread = Thread.currentThread();
        mHandler = new Handler();
        mainHandle = new Handler(Looper.getMainLooper());
        mSettingInfo = new SettingInfo();
        mDialogTool = new DialogTool();
        mPreferences = SharePreferenceTool.getInstance().getSharedPreferences();
        this.mStorage = getSystemService(StorageManager.class);
        initStorageDevices();
        loadSettings();
        locationManagerTool = new LocationManagerTool();
        locationManagerTool.getLocation();
        createRecorders();
        onOutputFilePathChanged();

        //mStatusObserver.startObserving(mSignalStatePath);
        //mStatusObserver.startObserving(mSignalFormatPath);
        //mStatusObserver.startObserving(mVideo0Path);
        mCameraSignalState = checkCameraSignalState();

        mAutoRecordingStateListeners = new ArrayList<>();
        cameraStartup();

        IntentFilter filter = new IntentFilter();
//        filter.addAction(Define.ACTION_MCU_ACC);
        filter.addAction(Configuration.ACTION_CLOSE_DVR);
        filter.addAction(Configuration.ACTION_SET_DVR_RECORD_TIME);
        filter.addAction(Configuration.ACTION_SET_ADAS_LEVEL);
        filter.addAction(Configuration.ACTION_SET_G_SENSOR_LEVEL);
        filter.addAction(Configuration.ACTION_FORMAT_SD_CARD);
        mCameraConnectionReceiver = new CameraConnectionReceiver();
        registerReceiver(mCameraConnectionReceiver, filter);


        mBinder = new DVRSrvBinder();
        mRecordingComponents = new ArrayList<>();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(mSensorEventListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "Service destroy !");
        mStorageManager.unregisterListener(mStorageEventListener);
        //unregisterReceiver(mStorageDeviceConnectionReceiver);
        unregisterReceiver(mCameraConnectionReceiver);
        locationManagerTool.stopLocation();
        cameraShutdown();
        releaseRecorders();
        if (mSensorManager != null && mSensorEventListener != null) {
            mSensorManager.unregisterListener(mSensorEventListener);
            mSensorManager = null;
            mSensorEventListener = null;
        }
        //mStatusObserver.stopObserving();
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.

        onConnectService();

        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        onConnectService();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        onDisconnectService();

        return true;
    }

    private void onConnectService() {

        Log.d(LOG_TAG, "Service connected .");
    }

    private void onDisconnectService() {

        Log.d(LOG_TAG, "Service disconnected .");
    }

    private void createRecorders() {
        mRecorderThread = new HandlerThread("Recorder-Handler-Thread");
        mRecorderThread.start();
        mRecorderList = new ArrayList<>(Configuration.CAMERA_NUM);
        mCameraConnectionStateListeners = new List[Configuration.CAMERA_NUM];
        mCameraSignalStateListeners = new List[Configuration.CAMERA_NUM];

        for (int i = 0; i < Configuration.CAMERA_NUM; i++) {
            int cameraID = Configuration.CAMERA_IDS[i];
            Recorder recorder;

            RecorderParameters parameters = new RecorderParameters();
            if (Configuration.IS_USB_CAMERA[i]) {
                parameters.videoEncoder = MediaRecorder.VideoEncoder.H264;
            }

            if (Configuration.CAMERA2) {
                recorder = new Camera2Recorder(this, cameraID, parameters, mRecorderThread);
            } else {
                recorder = new CameraRecorder(cameraID, parameters, mRecorderThread);
            }

            mRecorderList.add(recorder);

            mCameraSignalStateListeners[i] = new ArrayList<>();
            mCameraConnectionStateListeners[i] = new ArrayList<>();

            final int idx = i;
            recorder.setOnCameraConnectionStateChangeListener(new Recorder.OnCameraConnectionStateChangeListener() {
                @Override
                public void onConnect() {
                    for (Recorder.OnCameraConnectionStateChangeListener listener :
                            mCameraConnectionStateListeners[idx]) {
                        listener.onConnect();
                    }
                }

                @Override
                public void onDisconnect() {
                    for (Recorder.OnCameraConnectionStateChangeListener listener :
                            mCameraConnectionStateListeners[idx]) {
                        listener.onDisconnect();
                    }
                }
            });
        }
    }

    private void releaseRecorders() {
        final HandlerThread fHandlerThread = mRecorderThread;
        final List<Recorder> fList = mRecorderList;
        new Thread() {
            @Override
            public void run() {
                boolean disconnected;
                do {
                    disconnected = true;
                    for (Recorder recorder : fList) {
                        if (recorder.getCameraConnectionState() != Recorder.CAMERA_DISCONNECTED) {
                            disconnected = false;
                            Log.d(LOG_TAG,
                                    "Wait camera disconnected , camera id " + recorder.getCameraID());
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {

                            }

                            break;
                        }
                    }
                } while (!disconnected);

                fHandlerThread.quitSafely();
            }
        }.start();
    }

    private void cameraStartup() {
        if (isCameraStartup) {
            return;
        }

        isCameraStartup = true;
        Log.d(LOG_TAG, "Camera startup.");

        for (Recorder recorder : mRecorderList) {
            recorder.connectCamera();
        }

        startAutoRecordingIfNecessary();
    }

    private void cameraShutdown() {
        if (!isCameraStartup) {
            return;
        }

        isCameraStartup = false;
        Log.d(LOG_TAG, "Camera shutdown.");

        autoRecordingStop();

        for (Recorder recorder : mRecorderList) {
            recorder.disconnectCamera();
        }
    }

    /*
    private UEventObserver mStatusObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String path = event.get("DEVPATH");
            Log.d(TAG, "UEvent dev path : " + path);

            if (mSignalStatePath.equals(path)) {
                String stateString = event.get("SWITCH_STATE");
                int intValue;
                try {
                    intValue = Integer.parseInt(stateString);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                Log.d(TAG, "Signal state is " +  intValue);

                final int state = intValue;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        int lastSignalState = mCameraSignalState;
                        mCameraSignalState = state;

                        for (int i = 0; i < mRecorderList.size(); i++) {
                            Recorder recorder = mRecorderList.get(i);
                            int stateBit;
                            switch (recorder.getCameraID()) {
                                case 4: {
                                    stateBit = 0;
                                } break;
                                case 5: {
                                    stateBit = 1;
                                } break;
                                case 6: {
                                    stateBit = 2;
                                } break;
                                case 7: {
                                    stateBit = 3;
                                } break;
                                default: {
                                    stateBit = -1;
                                }
                            }

                            if (stateBit == -1) {
                                continue;
                            }

                            int status = (mCameraSignalState >> stateBit) & 0x01;
                            int lastStatus = (lastSignalState >> stateBit) & 0x01;

                            if (status != lastStatus) {
                                if (status == 1) {
                                    for (OnCameraSignalStateChangeListener listener :
                                    mCameraSignalStateListeners[i]) {
                                        listener.onSignalGot();
                                    }
                                } else {
                                    for (OnCameraSignalStateChangeListener listener :
                                    mCameraSignalStateListeners[i]) {
                                        listener.onSignalLost();
                                    }
                                }
                            }
                        }
                    }
                });
            } else if (mSignalFormatPath.equals(path)) {
                Log.d(TAG, "Signal format change .");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Recorder recorder : mRecorderList) {
                            //recorder.disconnectCamera();
                            //recorder.connectCamera();
                        }
                    }
                });
            } else if (path.contains(mVideo0Path)) {
                String action = event.get("ACTION");
                if ("add".equals(action)) {
                    Log.d(TAG, path + " added .");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (Recorder recorder : mRecorderList) {
                                if (recorder.getCameraID() == 0) {
                                    recorder.connectCamera();
                                }
                            }
                        }
                    });
                } else if ("remove".equals(action)) {
                    Log.d(TAG, path + " removed .");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (Recorder recorder : mRecorderList) {
                                if (recorder.getCameraID() == 0) {
                                    recorder.disconnectCamera();
                                }
                            }
                        }
                    });
                }
            }
        }
    };
    */

    private int checkCameraSignalState() {
        byte[] buf = new byte[10];
        File file = new File("/sys/devices/virtual/switch/tvd_signal/state");

        try {
            InputStream inputStream = new FileInputStream(file);
            try {
                inputStream.read(buf);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        String stateString = new String(buf);
        int intValue = 1025;
        try {
            intValue = Integer.parseInt(stateString.trim());
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        Log.d(LOG_TAG, "Signal state is " + intValue);

        return intValue;
    }

    private boolean isCameraSignalNormal(int idx) {
        Recorder recorder = mRecorderList.get(idx);
        int stateBit;
        Log.d("aat", "isCameraSignalNormal:idx " + idx + " " + recorder.getCameraID());
        switch (recorder.getCameraID()) {
            case 4: {
                stateBit = 0;
            }
            break;
            case 5: {
                stateBit = 1;
            }
            break;
            case 6: {
                stateBit = 2;
            }
            break;
            case 7: {
                stateBit = 3;
            }
            break;
            default: {
                return true;
            }
        }

        int status = (mCameraSignalState >> stateBit) & 0x01;
        return status == 1;
    }

    private void cleanStorageSpace() {
        File file = new File(mOutputFileRootSet);
        long space = file.getUsableSpace() / 1024 / 1024;
        int deleteCount;

        if (space < 100) {
            deleteCount = 5;
        } else if (space < 300) {
            deleteCount = 4;
        } else if (space < 500) {
            deleteCount = 3;
        } else if (space < 700) {
            deleteCount = 2;
        } else if (space < 1000) {
            deleteCount = 1;
        } else {
            long total = file.getTotalSpace() / 1024 / 1024;
            if (total > 5 * 1024 && space < 10 * 1024 && space < total / 9) {
                deleteCount = 1;
            } else {
                deleteCount = 0;
            }
        }

        while (deleteCount-- > 0) {
            for (Recorder recorder : mRecorderList) {
                DVRFileList fileList = recorder.getFileList();
                if (fileList != null && fileList.deleteEarliestVideo()) {
                    Log.d(LOG_TAG, "The space may be not enough, delete a earliest video file.");
                } else {
                    Log.w(LOG_TAG, "Delete earliest video file fail !");
                }
            }
        }
    }

    private void autoRecordingStart(int time) {
        timeInterval = time;
        if (isAutoRecordingStarted) {
            return;
        }

        if (!isSetPathValid || !isCameraStartup) {
            return;
        }

        if (timeInterval < 30) {
            throw new IllegalArgumentException();
        }

        final int INTERVAL = 5;

        mRecordingTimer = new Timer();
//        mRecordingTimer.schedule(new TimerTask() {
//            private Runnable runnable = new Runnable() {
//                @Override
//                public void run() {
//                    if (!isAutoRecordingStarted) {
//                        return;
//                    }
//
//                    mRecordingCounter++;
//                }
//            };
//
//            @Override
//            public void run() {
//                mHandler.removeCallbacks(runnable);
//                mHandler.post(runnable);
//            }
//        }, 1000, 1000);

        for (int i = 0; i < mRecorderList.size(); i++) {
            final Recorder recorder = mRecorderList.get(i);
            final int idx = i;

            mRecordingTimer.schedule(new TimerTask() {
                private int counter = 0;
                private boolean started = false;
                private Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        if (!isAutoRecordingStarted) {
                            return;
                        }

                        if (counter > 0) {
                            counter--;

                            if (counter == timeInterval / 2) {
                                cleanStorageSpace();
                            }

                            if (started) {
                                mCurRecordingTimes[idx]++;
                                Log.d("testa", "run:1 " + mCurRecordingTimes[idx]);
                            }
                        } else {
//                            if (started && mRecordingCounter < INTERVAL) {
//                                mCurRecordingTimes[idx]++;
//                                Log.d("testa", "run:2 " + mCurRecordingTimes[idx]);
//                            } else {
                            if (started) {
                                //recorder.cameraStopRecording();
                                started = false;
                            }
                            Log.d(LOG_TAG, "run:auto ");
                            if (isCameraSignalNormal(idx)) {
                                if (recorder.cameraStartRecording() == Recorder.RECORDING_RST_SUCCESSFUL) {
                                    mRecordingCounter = 0;
                                    Log.d("aTAG", "run:cameraStartRecording ");
                                    started = true;
                                }
                            }
                            updateTime();
                            mCurRecordingTimes[idx] = 0;
                            counter = timeInterval;
                        }
                    }
//                    }
                };

                @Override
                public void run() {
                    mHandler.removeCallbacks(runnable);
                    mHandler.post(runnable);
                }
            }, 1200, 1000);

            mCurRecordingTimes[i] = 0;
        }

        mRecordingCounter = 0;
        mCurRecordingTime = 0;
        isAutoRecordingStarted = true;
        Log.d(LOG_TAG, "Auto recording started .");
        for (OnAutoRecordingStateChangeListener listener : mAutoRecordingStateListeners) {
            listener.onStart();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            String id = "MyService";
            String description = "my-service";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(id, description, importance);
            manager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, id)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();
            notification.flags = Notification.FLAG_ONGOING_EVENT;
            manager.notify(1, notification);
        } else {
            Notification notification = new Notification();
            notification.flags = Notification.FLAG_ONGOING_EVENT;
            notification.icon = R.mipmap.ic_launcher;
            notification.contentView = new RemoteViews(getPackageName(), R.layout.notification_bar);
            Intent intent = new Intent(this, CarDVRActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            notification.contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, notification);
        }
    }

    public void updateTime() {
        timeInterval = mSettingInfo.autoRecordingTime;
    }

    private void autoRecordingStop() {
        if (!isAutoRecordingStarted) {
            return;
        }

        for (Recorder recorder : mRecorderList) {
            recorder.cameraStopRecording();
        }

        mRecordingTimer.cancel();
        mRecordingTimer = null;
        mCurRecordingTime = 0;
        isAutoRecordingStarted = false;
        Log.d(LOG_TAG, "Auto recording stop .");

        for (OnAutoRecordingStateChangeListener listener : mAutoRecordingStateListeners) {
            listener.onStop();
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(1);
    }

    private void startAutoRecordingIfNecessary() {
        if (mSettingInfo.isAutoRecording) {
            Log.d("aa", "stopAutoRecordingIfNecessary: " + mSettingInfo.isAutoRecording + " " +
                    mSettingInfo.isRecordingBackstage);
//            if (mSettingInfo.isRecordingBackstage || mRecordingComponents.size() > 0) {
            if (mSettingInfo.isRecordingBackstage) {
                if (mRecorderList.get(0).isCameraOpen()) {
                    autoRecordingStart(mSettingInfo.autoRecordingTime);
                } else {
                    ToastTool.showToast(R.string.camera_not_found);
                }
            }
        }
    }

    private void stopAutoRecordingIfNecessary() {
        if (mSettingInfo.isAutoRecording == false
                || (mSettingInfo.isRecordingBackstage == false && mRecordingComponents.size() <= 0)) {
            autoRecordingStop();
        }
    }

    private void loadSettings() {
        if (mPreferences.getBoolean("Default", true) == true) {
            settingsToDefault();
        } else {
            Log.d("aa", "loadSettings: ");
//            mSettingInfo.isAutoRecording =
//                    mPreferences.getBoolean(SettingInfo.ITEM_AUTO_RECORDING, true);
            mSettingInfo.isAutoRecording = true;
            mSettingInfo.autoRecordingTime = mPreferences.getInt(SettingInfo.ITEM_RECORDING_TIME,
                    SettingInfo.TIME_INTERVAL_1_MINUTE);
            mSettingInfo.isRecordingBackstage =
                    mPreferences.getBoolean(SettingInfo.ITEM_RECORDING_BACKSTAGE, true);
            mSettingInfo.savingPath = mPreferences.getInt(SettingInfo.ITEM_FILE_PATH,
                    SettingInfo.PATH_SD);
        }
    }

    private void settingsToDefaultValue() {
        mSettingInfo.isAutoRecording = true;
        mSettingInfo.autoRecordingTime = SettingInfo.TIME_INTERVAL_1_MINUTE;
        mSettingInfo.isRecordingBackstage = true;
        mSettingInfo.savingPath = SettingInfo.PATH_SD;
    }

    private void settingsToDefault() {
        Log.d("TAG", "settingsToDefault: ");
        settingsToDefaultValue();
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean("Default", false);
        editor.putBoolean(SettingInfo.ITEM_AUTO_RECORDING, mSettingInfo.isAutoRecording);
        editor.putInt(SettingInfo.ITEM_RECORDING_TIME, mSettingInfo.autoRecordingTime);
        editor.putBoolean(SettingInfo.ITEM_RECORDING_BACKSTAGE, mSettingInfo.isRecordingBackstage);
        editor.putInt(SettingInfo.ITEM_FILE_PATH, mSettingInfo.savingPath);
        editor.apply();
    }

    private void saveSetting(String item) {
        SharedPreferences.Editor editor = mPreferences.edit();

        if (SettingInfo.ITEM_AUTO_RECORDING.equals(item)) {
            editor.putBoolean(SettingInfo.ITEM_AUTO_RECORDING, mSettingInfo.isAutoRecording);
        } else if (SettingInfo.ITEM_RECORDING_TIME.equals(item)) {
            editor.putInt(SettingInfo.ITEM_RECORDING_TIME, mSettingInfo.autoRecordingTime);
        } else if (SettingInfo.ITEM_RECORDING_BACKSTAGE.equals(item)) {
            editor.putBoolean(SettingInfo.ITEM_RECORDING_BACKSTAGE,
                    mSettingInfo.isRecordingBackstage);
        } else if (SettingInfo.ITEM_FILE_PATH.equals(item)) {
            editor.putInt(SettingInfo.ITEM_FILE_PATH, mSettingInfo.savingPath);
        } else {
            return;
        }

        editor.apply();
    }

    private void initStorageDevices() {
        mStorageManager.registerListener(mStorageEventListener);
        mDeviceList = new ArrayList<>();
        Log.d(LOG_TAG, "init storage device");
        for (StorageVolume storage : mStorageManager.getStorageVolumes()) {
            Log.d(LOG_TAG, storage.getDescription(this) + " found , path : " + storage.getPath());
            if (storage.getState().equals(Environment.MEDIA_MOUNTED)) {
                addStorageDevice(storage);
            }
        }
    }

    private StorageEventListener mStorageEventListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            if (newState.equals(Environment.MEDIA_MOUNTED)) {
                onStorageDeviceMount(path);
            } else {
                if (oldState.equals(Environment.MEDIA_MOUNTED)) {
                    onStorageDeviceRemove(path);
                }
            }
        }
    };

    private StorageDevice addStorageDevice(StorageVolume storage) {
        String path = storage.getPath();

        for (StorageDevice device : mDeviceList) {
            if (device.getPath().equals(path)) {
                return null;
            }
        }

        String description = storage.getDescription(this);
        int diskType;
        if (description.contains("SD")) {
            diskType = StorageDevice.TYPE_SD;
        } else if (description.contains("USB")) {
            diskType = StorageDevice.TYPE_USB;
        } else {
            diskType = StorageDevice.TYPE_OTHER;
        }

        StorageDevice device = new StorageDevice(diskType, description, path);
        mDeviceList.add(device);
        Log.d(LOG_TAG, "Add the device to list .");
        return device;
    }

    private StorageDevice removeStorageDevice(StorageVolume storage) {
        for (StorageDevice device : mDeviceList) {
            if (device.getPath().equals(storage.getPath())) {
                mDeviceList.remove(device);
                Log.d(LOG_TAG, "Remove the device form list .");
                return device;
            }
        }

        return null;
    }

    private void onStorageDeviceMount(String path) {
        Log.d(LOG_TAG, "Storage device mounted , path : " + path);
        for (StorageVolume storage : mStorageManager.getStorageVolumes()) {
            if (path.equals(storage.getPath())) {
                if (storage.getState().equals(Environment.MEDIA_MOUNTED)) {
                    StorageDevice device = addStorageDevice(storage);
                    if (device != null) {
                        if (isMatchingSetPath(device)) {
                            onOutputFilePathChanged();
                            startAutoRecordingIfNecessary();
                        }
                    }
                }
                break;
            }
        }
    }

    private void onStorageDeviceRemove(String path) {
        Log.d(LOG_TAG, "Storage device unmounted or removed , path : " + path);
        for (StorageVolume storage : mStorageManager.getStorageVolumes()) {
            if (path.equals(storage.getPath())) {
                if (!storage.getState().equals(Environment.MEDIA_MOUNTED)) {
                    StorageDevice device = removeStorageDevice(storage);
                    if (device != null) {
                        if (isMatchingSetPath(device)) {
                            autoRecordingStop();
                            onOutputFilePathChanged();
                        }
                    }
                }
                break;
            }
        }
    }

    private boolean isMatchingSetPath(StorageDevice device) {
        switch (mSavingPathSet) {
            case SettingInfo.PATH_SD:
                if (device.getDiskType() == StorageDevice.TYPE_SD) {
                    return true;
                }
                break;
            case SettingInfo.PATH_USB:
                if (device.getDiskType() == StorageDevice.TYPE_USB) {
                    return true;
                }
                break;
            default:
                if (device.getDiskType() != StorageDevice.TYPE_USB && device.getDiskType() != StorageDevice.TYPE_SD) {
                    return true;
                }
                break;
        }

        return false;
    }

    private boolean isMatchingSetPath(String path) {
        if (path == null || mOutputFileRootSet == null || !isSetPathValid) {
            return false;
        }

        return path.equals(mOutputFileRootSet);
    }

    private void onOutputFilePathChanged() {
        autoRecordingStop();
        mSavingPathSet = mSettingInfo.savingPath;

        for (StorageDevice device : mDeviceList) {
            if (isMatchingSetPath(device)) {
                Log.d(LOG_TAG, "Set valid output file path .");
                isSetPathValid = true;
                mOutputFileRootSet = device.getPath();
                for (int i = 0; i < mRecorderList.size(); i++) {
                    Recorder recorder = mRecorderList.get(i);
                    DVRFileInfo fileInfo = new DVRFileInfo();
                    fileInfo.outputFilePath = mOutputFileRootSet;
                    fileInfo.directoryID = i + 1;
                    recorder.onSetValidOutputFilePath(fileInfo);
                }
                return;
            }
        }

        for (Recorder recorder : mRecorderList) {
            recorder.onSetInvalidOutputFilePath();
        }

        isSetPathValid = false;
        mOutputFileRootSet = null;

        Log.d(LOG_TAG, "Set invalid output file path .");
    }

    private int startFormatting() {
        if (isAutoRecordingStarted) {
            return FORMATTING_RST_FAIL_CURRENTLY_RECORDING;
        }

        for (Recorder recorder : mRecorderList) {
//            if (!recorder.isOutputFilePathValid()) {
//                return FORMATTING_RST_FAIL_CARD_INVALID;
//            }
            if (recorder.isRecording()) {
                return FORMATTING_RST_FAIL_CURRENTLY_RECORDING;
            }
        }

        mDisk = mStorage.findDiskById("disk:179,64");
        if (mDisk == null) {
            return FORMATTING_RST_FAIL_CARD_INVALID;
        }
//        final Intent intent = new Intent(this, StorageWizardFormatProgress.class);
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.deviceinfo.StorageWizardFormatProgress");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.setComponent(cn);
        intent.putExtra(DiskInfo.EXTRA_DISK_ID, mDisk.getId());
        intent.putExtra(EXTRA_FORMAT_PRIVATE, false);
        intent.putExtra(EXTRA_FORGET_UUID, "");
        startActivity(intent);

//        StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
//        StorageVolume[] storageVolumes = storageManager.getVolumeList();
//
//        if (storageVolumes == null) {
//            return FORMATTING_RST_FAIL_CARD_INVALID;
//        }
//
//        for (StorageVolume volume : storageVolumes) {
//            if (isMatchingSetPath(volume.getPath())) {
//                Intent intent = new Intent("com.android.internal.os.storage.FORMAT_ONLY");
//                ComponentName formatter = new ComponentName("android", "com.android.internal.os.storage.ExternalStorageFormatter");
//                intent.setComponent(formatter);
//                intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, volume);
//                startService(intent);
//                Log.d(LOG_TAG, "Start formatting storage !");
//
//                return FORMATTING_RST_SUCCESSFUL;
//            }
//        }

        return FORMATTING_RST_SUCCESSFUL;
    }


    private void checkThread() {
        if (mThread != Thread.currentThread()) {
            throw new AndroidRuntimeException("Only main thread can call !");
        }
    }


    private class CameraConnectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
//            if (action.equals(Define.ACTION_MCU_ACC)) {
//                boolean on = intent.getBooleanExtra(Define.EXTRA_ACC, false);
//
//                if (on) {
//                    cameraStartup();
//                } else {
//                    cameraShutdown();
//                }
//            }
            Log.d(LOG_TAG, "onReceive:action " + action);
            switch (action) {
                case Configuration.ACTION_CLOSE_DVR:
                    stopSelf();
                    break;
                case Configuration.ACTION_SET_DVR_RECORD_TIME:
                    int time = intent.getIntExtra("record_time", 60);
                    mBinder.mSettingInterface.setRecordingTime(time);
                    Log.d(LOG_TAG, "onReceive:mSettingInfo.autoRecordingTime  " +
                            mSettingInfo.autoRecordingTime);
                    break;
                case Configuration.ACTION_SET_ADAS_LEVEL:
                    int level = intent.getIntExtra("adasLevel", 2);
                    SharePreferenceTool.getInstance().saveADASLevel(level);
                    break;
                case Configuration.ACTION_SET_G_SENSOR_LEVEL:
                    int sensorLevel = intent.getIntExtra("g_sensor", 2);
                    SharePreferenceTool.getInstance().saveGSensorLevel(sensorLevel);
                    break;
                case Configuration.ACTION_FORMAT_SD_CARD:
                    if (NotifyMessageManager.getInstance().showFormatDialogListener()) {
                        showFormatDialog(getApplicationContext());
                    } else {
                        NotifyMessageManager.getInstance().showFormatDialog();
                    }
                    break;
            }
        }
    }


    public interface OnAutoRecordingStateChangeListener {
        void onStart();

        void onStop();
    }

    public interface OnCameraSignalStateChangeListener {
        void onSignalGot();

        void onSignalLost();
    }

    public class RecorderInterface {
        public void cameraStartPreview(int idx, SurfaceHolder surfaceHolder) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            Recorder recorder = mRecorderList.get(idx);
            recorder.cameraStartPreview(surfaceHolder);
        }

        public void cameraStartPreview(int idx, SurfaceTexture surfaceTexture) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            Recorder recorder = mRecorderList.get(idx);
            recorder.cameraStartPreview(surfaceTexture);
        }

        public void cameraStopPreview(int idx) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            Recorder recorder = mRecorderList.get(idx);
            recorder.cameraStopPreview();
        }

        /**
         * @param idx      recorder index.
         * @param listener listen the result.
         * @see com.bx.carDVR.Recorder.OnTakePictureFinishListener
         */
        public void cameraTakePicture(int idx, Recorder.OnTakePictureFinishListener listener) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            Recorder recorder = mRecorderList.get(idx);
            recorder.cameraTakePicture(listener);
        }

        /**
         * @param idx recorder index.
         * @return
         * @see com.bx.carDVR.Recorder#RECORDING_RST_SUCCESSFUL
         * @see com.bx.carDVR.Recorder#RECORDING_RST_FAIL
         */
        public int cameraStartRecording(int idx) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

//            if (isAutoRecordingStarted) {
//                return Recorder.RECORDING_RST_FAIL_CURRENTLY_RECORDING;
//            }
            Log.d(LOG_TAG, "cameraStartRecording: ");
            if (!isCameraSignalNormal(idx)) {
                return Recorder.RECORDING_RST_FAIL;
            }

            Recorder recorder = mRecorderList.get(idx);
            return recorder.cameraStartRecording();
        }

        public void cameraStopRecording(int idx) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            if (isAutoRecordingStarted) {
                return;
            }

            Recorder recorder = mRecorderList.get(idx);
            recorder.cameraStopRecording();
        }

        public int getCameraConnectionState(int idx) {
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            Recorder recorder = mRecorderList.get(idx);
            return recorder.getCameraConnectionState();
        }

        public boolean cameraIsSignalNormal(int idx) {
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }
            Log.d(LOG_TAG, "cameraIsSignalNormal: ");
            return isCameraSignalNormal(idx);
        }

        public void registerOnCameraConnectionStateChangeListener(int idx,
                                                                  Recorder.OnCameraConnectionStateChangeListener listener) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            if (listener == null) {
                throw new NullPointerException();
            }

            if (!mCameraConnectionStateListeners[idx].contains(listener)) {
                mCameraConnectionStateListeners[idx].add(listener);
            } else {
                Log.w(LOG_TAG, "registerOnCameraConnectionStateChangeListener(), the listener has" +
                        " already registered !");
            }
        }

        public void unregisterOnCameraConnectionStateChangeListener(int idx,
                                                                    Recorder.OnCameraConnectionStateChangeListener listener) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            if (listener == null) {
                throw new NullPointerException();
            }

            if (mCameraConnectionStateListeners[idx].contains(listener)) {
                mCameraConnectionStateListeners[idx].remove(listener);
            } else {
                Log.w(LOG_TAG, "unregisterOnCameraConnectionStateChangeListener(), the listener " +
                        "has not registered yet !");
            }
        }

        public void registerOnCameraSignalStateChangeListener(int idx,
                                                              OnCameraSignalStateChangeListener listener) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            if (listener == null) {
                throw new NullPointerException();
            }

            if (!mCameraSignalStateListeners[idx].contains(listener)) {
                mCameraSignalStateListeners[idx].add(listener);
            } else {
                Log.w(LOG_TAG, "registerOnCameraSignalStateChangeListener(), the listener has " +
                        "already registered !");
            }
        }

        public void unregisterOnCameraSignalStateChangeListener(int idx,
                                                                OnCameraSignalStateChangeListener listener) {
            checkThread();
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            if (listener == null) {
                throw new NullPointerException();
            }

            if (mCameraSignalStateListeners[idx].contains(listener)) {
                mCameraSignalStateListeners[idx].remove(listener);
            } else {
                Log.w(LOG_TAG, "unregisterOnCameraSignalStateChangeListener(), the listener has " +
                        "not registered yet !");
            }
        }

        public int getCameraID(int idx) {
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            Recorder recorder = mRecorderList.get(idx);
            return recorder.getCameraID();
        }

        public DVRFileList getFileList(int idx) {
            if (idx < 0 || idx >= mRecorderList.size()) {
                throw new IllegalArgumentException();
            }

            Recorder recorder = mRecorderList.get(idx);
            return recorder.getFileList();
        }

        public void registerOnAutoRecordingStateChangeListener(OnAutoRecordingStateChangeListener listener) {
            checkThread();
            if (listener == null) {
                throw new NullPointerException();
            }

            if (!mAutoRecordingStateListeners.contains(listener)) {
                mAutoRecordingStateListeners.add(listener);
            } else {
                Log.w(LOG_TAG, "registerOnAutoRecordingStateChangeListener(), the listener has " +
                        "already registered !");
            }
        }

        public void unregisterOnAutoRecordingStateChangeListener(OnAutoRecordingStateChangeListener listener) {
            checkThread();
            if (listener == null) {
                throw new NullPointerException();
            }

            if (mAutoRecordingStateListeners.contains(listener)) {
                mAutoRecordingStateListeners.remove(listener);
            } else {
                Log.w(LOG_TAG, "unregisterOnAutoRecordingStateChangeListener(), the listener has " +
                        "not registered yet !");
            }
        }

        public boolean isCurrentlyAutoRecording() {
            return isAutoRecordingStarted;
        }

        public int getCurrentRecordingTime() {
            return mCurRecordingTime;
        }

        public int getCurrentRecordingTime(int idx) {
            return mCurRecordingTimes[idx];
        }

        public int getRecorderCount() {
            return mRecorderList.size();
        }

        public void lockFile() {
            lockTime = System.currentTimeMillis();
            for (int i = 0; i < Configuration.CAMERA_NUM; i++) {
                mRecorderList.get(i).lockCurrentRecordingFile(true, lockTime);
            }
            callPhone();
        }

        public boolean isOpenCamera(int cameraID) {
            int index = 0;
            for (int i = 0; i < mRecorderList.size(); i++) {
                if (mRecorderList.get(i).getCameraID() == cameraID) {
                    index = i;
                }
            }
            return mRecorderList.get(index).isCameraOpen();
        }

    }

    public class SettingInterface {
        public boolean settingIsAutoRecording() {
            return mSettingInfo.isAutoRecording;
        }

        public int settingGetRecordingTime() {
            return mSettingInfo.autoRecordingTime;
        }

        public boolean settingIsRecordingBackstage() {
            return mSettingInfo.isRecordingBackstage;
        }

        public int settingGetSavingPath() {
            return mSettingInfo.savingPath;
        }

        public boolean isDeviceMatchingSetPath(String devPath) {
            return isMatchingSetPath(devPath);
        }

        public void setAutoRecording(boolean on) {
            checkThread();
            if (on != mSettingInfo.isAutoRecording) {
                mSettingInfo.isAutoRecording = on;
                saveSetting(SettingInfo.ITEM_AUTO_RECORDING);

                if (on) {
                    startAutoRecordingIfNecessary();
                } else {
                    autoRecordingStop();
                }
            }
        }

        public void setRecordingTime(int time) {
            checkThread();
            if (time < SettingInfo.TIME_INTERVAL_1_MINUTE || time > SettingInfo.TIME_INTERVAL_MAX) {
                return;
            }

            if (time != mSettingInfo.autoRecordingTime) {
                mSettingInfo.autoRecordingTime = time;
                saveSetting(SettingInfo.ITEM_RECORDING_TIME);
            }
        }

        public void setRecordingBackstage(boolean on) {
            checkThread();
            if (on != mSettingInfo.isRecordingBackstage) {
                mSettingInfo.isRecordingBackstage = on;
                saveSetting(SettingInfo.ITEM_RECORDING_BACKSTAGE);
            }
        }

        public void setSavingPath(int path) {
            checkThread();
            if (path != mSettingInfo.savingPath) {
                mSettingInfo.savingPath = path;
                saveSetting(SettingInfo.ITEM_FILE_PATH);
                onOutputFilePathChanged();
                startAutoRecordingIfNecessary();
            }
        }

        public void settingRestoreToDefault() {
            checkThread();
            boolean autoRecording = mSettingInfo.isAutoRecording;
            int path = mSettingInfo.savingPath;

            settingsToDefault();

            if (path != mSettingInfo.savingPath) {
                onOutputFilePathChanged();
            }

            if (autoRecording != mSettingInfo.isAutoRecording) {
                if (mSettingInfo.isAutoRecording) {
                    startAutoRecordingIfNecessary();
                } else {
                    autoRecordingStop();
                }
            }
        }

        /**
         * @return <li>{@link #FORMATTING_RST_SUCCESSFUL}
         * <li>{@link #FORMATTING_RST_FAIL}
         */
        public int formatStorage() {
            checkThread();
            return startFormatting();
        }

        public void stopAutoRecording() {
            autoRecordingStop();
        }
    }


    private void formatCard() {
        int strId = -1;
        int result = startFormatting();
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

    public class DVRSrvBinder extends Binder {
        private SettingInterface mSettingInterface = new SettingInterface();
        private RecorderInterface mRecorderInterface = new RecorderInterface();

        public SettingInterface getSettingInterface() {
            return mSettingInterface;
        }

        public RecorderInterface getRecorderInterface() {
            return mRecorderInterface;
        }

        public void notifyRecordingComponent(Context componentContext) {
            checkThread();
            if (!mRecordingComponents.contains(componentContext)) {
                mRecordingComponents.add(componentContext);
                startAutoRecordingIfNecessary();
            }
        }

        public void cancelRecordingComponent(Context componentContext) {
            checkThread();
            if (mRecordingComponents.contains(componentContext)) {
                mRecordingComponents.remove(componentContext);
                stopAutoRecordingIfNecessary();
            }
        }
    }

    private void releaseC() {
        for (int i = 0; i < Configuration.CAMERA_NUM; i++) {
            mRecorderList.get(i).lockCurrentRecordingFile(true, lockTime);
        }
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float threshold = SENSOR_LEVEL_MIDDLE;
                float xlateral = sensorEvent.values[0];
                float ylongitudinal = sensorEvent.values[1];
                float zvertical = sensorEvent.values[2];
                int g_sensorLockLevel = SharePreferenceTool.getInstance().getGSensorLevel();
                if (g_sensorLockLevel == 0) {
                    threshold = SENSOR_LEVEL_CLOSE;
                } else if (g_sensorLockLevel == 1) {
                    threshold = SENSOR_LEVEL_LOW;
                } else if (g_sensorLockLevel == 2) {
                    threshold = SENSOR_LEVEL_MIDDLE;
                } else {
                    threshold = SENSOR_LEVEL_HIGH;
                }
                if ((xlateral > threshold) || (ylongitudinal > threshold) || (zvertical > threshold)) {
                    Log.d(LOG_TAG,
                            "heading=" + xlateral + ", pitch=" + ylongitudinal + "," +
                                    " roll=" + zvertical + ", " + "threshold=" + threshold);
                    mBinder.mRecorderInterface.lockFile();
                    callPhone();
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public void callPhone() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        startActivity(intent);
//        Intent intent = new Intent(Intent.ACTION_CALL);
//        Uri data = Uri.parse("tel:" + "15302731185");
//        intent.setData(data);
        startActivity(intent);
    }

    public void showFormatDialog(Context context) {
        if (formatSDCardDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.dialog_title_format);
            builder.setMessage(R.string.dialog_message_format_sd_card);
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    formatSDCardDialog.dismiss();
                }
            });
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    formatSDCardDialog.dismiss();
                    formatCard();
                }
            });
            formatSDCardDialog = builder.create();
            formatSDCardDialog.getWindow().setType(
                    (WindowManager.LayoutParams.TYPE_SYSTEM_ALERT));
        }
        if (!formatSDCardDialog.isShowing()) {
            mainHandle.post(new Runnable() {
                @Override
                public void run() {
//                    mDialogTool.focusNotAle(formatSDCardDialog.getWindow());
                    formatSDCardDialog.show();
//                    mDialogTool.hideNavigationBar(formatSDCardDialog.getWindow());
//                    mDialogTool.clearFocusNotAle(formatSDCardDialog.getWindow());
                }
            });
        }
    }

}
