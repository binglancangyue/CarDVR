package com.bx.carDVR.bylym.video;

import android.content.Intent;
import android.util.Log;

import com.bx.carDVR.Configuration;
import com.bx.carDVR.DvrApplication;
import com.bx.carDVR.bylym.model.tools.DVRTools;
import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

import io.reactivex.ObservableEmitter;

/**
 * @author Altair
 * @date :2020.04.29 下午 03:34
 * @description:
 */
public class VideoClip {
    private static final String TAG = "VideoClip";
    private String filePath;//视频路径
    private String workingPath;//输出路径
    private String outName;//输出文件名
    private double startTime;//剪切起始时间
    private double endTime;//剪切结束时间
    private boolean isSendBroadcast;//发送上传广播
    private boolean isTheLastVideo;//合并视频文件的最后一个视频

    public boolean isTheLastVideo() {
        return isTheLastVideo;
    }

    public void setTheLastVideo(boolean theLastVideo) {
        isTheLastVideo = theLastVideo;
    }

    public boolean isSendBroadcast() {
        return isSendBroadcast;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }


    public void setWorkingPath(String workingPath) {
        this.workingPath = workingPath;
    }

    public void setOutName(String outName) {
        this.outName = outName;
    }

    public void setEndTime(double endTime) {
        this.endTime = endTime / 1000;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime / 1000;
    }

    public void setSendBroadcast(boolean b) {
        this.isSendBroadcast = b;
    }


    public synchronized void clip(ObservableEmitter<String> emitter) {
        try {
            //将要剪辑的视频文件
//            MetaBox metaBox = new MetaBox();
//            VisualSampleEntry visualSampleEntry=new VisualSampleEntry();
            Movie movie = MovieCreator.build(filePath);

            List<Track> tracks = movie.getTracks();
            movie.setTracks(new LinkedList<Track>());
            //时间是否修正
            boolean timeCorrected = false;

            //计算并换算剪切时间
            for (Track track : tracks) {
                if (track.getSyncSamples() != null
                        && track.getSyncSamples().length > 0) {
                    if (timeCorrected) {
                        throw new RuntimeException(
                                "The startTime has already been corrected by another track with " +
                                        "SyncSample. Not Supported.");
                    }
                    //true,false表示短截取；false,true表示长截取
                    startTime = VideoHelper.correctTimeToSyncSample(track, startTime, false);
                    //修正后的开始时间
                    endTime = VideoHelper.correctTimeToSyncSample(track, endTime, true);
                    //修正后的结束时间
                    timeCorrected = true;
                }
            }
            Log.d(TAG,
                    "clip: startTime " + startTime + " endTime " + endTime + " filePath " + filePath);
            //根据换算到的开始时间和结束时间来截取视频
            for (Track track : tracks) {
                long currentSample = 0; //视频截取到的当前的位置的时间
                double currentTime = 0; //视频的时间长度
                double lastTime = -1;    //上次截取到的最后的时间
                long startSample1 = -1;  //截取开始的时间
                long endSample1 = -1;    //截取结束的时间

                //设置开始剪辑的时间和结束剪辑的时间  避免超出视频总长
                for (int i = 0; i < track.getSampleDurations().length; i++) {
                    long delta = track.getSampleDurations()[i];
                    if (currentTime > lastTime && currentTime <= startTime) {
                        startSample1 = currentSample;//编辑开始的时间
                    }
                    if (currentTime > lastTime && currentTime <= endTime) {
                        endSample1 = currentSample;  //编辑结束的时间
                    }
                    lastTime = currentTime;          //上次截取到的时间（避免在视频最后位置了还在增加编辑结束的时间）
                    currentTime += (double) delta
                            / (double) track.getTrackMetaData().getTimescale();//视频的时间长度
                    currentSample++;                 //当前位置+1
                }
                Log.d(TAG, "clip:startSample1 " + startSample1 + " endSample1 " + endSample1);
                movie.addTrack(new CroppedTrack(track, startSample1, endSample1));// 创建一个新的视频文件
            }

            //合成视频mp4
            Container out = new DefaultMp4Builder().build(movie);
            File storagePath = new File(workingPath);
            storagePath.mkdirs();
            File video = new File(storagePath, outName);
            FileOutputStream fos = new FileOutputStream(video);
            Log.d(TAG, "clip: storagePath " + storagePath + " outName " + outName);
            FileChannel fco = fos.getChannel();
            out.writeContainer(fco);
            //关闭流
            fco.close();
            fos.close();
            emitter.onNext(video.getAbsolutePath());
            Log.d(TAG, "clip: over " + outName);
        } catch (Exception e) {
            e.printStackTrace();
            emitter.onNext("null");
            Log.e(TAG, "clip:Exception " + outName);
        }
    }

    private void sendBroadcast(String msgId, String resultType, String path, boolean successful) {
        Intent intent = new Intent();
        intent.setAction("com.android.zqc.send");
        intent.putExtra("ecarSendKey", resultType);
        intent.putExtra("result", successful);
        intent.putExtra("msgid", msgId);
        intent.putExtra("filePath", path);
        DvrApplication.getInstance().sendBroadcast(intent);
    }

}
