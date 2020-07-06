package com.bx.carDVR;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.bx.carDVR.bylym.model.tools.DVRTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class Camera2Recorder implements Recorder {
    public static final String LOG_TAG = "DVR-Recorder";

    private CameraManager mCameraManager;
    private int mCameraID;
    private CameraDevice mCamera;
    private MediaRecorder mRecorder;
    private Surface mUserPreviewSurface;
    private PreviewSurface mPreviewSurfaceForRecord;
    private ImageReader mImageReader;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mTakingPicRequest;
    private RecorderParameters mRecorderParameters;
    private int mCameraConnectionState;
    private OnCameraConnectionStateChangeListener mCameraConnectionStateListener;
    private int mConnectCameraRetry;
    private DVRFileList mFileList;
    private Handler mEventHandler;
    private Handler mHandler;
    private int mRunnableCnt;
    private long mRecorderStartTime;
    private boolean mRecordingStarted;

    private boolean isPreviewing;
    private boolean isTakingPic;
    private boolean isRecording;
    private File mRecordingFile;
    private OnTakePictureFinishListener mOnTakePictureFinishListener;
    private DVRTools dvrTools;
    private String outPath;
    private String currentVideoPath;
    private String photoPath;
    private boolean isLock = false;
    private long lockTime;
    public boolean cameraIsOpen = false;


    public Camera2Recorder(Context context, int cameraID, RecorderParameters parameters,
                           HandlerThread handlerThread) {
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mCameraConnectionState = CAMERA_DISCONNECTED;
        mCameraID = cameraID;
        mRecorderParameters = parameters;
        mEventHandler = new Handler();
        dvrTools = new DVRTools(context);
        createHandler(handlerThread);
    }

    private void createHandler(HandlerThread handlerThread) {
        Looper looper;

        do {
            looper = handlerThread.getLooper();
        } while (looper == null);

        mHandler = new Handler(looper);
        mRunnableCnt = 0;
    }

    private void runAtHandlerThread(final Runnable r) {
        Log.d(LOG_TAG, "Post to HandlerThread , camera id " + mCameraID);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mHandler) {
                    mRunnableCnt--;
                }

                Log.d(LOG_TAG, "Run at HandlerThread , in , camera id " + mCameraID);
                r.run();
                Log.d(LOG_TAG, "Run at HandlerThread , out , camera id " + mCameraID);
            }
        });

        synchronized (mHandler) {
            if (mRunnableCnt > 0) {
                Log.d(LOG_TAG, "HandlerThread busy , mRunnableCnt = " + mRunnableCnt + " , camera" +
                        " id " + mCameraID);
            }
            mRunnableCnt++;
        }
    }

    private void runRecorder(Runnable r) {
        runAtHandlerThread(r);
    }

    @Override

    public void connectCamera() {
        if (mCameraConnectionState == CAMERA_DISCONNECTED) {
            runRecorder(new Runnable() {
                @Override
                public void run() {
                    mConnectCameraRetry = 10;
                    doConnectingCamera();
                }
            });
        }
    }

    @Override
    public void disconnectCamera() {
        if (mCameraConnectionState == CAMERA_CONNECTED) {
            if (isRecording) {
                stopRecording();
            }

            runRecorder(new Runnable() {
                @Override
                public void run() {
                    mHandler.removeCallbacks(autoConnectingCamera);
                    doDisconnectingCamera();
                }
            });
        }
        releaseCompositeDisposable();
    }

    private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(LOG_TAG, "DVR open camera ID " + mCameraID + " , ok !");
            cameraIsOpen = true;
            mCamera = camera;
            mConnectCameraRetry = 10;
            correctRecorderOutputSize();
            initMediaRecorder();
            initImageReader();
            createPreviewSurfaceForRecord();

            mEventHandler.post(new Runnable() {
                @Override
                public void run() {
                    onCameraConnected();
                }
            });
        }

        @Override
        public void onClosed(CameraDevice camera) {
            mCamera = null;
            cameraIsOpen = false;
            releasePreviewSurfaceForRecord();
            mEventHandler.post(new Runnable() {
                @Override
                public void run() {
                    onCameraDisconnected();
                }
            });
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraIsOpen = false;
            mConnectCameraRetry = 10;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.w(LOG_TAG, "DVR open camera ID " + mCameraID + " , fail ! error=" + error);
            cameraIsOpen = false;
            camera.close();
            if (mConnectCameraRetry-- > 0) {
                mHandler.removeCallbacks(autoConnectingCamera);
                mHandler.postDelayed(autoConnectingCamera, 3000);
            }
        }
    };

    private Runnable autoConnectingCamera = new Runnable() {
        @Override
        public void run() {
            doConnectingCamera();
        }
    };

    private void doConnectingCamera() {
        Log.d(LOG_TAG, "DVR connecting to camera " + mCameraID);

        mEventHandler.post(new Runnable() {
            @Override
            public void run() {
                mCameraConnectionState = CAMERA_CONNECTING;
            }
        });

        try {
            mCameraManager.openCamera(String.valueOf(mCameraID), mCameraStateCallback, mHandler);
        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void doDisconnectingCamera() {
        mEventHandler.post(new Runnable() {
            @Override
            public void run() {
                mCameraConnectionState = CAMERA_DISCONNECTING;
            }
        });

        if (mRecorder != null) {
            mRecorder.setOnErrorListener(null);
            mRecorder.release();
            mRecorder = null;
        }
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
    }

    private void onCameraConnected() {
        Log.d(LOG_TAG, "Camera connected , ID " + mCameraID);

        isPreviewing = false;
        isTakingPic = false;
        isRecording = false;

        if (mCameraConnectionState != CAMERA_CONNECTED) {
            mCameraConnectionState = CAMERA_CONNECTED;
            if (mCameraConnectionStateListener != null) {
                mCameraConnectionStateListener.onConnect();
            }
        }
    }

    private void onCameraDisconnected() {
        Log.d(LOG_TAG, "Camera disconnected , ID " + mCameraID);

        isPreviewing = false;
        isTakingPic = false;
        isRecording = false;

        if (mCameraConnectionState != CAMERA_DISCONNECTED) {
            mCameraConnectionState = CAMERA_DISCONNECTED;
            if (mCameraConnectionStateListener != null) {
                mCameraConnectionStateListener.onDisconnect();
            }
        }
    }

    @Override
    public void onSetValidOutputFilePath(final DVRFileInfo fileInfo) {
        Log.d(LOG_TAG, "Valid output file path , camera id " + mCameraID);

        mFileList = new DVRFileList(fileInfo,mCameraID);
    }

    @Override
    public void onSetInvalidOutputFilePath() {
        Log.d(LOG_TAG, "Invalid output file path , camera id " + mCameraID);

        mFileList = null;

        if (isRecording) {
            stopRecording();
        }
    }

    private void initMediaRecorder() {
        mRecordingStarted = false;
        mRecorder = new MediaRecorder();
        mRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                if (what == MediaRecorder.MEDIA_ERROR_SERVER_DIED) {
                    Log.e(LOG_TAG, "Media server died , camera id " + mCameraID);
                    mr.release();
                    initMediaRecorder();
                }
            }
        });
    }

    private void createPreviewSurfaceForRecord() {
        if (mPreviewSurfaceForRecord == null) {
            mPreviewSurfaceForRecord = new PreviewSurface(32, 24);
        }
    }

    private void releasePreviewSurfaceForRecord() {
        if (mPreviewSurfaceForRecord != null) {
            mPreviewSurfaceForRecord.destroy();
            mPreviewSurfaceForRecord = null;
        }
    }

    private void initImageReader() {
        mImageReader = ImageReader.newInstance(
                mRecorderParameters.videoWidth, mRecorderParameters.videoHeight,
                ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(LOG_TAG, "Image available, camera id " + mCameraID);
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                onJpegTaken(bytes, mOnTakePictureFinishListener);
                image.close();
            }
        }, mEventHandler);
    }

    private void correctRecorderOutputSize() {
        try {
            StreamConfigurationMap config =
                    mCameraManager.getCameraCharacteristics(String.valueOf(mCameraID)).
                            get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (config == null) {
                return;
            }

            Size[] sizes = config.getOutputSizes(ImageFormat.JPEG);
            if (sizes != null) {
                Size size = sizes[0];
                mRecorderParameters.pictureWidth = size.getWidth();
                mRecorderParameters.pictureHeight = size.getHeight();
                Log.d(LOG_TAG,
                        "Camera supported picture size is " + size.getWidth() + "X" + size.getHeight() + ", camera id " + mCameraID);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session,
                                             CaptureRequest request, long timestamp,
                                             long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }
            };
    private CameraCaptureSession.CaptureCallback mRecordCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session,
                                             CaptureRequest request, long timestamp,
                                             long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);

                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);


                }
            };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCaptureSession(String name) {
        Log.d("ag", "createCaptureSession:name " + name);
        final Surface previewingSurface = mUserPreviewSurface != null ? mUserPreviewSurface :
                mPreviewSurfaceForRecord.getSurface();
        final Surface recordSurface = mRecorder.getSurface();
        final Surface takingPicSurface = mImageReader.getSurface();
        List<Surface> surfaces = new ArrayList<>();

        surfaces.add(previewingSurface);
        surfaces.add(recordSurface);
        surfaces.add(takingPicSurface);

        CameraCaptureSession.StateCallback callback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                if (mCamera != null) {
                    if (previewingSurface == mUserPreviewSurface || previewingSurface == mPreviewSurfaceForRecord.getSurface()) {
                        mCaptureSession = session;

                        try {
                            CaptureRequest.Builder builder =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                            builder.addTarget(previewingSurface);
                            builder.addTarget(recordSurface);
                            CaptureRequest request = builder.build();
                            session.setRepeatingRequest(request, mRecordCaptureCallback, mHandler);

                            builder =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
                            builder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
                            builder.addTarget(takingPicSurface);
                            mTakingPicRequest = builder.build();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                        try {
                            startRecorder();
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Log.e(LOG_TAG, "Camera capture session configure failed, camera id " + mCameraID);
            }
        };

        mCaptureSession = null;
        mTakingPicRequest = null;

        try {
            mCamera.createCaptureSession(surfaces, callback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCaptureSessionForPreview() {
        final Surface previewingSurface = mUserPreviewSurface;
        final Surface takingPicSurface = mImageReader.getSurface();
        List<Surface> surfaces = new ArrayList<>();

        surfaces.add(previewingSurface);
        surfaces.add(takingPicSurface);

        CameraCaptureSession.StateCallback callback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                if (mCamera != null) {
                    if (previewingSurface == mUserPreviewSurface) {
                        mCaptureSession = session;

                        try {
                            CaptureRequest.Builder builder =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(previewingSurface);
                            CaptureRequest request = builder.build();
                            session.setRepeatingRequest(request, mCaptureCallback, mHandler);

                            builder =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            builder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
                            builder.addTarget(takingPicSurface);
                            mTakingPicRequest = builder.build();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Log.e(LOG_TAG, "Camera capture session configure failed, camera id " + mCameraID);
            }
        };

        mCaptureSession = null;
        mTakingPicRequest = null;

        try {
            Log.d(LOG_TAG, "createCaptureSessionForPreview:camerId " + mCamera.getId());
            mCamera.createCaptureSession(surfaces, callback, mHandler);
        } catch (Exception e) {
//            e.printStackTrace();
            Log.e(LOG_TAG, "CameraAccessException: " + e.getMessage() + " " + e);
        }
    }

    private void closeCaptureSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            mTakingPicRequest = null;
        }
    }

    private void prepareRecorder(File outputFile) {
        try {
            Log.d("test", "prepareRecorder: outputFile " + outputFile.getAbsolutePath());
            mRecorder.reset();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER); //设置用于录制的音源
            mRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mRecorder.setOutputFormat(mRecorderParameters.outputFormat);
            mRecorder.setOutputFile(outputFile.getAbsolutePath());
            //设置audio的编码格式
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setVideoEncoder(mRecorderParameters.videoEncoder);
            mRecorder.setVideoSize(mRecorderParameters.videoWidth, mRecorderParameters.videoHeight);
            mRecorder.setVideoFrameRate(mRecorderParameters.videoFrameRate);
            mRecorder.setVideoEncodingBitRate(mRecorderParameters.videoEncodingBitRate);
            mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    Log.d("info", "onInfo:what " + what + " extra " + extra);
                }

            });

            Log.d("test", "prepareRecorder:setOnInfoListener ");
            mRecorder.prepare();

//            MyFileObserver fb = new MyFileObserver("/storage/0000-0000/DVR-BX/1/video/20180101/");
//            Log.d(LOG_TAG, "addFilePath prepareRecorder: " );
//            fb.startWatching();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startRecorder() {
        if (mRecordingStarted) {
            return;
        }

        try {
            mRecorder.start();
            mRecordingStarted = true;
            mRecorderStartTime = System.nanoTime();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void stopRecorder() {
        Log.d("aa", "aaaac stopRecorder: ");
        if (!mRecordingStarted) {
            return;
        }

        mRecordingStarted = false;

        while (System.nanoTime() - mRecorderStartTime < 200000000) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {

            }
        }

        try {
            mRecorder.stop();
            dvrTools.tianji(currentVideoPath);
            if (isLock) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("aadfd", "run: lockTime " + lockTime);
                        dvrTools.toFileClip(lockTime, dvrTools.frontFileList, outPath);
                        isLock = false;
                    }
                }, 6000);
            }


        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private int startRecording() {
        Log.d("aa", "aaaac startRecording: ");
        int result;

        do {
            if (mCameraConnectionState != CAMERA_CONNECTED) {
                result = RECORDING_RST_FAIL_NO_CAMERA;
                break;
            }

            if (isRecording) {
                //result = RECORDING_RST_FAIL_CURRENTLY_RECORDING;
                //break;
            }

            if (mFileList == null || !isOutputFilePathValid()) {
                result = RECORDING_RST_FAIL_CARD_INVALID;
                break;
            }


            if (!isRecording) {
                runRecorder(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            Log.d(LOG_TAG, "Start recording , camera id " + mCameraID);
//                            mCamera.startWaterMark(); //开始在图像上显示时间日期

                            try {
                                Log.d("test", "aaaac: newVideoFile");
                                final File file = mFileList.newVideoFile();
                                currentVideoPath = file.getAbsolutePath();
                                outPath = currentVideoPath.substring(0,
                                        currentVideoPath.lastIndexOf("/"));
                                Log.d("test", "startRecording:path " + currentVideoPath + " " +
                                        "outPath " + outPath);
                                if (file == null) {
//                                    result = RECORDING_RST_FAIL_CARD_INVALID;
                                    Log.w(LOG_TAG, "Create a video file fail !");
//                                    return RECORDING_RST_FAIL_CARD_INVALID;
                                }
                                prepareRecorder(file);
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                                return;
                            }

                            createCaptureSession("startRecording");
                        }
                    }
                });
            } else {
                runRecorder(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            Log.d(LOG_TAG, "Restart recording , camera id " + mCameraID);

                            try {
                                stopRecorder();
                                Log.d("test", "aaaac: newVideoFile1");
                                final File file = mFileList.newVideoFile();
                                currentVideoPath = file.getAbsolutePath();
                                outPath = currentVideoPath.substring(0,
                                        currentVideoPath.lastIndexOf("/"));
                                Log.d("test", "startRecording:path " + currentVideoPath + " " +
                                        "outPath " + outPath);
                                prepareRecorder(file);
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                                return;
                            }

                            createCaptureSession("stopRecorder");
                        }
                    }
                });
            }

