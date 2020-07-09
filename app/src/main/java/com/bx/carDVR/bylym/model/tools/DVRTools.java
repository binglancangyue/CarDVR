package com.bx.carDVR.bylym.model.tools;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.bx.carDVR.DvrApplication;
import com.bx.carDVR.bylym.video.Mp4ParseUtil;
import com.bx.carDVR.bylym.video.VideoClip;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * @author Altair
 * @date :2020.04.25 下午 05:18
 * @description:
 */
public class DVRTools {
    private static final String TAG = DVRTools.class.getName();
    public ArrayList<String> frontFileList = new ArrayList<>();
    private static final String removeString = "_";
    private Context mContext;
    public static final int MSG_FRONT_VID_SCRP_SUCCESS = 1034;
    public static final int MSG_TO_MERGE_VIDEO = 1035;
    public static final int MSG_VID_SEND_ERROR = 1032;
    public static final int MSG_VID_THREAD2_SUCCESS_FRONT = 1033;
    final String frontoutPath0_1 = Environment.getExternalStorageDirectory().getPath() +
            "/out0front";
    final String frontoutPath1_1 = Environment.getExternalStorageDirectory().getPath() +
            "/out1front";

    String frontoutPath0 = frontoutPath0_1;
    String frontoutPath1 = frontoutPath1_1;
    final String outFileFront = Environment.getExternalStorageDirectory().getPath() + "/";

    public static final String RESULT_PICTURE = "VIDEO_CALL_WE_TAKE_PHOTO_RESULT";
    public static final String RESULT_VIDEO = "VIDEO_CALL_WE_TAKE_VIDEO_RESULT";
    private VideoClip videoClip;
    private List<String> mergeVideoList = new ArrayList<>();
    private String mergeVideoPath;
    private static final int CUT_BEFORE_DURATION = 15;
    private static final int CUT_AFTER_DURATION = 15;
    private static final int CUT_TOTAL_DURATION = 30;
    private static int RECORD_TIME = 60;
    private CompositeDisposable compositeDisposable;
    private String cameraFacing;

