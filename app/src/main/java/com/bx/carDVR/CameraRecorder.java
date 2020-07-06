package com.bx.carDVR;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;


public class CameraRecorder implements Recorder {
    public static final String LOG_TAG = "DVR-Recorder";

    private int mCameraID;
    private Camera mCamera;
    private MediaRecorder mRecorder;
    private RecorderParameters mRecorderParameters;
    private int mCameraConnectionState;
    private OnCameraConnectionStateChangeListener mCameraConnectionStateListener;
    private int mConnectCameraRetry;
    private DVRFileList mFileList;
    private Handler mEventHandler;
    private Handler mHandler;
    private int mRunnableCnt;
    private long mRecorderStartTime;

    private boolean isPreviewing;
    private boolean isTakingPic;
    private boolean isRecording;
    private File mRecordingFile;


    public CameraRecorder(int cameraID, RecorderParameters parameters, HandlerThread handlerThread) {
        mCameraConnectionState = CAMERA_DISCONNECTED;
        mCameraID = cameraID;
        mRecorderParameters = parameters;
        mEventHandler = new Handler();
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
                Log.d(LOG_TAG, "HandlerThread busy , mRunnableCnt = " + mRunnableCnt + " , camera id " + mCameraID);
            }
            mRunnableCnt++;
        }
    }

    @Override
    public void connectCamera() {
        if (mCameraConnectionState == CAMERA_DISCONNECTED) {
            runAtHandlerThread(new Runnable() {
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

            runAtHandlerThread(new Runnable() {
                @Override
                public void run() {
                    mEventHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onCameraDisconnected();
                        }
                    });

                    mHandler.removeCallbacks(autoConnectingCamera);
                    doDisconnectingCamera();
                }
            });
        }
    }

    private Runnable autoConnectingCamera = new Runnable() {
        @Override
        public void run() {
            doConnectingCamera();
        }
    };

    private void doConnectingCamera() {
        if (mCamera == null) {
            try {
                mCamera = Camera.open(mCameraID);
                Log.d(LOG_TAG, "DVR open camera ID " + mCameraID + " , ok !");
            } catch (RuntimeException e) {
                mCamera = null;
                Log.w(LOG_TAG, "DVR open camera ID " + mCameraID + " , fail !");
            }

            if (mCamera != null) {
                mCamera.setErrorCallback(new Camera.ErrorCallback() {
                    @Override
                    public void onError(int error, Camera camera) {
                        if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                            Log.e(LOG_TAG, "Camera server died , ID " + mCameraID);
                            if (mCamera != null) {
                                mHandler.removeCallbacks(autoConnectingCamera);
                                mHandler.postDelayed(autoConnectingCamera, 3000);
                                mConnectCameraRetry = 10;
                            }

                            mEventHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    onCameraDisconnected();
                                }
                            });

                            doDisconnectingCamera();
                        }
                    }
                });

                initMediaRecorder();
                correctRecorderVideoSize();
                setCameraGeneralParameter();

                mEventHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onCameraConnected();
                    }
                });
            } else {
                if (mConnectCameraRetry-- > 0) {
                    mHandler.postDelayed(autoConnectingCamera, 3000);
                }
            }
        }
    }

    private void doDisconnectingCamera() {
        if (mRecorder != null) {
            mRecorder.setOnErrorListener(null);
            mRecorder.release();
            mRecorder = null;
        }
        if (mCamera != null) {
            mCamera.setErrorCallback(null);
            mCamera.release();
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

    private void correctRecorderVideoSize() {
        if (mCamera != null) {
            try {
                List<Camera.Size> sizes = mCamera.getParameters().getSupportedVideoSizes();
                Iterator<Camera.Size> iterator = sizes.iterator();

                if (iterator.hasNext()) {
                    Camera.Size size = iterator.next();
                    mRecorderParameters.videoWidth = size.width;
                    mRecorderParameters.videoHeight = size.height;
                    Log.d(LOG_TAG, "Camera supported video size is " + size.width + "X" + size.height + ", camera id " + mCameraID);
                } else {
                    mRecorderParameters.videoWidth = RecorderParameters.VIDEO_WIDTH_DEFAULT;
                    mRecorderParameters.videoHeight = RecorderParameters.VIDEO_HEIGHT_DEFAULT;
                }

                CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                if (profile != null) {
                    mRecorderParameters.videoFrameRate = profile.videoFrameRate;
                    Log.d(LOG_TAG, "Camera supported video frame rate is " + profile.videoFrameRate + " bps");
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private void setCameraGeneralParameter() {
        if (mCamera != null) {
            try {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPictureFormat(ImageFormat.JPEG);
                parameters.setJpegQuality(85);
                parameters.setPictureSize(mRecorderParameters.videoWidth, mRecorderParameters.videoHeight);
                mCamera.setParameters(parameters);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private void initMediaRecorder() {
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

    private int startRecording() {
        int result;

        do {
            if (mCameraConnectionState != CAMERA_CONNECTED) {
                result = RECORDING_RST_FAIL_NO_CAMERA;
                break;
            }

            if (isRecording) {
                result = RECORDING_RST_FAIL_CURRENTLY_RECORDING;
                break;
            }

            if (mFileList == null || !isOutputFilePathValid()) {
                result = RECORDING_RST_FAIL_CARD_INVALID;
                break;
            }

            final File file = mFileList.newVideoFile();
            if (file == null) {
                result = RECORDING_RST_FAIL_CARD_INVALID;
                Log.w(LOG_TAG, "Create a video file fail !");
                break;
            }

            runAtHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        Log.d(LOG_TAG, "Start recording , camera id " + mCameraID);
                        //mCamera.startWaterMark(); //开始在图像上显示时间日期

                        try {
                            mCamera.unlock();
                            mRecorder.reset();
                            mRecorder.setCamera(mCamera);
                            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                            mRecorder.setOutputFormat(mRecorderParameters.outputFormat);
                            mRecorder.setOutputFile(file.getAbsolutePath());
                            mRecorder.setVideoEncoder(mRecorderParameters.videoEncoder);
                            mRecorder.setVideoSize(mRecorderParameters.videoWidth, mRecorderParameters.videoHeight);
                            mRecorder.setVideoFrameRate(mRecorderParameters.videoFrameRate);
                            mRecorder.setVideoEncodingBitRate(mRecorderParameters.videoEncodingBitRate);
                            mRecorder.prepare();
                            mRecorder.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }

                        mRecorderStartTime = System.nanoTime();
                    }
                }
            });

            if (!isPreviewing) {
                runAtHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            //mCamera.stopRender();
                        }
                    }
                });
            }

            mRecordingFile = file;
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

        runAtHandlerThread(new Runnable() {
            @Override
            public void run() {
                if (mRecorder != null) {
                    Log.d(LOG_TAG, "Stop recording , camera id " + mCameraID);

                    while (System.nanoTime() - mRecorderStartTime < 200000000) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {

                        }
                    }

                    try {
                        mRecorder.stop();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }

                    //mCamera.stopWaterMark();
                }
            }
        });

        isRecording = false;
    }

    private void onJpegTaken(byte[] data, OnTakePictureFinishListener listener) {
        int result = TAKE_PIC_RST_FAIL;

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
            listener.onFinish(result,null);
        }
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

            final Handler fHandler = new Handler();

            runAtHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        Log.d(LOG_TAG, "Take picture , camera id " + mCameraID);
                        try {
                            mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
                                @Override
                                public void onPictureTaken(final byte[] data, Camera camera) {
                                    Log.d(LOG_TAG, "Taking picture callback , camera id " + mCameraID);
                                    mEventHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            onJpegTaken(data, listener);
                                            fHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (isPreviewing) {
                                                        if (false) {//if (isRecording) {
                                                            runAtHandlerThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    if (mCamera != null) {
                                                                        //mCamera.startRender();
                                                                    }
                                                                }
                                                            });
                                                        } else {
                                                            runAtHandlerThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    if (mCamera != null) {
                                                                        try {
                                                                            mCamera.startPreview();
                                                                        } catch (RuntimeException e) {
                                                                            e.printStackTrace();
                                                                        }
                                                                    }
                                                                }
                                                            });
                                                        }
                                                    }
                                                }
                                            });
                                        }
                                    });

                                }
                            });
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } while (false);

        if (listener != null && !takingPic) {
            listener.onFinish(result, null);
        }
    }

    @Override
    public void cameraStartPreview(final SurfaceHolder surfaceHolder) {
        if (mCameraConnectionState != CAMERA_CONNECTED) {
            return;
        }

        if (isPreviewing) {
            if (false) {//(isRecording) {
                runAtHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            //mCamera.stopRender();
                        }
                    }
                });
            } else {
                runAtHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            try {
                                mCamera.stopPreview();
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        }

        isPreviewing = true;
        Log.d(LOG_TAG, "Camera start preview , camera id " + mCameraID);

        if (false) {//if (isRecording) {
            runAtHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        //mCamera.startRender();
                    }
                }
            });
        } else {
            runAtHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        try {
                            mCamera.setPreviewDisplay(surfaceHolder);
                            mCamera.startPreview();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void cameraStartPreview(final SurfaceTexture surfaceTexture) {
        if (mCameraConnectionState != CAMERA_CONNECTED) {
            return;
        }

        if (isPreviewing) {
            if (false) {//(isRecording) {
                runAtHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            //mCamera.stopRender();
                        }
                    }
                });
            } else {
                runAtHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            try {
                                mCamera.stopPreview();
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        }

        isPreviewing = true;
        Log.d(LOG_TAG, "Camera start preview , camera id " + mCameraID);

        if (false) {//if (isRecording) {
            runAtHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        //mCamera.startRender();
                    }
                }
            });
        } else {
            runAtHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        try {
                            mCamera.setPreviewTexture(surfaceTexture);
                            mCamera.startPreview();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void cameraStopPreview() {
        if (mCameraConnectionState != CAMERA_CONNECTED) {
            return;
        }

        if (isPreviewing) {
            isPreviewing = false;
            if (isTakingPic) {
                isTakingPic = false;
            }

            Log.d(LOG_TAG, "Camera stop preview , camera id " + mCameraID);
            if (false) {//if (isRecording) {
                runAtHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            //mCamera.stopRender();
                        }
                    }
                });
            } else {
                runAtHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null) {
                            try {
                                mCamera.stopPreview();
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
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
    public void lockCurrentRecordingFile( boolean b,long time) {
        if (isRecording) {
            mFileList.lockFile(mRecordingFile.getAbsolutePath());
        }
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
        return false;
    }
}