//            mRecordingFile = file;
            isRecording = true;
            result = RECORDING_RST_SUCCESSFUL;
        } while (false);

        return result;
    }

    private void stopRecording() {
        if (mCameraConnectionState != CAMERA_CONNECTED) {
            return;
        }

        if (!isRecording) {
            return;
        }

        runRecorder(new Runnable() {
            @Override
            public void run() {
                if (mCamera != null) {
                    Log.d(LOG_TAG, "Stop recording , camera id " + mCameraID);

                    try {
                        stopRecorder();
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }

                    closeCaptureSession();

                    if (mUserPreviewSurface != null) {
                        runRecorder(new Runnable() {
                            @Override
                            public void run() {
                                if (mCamera != null) {
                                    if (mUserPreviewSurface != null && mCaptureSession == null) {
                                        createCaptureSessionForPreview();
                                    }
                                }
                            }
                        });
                    }
                }
            }
        });

        isRecording = false;
    }

    private void takePicture(final OnTakePictureFinishListener listener) {
        boolean takingPic = false;
        int result = TAKE_PIC_RST_FAIL;

        do {
            if (mCameraConnectionState != CAMERA_CONNECTED) {
                result = TAKE_PIC_RST_FAIL_NO_CAMERA;
                break;
            }

            if (mFileList == null || !isOutputFilePathValid()) {
                result = TAKE_PIC_RST_FAIL_CARD_INVALID;
                break;
            }

            if (isPreviewing == false) {
                result = TAKE_PIC_RST_FAIL;
                break;
            }

            if (isRecording == true) {
                //result = TAKE_PIC_RST_FAIL_CURRENTLY_RECORDING;
                //break;
            }

            if (isTakingPic == true) {
                result = TAKE_PIC_RST_FAIL;
                break;
            }

            takingPic = true;
            isTakingPic = true;
            mOnTakePictureFinishListener = listener;

            runRecorder(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        Log.d(LOG_TAG, "Take picture , camera id " + mCameraID);
                        if (mCaptureSession != null && mTakingPicRequest != null) {
                            try {
                                mCaptureSession.capture(mTakingPicRequest, mCaptureCallback,
                                        mHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        } while (false);

        if (listener != null && !takingPic) {
            listener.onFinish(result, null);
        }
    }

    private void onJpegTaken(byte[] data, OnTakePictureFinishListener listener) {
        int result = TAKE_PIC_RST_FAIL;
        photoPath = null;

        do {
            if (isTakingPic == false) {
                result = TAKE_PIC_RST_FAIL;
                break;
            }

            isTakingPic = false;

            if (mCameraConnectionState != CAMERA_CONNECTED) {
                result = TAKE_PIC_RST_FAIL_NO_CAMERA;
                break;
            }

            if (mFileList == null || !isOutputFilePathValid()) {
                result = TAKE_PIC_RST_FAIL_CARD_INVALID;
                break;
            }

            File file = mFileList.newPictureFile();
            if (file != null) {
                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(file);
                    outputStream.write(data);
                    result = TAKE_PIC_RST_SUCCESSFUL;
                    photoPath = file.getAbsolutePath();
                } catch (IOException e) {
                    result = TAKE_PIC_RST_FAIL;
                    e.printStackTrace();
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            result = TAKE_PIC_RST_FAIL;
                        }
                    }
                }
            } else {
                result = TAKE_PIC_RST_FAIL_CARD_INVALID;
                Log.w(LOG_TAG, "Create a picture file fail !");
            }
        } while (false);

        if (listener != null) {
            listener.onFinish(result, photoPath);
        }
    }

    @Override
    public void cameraStartPreview(final SurfaceHolder surfaceHolder) {
        if (mCameraConnectionState != CAMERA_CONNECTED) {
            return;
        }

        Log.d(LOG_TAG, "Camera start preview , camera id " + mCameraID);

        runRecorder(new Runnable() {
            @Override
            public void run() {
                if (mCamera != null) {
                    Surface surface = surfaceHolder.getSurface();
                    if (surface != null && surface.isValid()) {
                        mUserPreviewSurface = surface;
                        if (mRecordingStarted) {
                            createCaptureSession("cameraStartPreview");
                        } else {
                            createCaptureSessionForPreview();
                        }
                    }
                }
            }
        });

        isPreviewing = true;
    }

    @Override
    public void cameraStartPreview(final SurfaceTexture surfaceTexture) {
        if (mCameraConnectionState != CAMERA_CONNECTED) {
            return;
        }

        Log.d(LOG_TAG, "Camera start preview , camera id " + mCameraID);

        runRecorder(new Runnable() {
            @Override
            public void run() {
                if (mCamera != null) {
                    if (surfaceTexture != null && !surfaceTexture.isReleased()) {
                        mUserPreviewSurface = new Surface(surfaceTexture);
                        if (mRecordingStarted) {
                            createCaptureSession("cameraStartPreview");
                        } else {
                            createCaptureSessionForPreview();
                        }
                    }
                }
            }
        });

        isPreviewing = true;
    }

    @Override
    public void cameraStopPreview() {
        if (mCameraConnectionState != CAMERA_CONNECTED) {
            return;
        }

        if (isPreviewing) {
            isPreviewing = false;
            Log.d(LOG_TAG, "Camera stop preview , camera id " + mCameraID);

            runRecorder(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        if (mUserPreviewSurface != null) {
                            mUserPreviewSurface = null;
                            if (mRecordingStarted) {
                                createCaptureSession("cameraStopPreview");
                            } else {
                                closeCaptureSession();
                            }
                        }
                    }
                }
            });
        }
    }


    @Override
    public void cameraTakePicture(OnTakePictureFinishListener listener) {
        takePicture(listener);
    }

    @Override
    public int cameraStartRecording() {
        return startRecording();
    }

    @Override
    public void cameraStopRecording() {
        stopRecording();
    }

    @Override
    public int getCameraConnectionState() {
        return mCameraConnectionState;
    }

    @Override
    public void setOnCameraConnectionStateChangeListener(OnCameraConnectionStateChangeListener listener) {
        mCameraConnectionStateListener = listener;
    }

    @Override
    public int getCameraID() {
        return mCameraID;
    }

    @Override
    public DVRFileList getFileList() {
        return mFileList;
    }

    @Override
    public void lockCurrentRecordingFile(boolean b, long time) {
        isLock = b;
        lockTime = time;
        Log.d("test", "lockCurrentRecordingFile: " + currentVideoPath);
    }

    @Override
    public boolean isOutputFilePathValid() {
        return (mFileList != null);
    }

    @Override
    public boolean isRecording() {
        return isRecording;
    }

    @Override
    public boolean isPreviewing() {
        return isPreviewing;
    }

    @Override
    public boolean isCameraOpen() {
        return cameraIsOpen;
    }

    private void releaseCompositeDisposable() {
        dvrTools.release();
    }

}