    public DVRTools(Context mContext) {
        this.mContext = mContext;
        this.compositeDisposable = new CompositeDisposable();
        videoClip = new VideoClip();

    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TO_MERGE_VIDEO) {
                mergeVideo();
            }
        }
    };

    /**
     * 获取加锁触发时间和当前视频开始时间的值
     *
     * @param time 加锁触发时间
     * @param num  视频录制时间
     * @return
     */
    private long calculateTime(long time, long num) {
        // TODO Auto-generated method stub

        if (time == -1 || num == -1) {
            return -1;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Date before = null;
        Date now = null;
        try {
            before = sdf.parse(String.valueOf(num));
            now = sdf.parse(String.valueOf(time));

        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        long l = now.getTime() - before.getTime();
        long day = l / (24 * 60 * 60 * 1000);
        long hour = (l / (60 * 60 * 1000) - day * 24);
        long min = ((l / (60 * 1000)) - day * 24 * 60 - hour * 60);
        long s = (l / 1000 - day * 24 * 60 * 60 - hour * 60 * 60);

        Log.i("hy", "min:" + min);

        return s;
    }

    /**
     * 添加前后2个连续视频文件
     *
     * @param finalName
     */
    public void tianji(String finalName) {
//        if (finalName.contains("Front_1min")) {
        Log.d(TAG, "tianji:finalName " + finalName);
        if (frontFileList.size() == 2) {
            frontFileList.remove(0);
            frontFileList.add(finalName);
        } else {
            frontFileList.add(finalName);
        }
        if (!isContinuousfile(frontFileList)) {
            frontFileList.remove(0);
            Log.e("hy", "isContinuousFile ::" + frontFileList);
        }
        Log.i("hy", "frontfilelist:" + frontFileList);
//        }
    }

    /**
     * 是否是连续录制文件
     *
     * @param list
     * @return
     */
    public boolean isContinuousfile(final ArrayList<String> list) {
        String substring0 = null;
        String substring1 = null;
        long num0 = -1;
        long num1 = -1;
        if (list.size() == 1) {
            String file0 = list.get(0);
        }
        if (list.size() == 2) {
            String file0 = list.get(0);
            String file1 = list.get(1);
            if (list.get(0).contains("Back")) {
                if (list.get(0).contains("impact")) {
                    substring0 = list.get(0).substring(list.get(0).length() - 36,
                            list.get(0).length() - 21);
                } else {

                    substring0 = list.get(0).substring(list.get(0).length() - 29,
                            list.get(0).length() - 14);
                }
            } else {
                if (list.get(0).contains("impact")) {
                    substring0 = list.get(0).substring(list.get(0).length() - 37,
                            list.get(0).length() - 22);
                } else {
                    substring0 = list.get(0).substring(list.get(0).length() - 30,
                            list.get(0).length() - 15);
                }
            }

            String string0 = substring0.replace(removeString, "");
            num0 = Long.parseLong(string0);

            if (list.get(1).contains("Back")) {
                if (list.get(1).contains("impact")) {
                    substring1 = list.get(1).substring(list.get(1).length() - 36,
                            list.get(1).length() - 21);
                } else {

                    substring1 = list.get(1).substring(list.get(1).length() - 29,
                            list.get(1).length() - 14);
                }
            } else {
                if (list.get(1).contains("impact")) {
                    substring1 = list.get(1).substring(list.get(1).length() - 37,
                            list.get(1).length() - 22);
                } else {
                    substring1 = list.get(1).substring(list.get(1).length() - 30,
                            list.get(1).length() - 15);
                }
            }

            String string1 = substring1.replace(removeString, "");
            num1 = Long.parseLong(string1);
            long reduce = calculateTime(num1, num0) - getDuration(list.get(0));
            if (reduce >= -3 && reduce <= 3) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取视频文件时间
     *
     * @param pt
     * @return
     */
    private int getDuration(String pt) {
//        MediaPlayer mp = MediaPlayer.create(mContext, Uri.parse(pt));
//        if (mp != null) {
//            int duration = mp.getDuration();
//            mp.release();
//            duration /= 1000;
//
//            return duration;
//        }

        return getLocalVideoDuration(pt);
    }

    public void fileClip(long time, final ArrayList<String> list, final String outPath) {
        Log.d(TAG, "list:" + list + " size " + list.size());

        if (list.size() == 1) {// 判断录制的文件为第一个文件
            String substring = null;

            String fileName = list.get(0);

            substring = getTimeByFileName(fileName);
            String string = substring.replace(removeString, "");
            long num = Long.parseLong(string);
            String filename = getMergeVideoName(time);
            time = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(time));
            final int subTime = (int) calculateTime(time, num);
            final int videoDuration = getDuration(list.get(0));
            if (subTime <= CUT_BEFORE_DURATION) {// 从头开始切割
                cutVideo(getVideoClip(0, CUT_TOTAL_DURATION, list.get(0), outPath, filename,
                        true, false));
                Log.d(TAG, "cutVideo size1 : 0-" + CUT_TOTAL_DURATION);
            } else if (subTime > CUT_BEFORE_DURATION && subTime <= (videoDuration - CUT_AFTER_DURATION)) {//
                cutVideo(getVideoClip(subTime - CUT_BEFORE_DURATION, subTime + CUT_AFTER_DURATION, list.get(0),
                        outPath, filename, true, false));
                Log.d(TAG, "cutVideo size1 :subTime < videoDuration-" + CUT_AFTER_DURATION);
            } else {
                Log.d(TAG, "MSG_VID_SEND_ERROR size1 subTime>videoDuration-" + CUT_AFTER_DURATION);
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_VID_SEND_ERROR), 0);
            }
        } else {
            // 判断录制的文件为第二个文件
            String substring = null;
            for (int i = 0; i < list.size(); i++) {
                substring = getTimeByFileName(list.get(i));
                String string = substring.replace(removeString, "");
                long num = Long.parseLong(string);
                long lockTime = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(time));
                final int subTime = (int) calculateTime(lockTime, num);
                if (i == 1) {
                    int videoDuration = getDuration(list.get(1));
                    if (subTime >= CUT_BEFORE_DURATION && subTime <= (videoDuration - CUT_BEFORE_DURATION)) {
                        String filename = getMergeVideoName(time);
                        Log.d(TAG,
                                "cutVideo size2:subtime " + subTime + " filename " + filename);
                        cutVideo(getVideoClip(subTime - CUT_BEFORE_DURATION, subTime + CUT_AFTER_DURATION,
                                list.get(i), outPath, filename, true, false));
                        Log.d(TAG, "cutVideo size2 videoDuration -" + CUT_BEFORE_DURATION);
                    } else if (subTime < CUT_BEFORE_DURATION && subTime >= 0) {
//                        new Thread() {
//                            @Override
//                            public void run() {
                        String filename = getMergeVideoName(list.get(0));
                        frontoutPath0 = outPath + filename;
                        mergeVideoList.add(outPath + "/" + filename);
                        int videoTime = getDuration(list.get(0));
                        Log.d(TAG,
                                "cutVideo run: 截取第一个视频 " + filename + " videoTime " + videoTime);

                        cutVideo(getVideoClip(videoTime - CUT_BEFORE_DURATION + subTime, videoTime,
                                list.get(0), outPath, filename, false, false));

//                            }
//                        }.start();

                        long finalTime = time;
//                        Thread thread1 = new Thread() {
//                            @Override
//                            public void run() {
//                                String filename = list.get(1).substring(list.get(1).
//                                        lastIndexOf("/") + 1, list.get(1).lastIndexOf("."));
                        String fileName = getMergeVideoName(list.get(1));
                        mergeVideoPath = outPath + "/" + getMergeVideoName(finalTime);
//                                filename = filename + "_impact" + ".mp4";
                        Log.d(TAG, "run: mergeVideoPath " + mergeVideoPath);
                        Log.d(TAG, "cutVideo size2 run filename: " + fileName);
                        frontoutPath1 = outPath + fileName;
                        Log.d(TAG,
                                "cutVideo size2 run: 截取第二个视频 " + fileName + " time " + getDuration(list.get(1))
                                        + " subtime " + subTime);
                        mergeVideoList.add(outPath + "/" + fileName);
                        cutVideo(getVideoClip(0, subTime + CUT_AFTER_DURATION, list.get(1), outPath,
                                fileName, false, true));
//                            }
//                        };
//                        thread1.start();
                    } else {
                        Log.e(TAG, "MSG_VID_SEND_ERROR:cutVideo size2" + subTime);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_VID_SEND_ERROR), 0);
                    }
                }
            }
        }
    }

    private String getMergeVideoName(long time) {
        String filename = new SimpleDateFormat("yyyyMMdd_HHmmss").format(time);
        Log.d(TAG, "getMergeVideoName:filename " + filename);
        filename = filename + cameraFacing + "_" + CUT_TOTAL_DURATION + "s" + "_impact" + ".mp4";
        Log.d(TAG, "getMergeVideoName: " + filename);
        return filename;
    }

    private String getMergeVideoName(String file) {
        String filename = file.substring(file.lastIndexOf("/") + 1, file.lastIndexOf("."));
        filename = filename + "_impact" + ".mp4";
        return filename;
    }

    @SuppressLint("CheckResult")
    public void toFileClip(long time, final ArrayList<String> list, final String outPath) {
        compositeDisposable.add(Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                fileClip(time, list, outPath);
                emitter.onNext(1);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) throws Exception {

                    }
                }));
    }

    @SuppressLint("CheckResult")
    public void cutVideo(VideoClip videoClip) {
        compositeDisposable.add(Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
//                cutMp4(startTime, endTime, FilePath, WorkingPath, fileName, isTrue, emitter);
                cutMp4(videoClip, emitter);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String fileName) throws Exception {
                        Log.d(TAG, "cutVideo accept: " + fileName);
                        if (videoClip.isSendBroadcast() && !fileName.equals("null")) {
                            sendBroadcast("1", RESULT_VIDEO, fileName, true);
                        }
                        if (videoClip.isTheLastVideo()) {
                            mHandler.sendEmptyMessage(MSG_TO_MERGE_VIDEO);
                        }
                    }
                }));
    }

    private void mergeVideo() {
        compositeDisposable.add(Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                Mp4ParseUtil.appendMp4List(mergeVideoList, mergeVideoPath, emitter);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String fileName) throws Exception {
                        Log.d(TAG, "mergeVideo accept: " + fileName);
                        if (!fileName.equals("null")) {
                            sendBroadcast("2", RESULT_VIDEO, fileName, true);
                        }
                    }
                }));
    }

    /**
     * 视频剪切
     *
     * @param startTime      视频剪切的开始时间
     * @param endTime        视频剪切的结束时间
     * @param FilePath       被剪切视频的路径
     * @param WorkingPath    剪切成功保存的视频路径
     * @param fileName       剪切成功保存的文件名
     * @param isTrue         是否发送上传广播
     * @param isTheLastVideo 是否是合并视频的最后一个时候
     */
    private VideoClip getVideoClip(final long startTime, final long endTime, final String FilePath,
                                   final String WorkingPath, final String fileName, final Boolean isTrue
            , final Boolean isTheLastVideo) {
        //视频剪切
        VideoClip videoClip = new VideoClip();//实例化VideoClip类
        videoClip.setFilePath(FilePath);//设置被编辑视频的文件路径  FileUtil.getMediaDir()
        // +"/test/laoma3.mp4"
        videoClip.setWorkingPath(WorkingPath);//设置被编辑的视频输出路径  FileUtil.getMediaDir()
        videoClip.setStartTime(startTime * 1000);//设置剪辑开始的时间
        videoClip.setEndTime(endTime * 1000);//设置剪辑结束的时间
        videoClip.setOutName(fileName);//设置输出的文件名称
        videoClip.setSendBroadcast(isTrue);//是否发送上传广播
        videoClip.setTheLastVideo(isTheLastVideo);//是否是合并视频的最后一个时候
        return videoClip;
    }


    private synchronized void cutMp4(VideoClip info, ObservableEmitter<String> emitter) {
        VideoClip videoClip = info;
        videoClip.clip(emitter);
    }
