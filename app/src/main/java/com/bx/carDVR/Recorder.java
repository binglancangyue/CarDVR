package com.bx.carDVR;

import android.graphics.SurfaceTexture;
import android.media.MediaRecorder;
import android.view.Surface;
import android.view.SurfaceHolder;

public interface Recorder {
    int CAMERA_DISCONNECTED = 0;
    int CAMERA_CONNECTED = 1;
    int CAMERA_CONNECTING = 2;
    int CAMERA_DISCONNECTING = 3;

    int TAKE_PIC_RST_SUCCESSFUL = 1;
    int TAKE_PIC_RST_FAIL = -1;
    int TAKE_PIC_RST_FAIL_NO_CAMERA = -2;
    int TAKE_PIC_RST_FAIL_CARD_INVALID = -3;
    int TAKE_PIC_RST_FAIL_CURRENTLY_RECORDING = -4;

    int RECORDING_RST_SUCCESSFUL = 1;
    int RECORDING_RST_FAIL = -1;
    int RECORDING_RST_FAIL_NO_CAMERA = -2;
    int RECORDING_RST_FAIL_CARD_INVALID = -3;
    int RECORDING_RST_FAIL_CURRENTLY_RECORDING = -4;


    interface OnCameraConnectionStateChangeListener {
        void onConnect();
        void onDisconnect();
    }

    interface OnTakePictureFinishListener {
        /**
         *
         * @param result take picture result.
         *               <li>{@link #TAKE_PIC_RST_SUCCESSFUL}
         *               <li>{@link #TAKE_PIC_RST_FAIL}
         */
        void onFinish(int result,String photoPath);
    }

    void connectCamera();
    void disconnectCamera();
    void cameraStartPreview(SurfaceHolder surfaceHolder);
    void cameraStartPreview(SurfaceTexture surfaceTexture);
    void cameraStopPreview();
    void cameraTakePicture(OnTakePictureFinishListener listener);
    int cameraStartRecording();
    void cameraStopRecording();

    /**
     *
     * @return state
     * <li>{@link #CAMERA_CONNECTED}
     * <li>{@link #CAMERA_DISCONNECTED}
     */
    int getCameraConnectionState();
    void setOnCameraConnectionStateChangeListener(OnCameraConnectionStateChangeListener listener);

    int getCameraID();
    DVRFileList getFileList();
    void lockCurrentRecordingFile(boolean b,long time);

    void onSetValidOutputFilePath(final DVRFileInfo fileInfo);
    void onSetInvalidOutputFilePath();
    boolean isOutputFilePathValid();

    boolean isRecording();
    boolean isPreviewing();
    boolean isCameraOpen();
}

class RecorderParameters {
    int outputFormat = MediaRecorder.OutputFormat.MPEG_4;
    int videoEncoder = MediaRecorder.VideoEncoder.DEFAULT;
    int videoWidth = VIDEO_WIDTH_DEFAULT;
    int videoHeight = VIDEO_HEIGHT_DEFAULT;
    int videoFrameRate = 24;
    int videoEncodingBitRate = 10 * 100 * 1024;
    int maxDurationMs = 10 * 1000;
    int pictureWidth = VIDEO_WIDTH_DEFAULT;
    int pictureHeight = VIDEO_HEIGHT_DEFAULT;

    public static final int VIDEO_WIDTH_DEFAULT = 1280;
    public static final int VIDEO_HEIGHT_DEFAULT = 720;
}