/*    private synchronized void cutMp4(final long startTime, final long endTime, final String FilePath,
                                     final String WorkingPath, final String fileName, final Boolean isTrue
            , ObservableEmitter<String> emitter) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
        try {
            //视频剪切
            VideoClip videoClip = new VideoClip();//实例化VideoClip类
            videoClip.setFilePath(FilePath);//设置被编辑视频的文件路径  FileUtil.getMediaDir()
            // +"/test/laoma3.mp4"
            videoClip.setWorkingPath(WorkingPath);//设置被编辑的视频输出路径  FileUtil.getMediaDir()
            videoClip.setStartTime(startTime * 1000);//设置剪辑开始的时间
            videoClip.setEndTime(endTime * 1000);//设置剪辑结束的时间
            videoClip.setOutName(fileName);//设置输出的文件名称
            videoClip.setSendBroadcast(isTrue);
            videoClip.clip(emitter);//调用剪辑并保存视频文件方法（建议作为点击保存时的操作并加入等待对话框）

        } catch (Exception e) {
            e.printStackTrace();
        }
//            }
//        }).start();
    }*/


    private int getLocalVideoDuration(String videoPath) {
        int duration;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(videoPath);
            duration = Integer.parseInt(mmr.extractMetadata
                    (MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
        Log.d(TAG, "getLocalVideoDuration:duration " + duration);
        return duration / 1000;
    }

    public void sendBroadcast(String msgId, String resultType, String path, boolean successful) {
        Intent intent = new Intent();
        intent.setAction("com.android.zqc.send");
//        intent.setComponent(new ComponentName("com.zsi.powervideo",
//                "com.zsi.powervideo.recevier.MyReceiver"));
        intent.addFlags(0x01000000);
        intent.putExtra("ecarSendKey", resultType);
        intent.putExtra("result", successful);
        intent.putExtra("msgid", msgId);
        intent.putExtra("filePath", path);
        DvrApplication.getInstance().sendBroadcast(intent);
        Log.d(TAG, "sendBroadcastToActivity ");
    }

    public void release() {
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
            compositeDisposable.clear();
            compositeDisposable = null;
        }
        mContext = null;
    }

    private String getTimeByFileName(String fileName) {
        int length = fileName.length();
        String substring;
        if (fileName.contains("Back")) {
            cameraFacing = "_Back";
            if (fileName.contains("impact")) {
                substring = fileName.substring(length - 36, length - 21);
            } else {
                substring = fileName.substring(length - 29, length - 14);
            }
        } else {
            cameraFacing = "_Front";
            if (fileName.contains("impact")) {
                substring = fileName.substring(length - 37, length - 22);
            } else {
                substring = fileName.substring(length - 30, length - 15);
            }
        }
        return substring;
    }

}
