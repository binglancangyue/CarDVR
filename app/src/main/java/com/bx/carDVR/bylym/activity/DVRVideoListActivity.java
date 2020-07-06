package com.bx.carDVR.bylym.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ClipDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import com.bx.carDVR.Configuration;
import com.bx.carDVR.DVRFileInfo;
import com.bx.carDVR.DVRFileList;
import com.bx.carDVR.DVRService;
import com.bx.carDVR.DvrApplication;
import com.bx.carDVR.R;
import com.bx.carDVR.bylym.model.tools.ToastTool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Altair
 * @date :2019.12.31 上午 11:31
 * @description:
 */
public class DVRVideoListActivity extends Activity {
    private static final int VIDEO_NUM = Configuration.CAMERA_NUM;
    private static final int SUB_PAGE_MAX = 4;
    private static final int MD_NONE = -1;
    private static final int MD_VIDEO = 0;
    private static final int MD_PHOTO = 1;
    private Handler mHandler = new Handler();
    private int mCurVideoId;
    private DVRFileList mList;
    private Map<String, List<String>> mVideoList;
    private Map<String, List<String>> mPicList;
    private ArrayList<String> mVideoListArray;
    private ArrayList<String> mPicListArray;
    private ArrayList<String> mFileListArray;
    private boolean[] mVideoListSelection;
    private boolean[] mPicListSelection;
    private boolean[] mFileListSelection;
    private int mDirListSelectionCnt;
    private int mFileListSelectionCnt;

    private int mListNavId;
    private boolean listSwitchEnabled;
    private boolean listDeleting;

    private int curPlayingMediaType;
    private String curPlayingName;
    private boolean isErrorPlay;
    private boolean isVideoPlayback;
    private boolean isRefreshTimeEnabled;
    private Timer mRecordingTimer;
    private Timer mPlayTimeTimer;

    private ImageView[] mTabItems = new ImageView[SUB_PAGE_MAX];
    private View[] mSelectVideoBtns = new View[VIDEO_NUM];
    private ViewAnimator mListSwitcher;
    private ListView[] mListViews = new ListView[2];
    private View mListCenterView;
    private View mListBackView;
    private View mListRefreshView;
    private Button mListDeleteBtn;
    private Button mListCancelBtn;
    private TextView mNoFileView;
    private TextView mFilePathView;
    private View mVideoPlayView;
    private SurfaceView mPlayingView;
    private View mPhotoPlayView;
    private ImageView mPhotoView;
    private TextView mCurVideoNameView, mCurPhotoNameView;
    private ImageView mTimeBarHlView;
    private ImageView mTimeBarIconView;
    private ImageView mPlayPauseView;
    private TextView mCurTimeView, mTotalTimeView;
    private MediaPlayer mMediaPlayer;
    private Bitmap mBitmap;
    private ViewGroup mPlayingPageParent;
    private DVRService.RecorderInterface mRecorder;
    private Activity mActivity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
//        View parent = getLayoutInflater().inflate(R.layout.activity_video_list, null);
        setContentView(R.layout.activity_video_list);
        initList();
        initPlayer();

        mRecorder = DvrApplication.getInstance().getRecorderInterface();
        updateListInfo();
        resetPlayState();
    }


    private boolean switchVideo(int idx) {
        if (idx == mCurVideoId || idx < 0 || idx >= VIDEO_NUM) {
            return false;
        }
        mCurVideoId = idx;
        return true;
    }

    private void updatePlayingTime() {
        if (isRefreshTimeEnabled == false) {
            return;
        }

        long curShowTime = 0;
        long totalShowTime = 0;
        long cm = 0;
        long cs = 0;
        long tm = 0;
        long ts = 0;

        if (mMediaPlayer != null && curPlayingName != null && curPlayingMediaType == MD_VIDEO && isErrorPlay == false) {
            curShowTime = mMediaPlayer.getCurrentPosition();
            totalShowTime = mMediaPlayer.getDuration();
            cm = curShowTime / 1000 / 60 % 100;
            cs = curShowTime / 1000 % 60;
            tm = totalShowTime / 1000 / 60 % 100;
            ts = totalShowTime / 1000 % 60;
        }

        mCurTimeView.setText(String.format("%02d:%02d", cm, cs));
        mTotalTimeView.setText(String.format("%02d:%02d", tm, ts));

        double percent;
        if (totalShowTime > 0) {
            percent = (double) curShowTime / totalShowTime;
        } else {
            percent = 0;
        }

        ClipDrawable clip = (ClipDrawable) mTimeBarHlView.getDrawable();
        clip.setLevel((int) (percent * 10000));
        mTimeBarIconView.setX((float) ((mTimeBarHlView.getWidth() - mTimeBarIconView.getWidth()) * percent));
    }

    private void initPlayer() {
        mVideoPlayView = findViewById(R.id.video_play);
        mPlayingView = (SurfaceView) findViewById(R.id.video_play_surface);
        mCurVideoNameView = (TextView) findViewById(R.id.current_video_name);
        mPhotoPlayView = findViewById(R.id.photo_play);
        mPhotoView = (ImageView) findViewById(R.id.photo_play_image);
        mCurPhotoNameView = (TextView) findViewById(R.id.current_photo_name);
        mTimeBarHlView = (ImageView) findViewById(R.id.time_bar_hl);
        mTimeBarIconView = (ImageView) findViewById(R.id.time_bar_dot);
        mCurTimeView = (TextView) findViewById(R.id.current_time);
        mTotalTimeView = (TextView) findViewById(R.id.total_time);

        mPlayPauseView = (ImageView) findViewById(R.id.play);
        mPlayPauseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (curPlayingMediaType == MD_VIDEO && curPlayingName != null && isErrorPlay == false) {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                        mPlayPauseView.setImageResource(R.drawable.play_selector);
                    } else {
                        mMediaPlayer.start();
                        mPlayPauseView.setImageResource(R.drawable.pause_selector);
                    }
                }
            }
        });
        findViewById(R.id.prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (curPlayingName != null) {
                    String name = fileListSearchPrev(curPlayingName);
                    if (name == null) {
                        ToastTool.showToast(R.string.file_not_found);
                    } else if (name.equals("first")) {
                        ToastTool.showToast(R.string.is_first_one);
                    } else {
                        playFile(name);
                    }
                }
            }
        });
        findViewById(R.id.next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (curPlayingName != null) {
                    String name = fileListSearchNext(curPlayingName);
                    if (name == null) {
                        ToastTool.showToast(R.string.file_not_found);
                    } else if (name.equals("last")) {
                        ToastTool.showToast(R.string.is_last_one);
                    } else {
                        playFile(name);
                    }
                }
            }
        });

        findViewById(R.id.time).setOnTouchListener(new View.OnTouchListener() {
            private double percent;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE: {
                        if (curPlayingMediaType == MD_VIDEO && curPlayingName != null && isErrorPlay == false) {
                            long w = v.getWidth();
                            long x = (long) event.getX();

                            if (x < 0) {
                                percent = 0;
                            } else if (x > w) {
                                percent = 1;
                            } else {
                                percent = (double) x / w;
                            }
                        } else {
                            percent = 0;
                        }

                        ClipDrawable clip = (ClipDrawable) mTimeBarHlView.getDrawable();
                        clip.setLevel((int) (percent * 10000));
                        mTimeBarIconView.setX((float) ((mTimeBarHlView.getWidth() - mTimeBarIconView.getWidth()) * percent));
                        isRefreshTimeEnabled = false;
                    }
                    break;
                    case MotionEvent.ACTION_UP:
                        if (curPlayingMediaType == MD_VIDEO && curPlayingName != null && isErrorPlay == false) {
                            try {
                                mMediaPlayer.seekTo((int) (mMediaPlayer.getDuration() * percent));
                            } catch (IllegalStateException e) {

                            }
                            isRefreshTimeEnabled = true;
                        }
                        break;
                }

                return true;
            }
        });

        initMediaPlayer();
    }

    private void initMediaPlayer() {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                curPlayingName = null;
                mCurVideoNameView.setText(null);
                mPlayPauseView.setImageResource(R.drawable.play_selector);
                mp.reset();
                isVideoPlayback = false;
            }
        });
        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    stopPlaying();
                    resetPlayState();
                    mp.reset();
                    mp.release();
                    initMediaPlayer();
                    return false;
                } else {
                    ToastTool.showToast(R.string.file_error);
                    mp.reset();
                    return true;
                }
            }
        });
    }

    private void resetPlayState() {
        mPlayTimeTimer = new Timer();
        mPlayTimeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updatePlayingTime();
                    }
                });
            }
        }, 200, 200);

        curPlayingName = null;
        curPlayingMediaType = MD_NONE;
        isVideoPlayback = false;
        isErrorPlay = false;
        isRefreshTimeEnabled = true;
        mCurVideoNameView.setText(null);
        mCurPhotoNameView.setText(null);
        mPhotoView.setImageBitmap(null);
        mPhotoPlayView.setVisibility(View.INVISIBLE);

        updatePlayingTime();
    }

    private void stopPlaying() {
        if (mPlayTimeTimer != null) {
            mPlayTimeTimer.cancel();
            mPlayTimeTimer = null;
        }

        if (mMediaPlayer != null && isVideoPlayback) {
            try {
                mMediaPlayer.stop();
            } catch (IllegalStateException e) {

            }
            mMediaPlayer.reset();
            isVideoPlayback = false;
        }
    }

    private void playFile(String name) {
        String ext = name.substring(name.lastIndexOf("."));
        String path = mList.findFilePath(name);
        if (ext.equals(DVRFileInfo.VIDEO_FILE_EXT) && name.contains(DVRFileInfo.VIDEO_FILE_NAME_HEADER)) {
            mVideoPlayView.setVisibility(View.VISIBLE);
            mPhotoPlayView.setVisibility(View.INVISIBLE);

            boolean playFailure = false;

            if (isVideoPlayback) {
                if (mMediaPlayer.isPlaying()) {
                    try {
                        mMediaPlayer.stop();
                    } catch (IllegalStateException e) {

                    }
                }
                mMediaPlayer.reset();
                isVideoPlayback = false;
            }

            mMediaPlayer.setDisplay(mPlayingView.getHolder());
            try {
                mMediaPlayer.setDataSource(path);
                try {
                    mMediaPlayer.prepare();
                    mMediaPlayer.start();
                } catch (IOException e) {
                    mMediaPlayer.reset();
                    playFailure = true;
                    ToastTool.showToast(R.string.file_error);
                }
            } catch (IOException e) {
                mMediaPlayer.reset();
                playFailure = true;
                ToastTool.showToast(R.string.file_open_failure);
            }

            curPlayingMediaType = MD_VIDEO;
            curPlayingName = name;
            isRefreshTimeEnabled = true;
            mPlayPauseView.setVisibility(View.VISIBLE);

            if (playFailure) {
                isErrorPlay = true;
                isVideoPlayback = false;
                mPlayPauseView.setImageResource(R.drawable.play_selector);
                mCurVideoNameView.setText(null);
            } else {
                isErrorPlay = false;
                isVideoPlayback = true;
                mPlayPauseView.setImageResource(R.drawable.pause_selector);
                mCurVideoNameView.setText(name);
            }
        } else if (ext.equals(DVRFileInfo.PIC_FILE_EXT) && name.contains(DVRFileInfo.PIC_FILE_NAME_HEADER)) {
            mPhotoPlayView.setVisibility(View.VISIBLE);

            if (isVideoPlayback) {
                if (mMediaPlayer.isPlaying()) {
                    try {
                        mMediaPlayer.stop();
                    } catch (IllegalStateException e) {

                    }
                }
                mMediaPlayer.reset();
                isVideoPlayback = false;
            }

            boolean playFailure = false;

            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp != null) {
                mPhotoView.setImageBitmap(bmp);
                if (mBitmap != null) {
                    mBitmap.recycle();
                    mBitmap = null;
                }
                mBitmap = bmp;
            } else {
                playFailure = true;
                ToastTool.showToast(R.string.file_open_failure);
            }

            curPlayingMediaType = MD_PHOTO;
            curPlayingName = name;
            mPlayPauseView.setImageResource(R.drawable.play_selector);
            mPlayPauseView.setVisibility(View.INVISIBLE);

            if (playFailure) {
                mCurPhotoNameView.setText(null);
            } else {
                mCurPhotoNameView.setText(name);
            }
        }
    }

    private String fileListSearchPrev(String name) {
        if (name == null) {
            return null;
        }

        String ext = name.substring(name.lastIndexOf("."));
        String curDirName;
        Map<String, List<String>> list;
        List<String> dirListArray;

        if (ext.equals(DVRFileInfo.VIDEO_FILE_EXT) && name.contains(DVRFileInfo.VIDEO_FILE_NAME_HEADER)) {
            int start =
                    name.lastIndexOf(DVRFileInfo.VIDEO_FILE_NAME_HEADER) + DVRFileInfo.VIDEO_FILE_NAME_HEADER.length();
            curDirName = name.substring(start, start + 8);
            list = mVideoList;
            dirListArray = mVideoListArray;
        } else if (ext.equals(DVRFileInfo.PIC_FILE_EXT) && name.contains(DVRFileInfo.PIC_FILE_NAME_HEADER)) {
            int start =
                    name.lastIndexOf(DVRFileInfo.PIC_FILE_NAME_HEADER) + DVRFileInfo.PIC_FILE_NAME_HEADER.length();
            curDirName = name.substring(start, start + 8);
            list = mPicList;
            dirListArray = mPicListArray;
        } else {
            return null;
        }

        int curDirIndex = dirListArray.indexOf(curDirName);
        if (curDirIndex >= 0 && curDirIndex < dirListArray.size()) {
            List<String> curFileListArray = list.get(dirListArray.get(curDirIndex));
            if (curFileListArray != null) {
                int curFileIndex = curFileListArray.indexOf(name);
                if (curFileIndex > 0) {
                    return curFileListArray.get(curFileIndex - 1);
                } else if (curDirIndex > 0) {
                    String prevDirName = dirListArray.get(curDirIndex - 1);
                    List<String> fileListArrayOfPrevDir = list.get(prevDirName);
                    if (fileListArrayOfPrevDir != null && fileListArrayOfPrevDir.size() > 0) {
                        return fileListArrayOfPrevDir.get(fileListArrayOfPrevDir.size() - 1);
                    }
                } else {
                    return "first";
                }
            }
        }

        return null;
    }

    private String fileListSearchNext(String name) {
        if (name == null) {
            return null;
        }

        String ext = name.substring(name.lastIndexOf("."));
        String curDirName;
        Map<String, List<String>> list;
        List<String> dirListArray;

        if (ext.equals(DVRFileInfo.VIDEO_FILE_EXT) && name.contains(DVRFileInfo.VIDEO_FILE_NAME_HEADER)) {
            int start =
                    name.lastIndexOf(DVRFileInfo.VIDEO_FILE_NAME_HEADER) + DVRFileInfo.VIDEO_FILE_NAME_HEADER.length();
            curDirName = name.substring(start, start + 8);
            list = mVideoList;
            dirListArray = mVideoListArray;
        } else if (ext.equals(DVRFileInfo.PIC_FILE_EXT) && name.contains(DVRFileInfo.PIC_FILE_NAME_HEADER)) {
            int start =
                    name.lastIndexOf(DVRFileInfo.PIC_FILE_NAME_HEADER) + DVRFileInfo.PIC_FILE_NAME_HEADER.length();
            curDirName = name.substring(start, start + 8);
            list = mPicList;
            dirListArray = mPicListArray;
        } else {
            return null;
        }

        int curDirIndex = dirListArray.indexOf(curDirName);
        if (curDirIndex >= 0 && curDirIndex < dirListArray.size()) {
            List<String> curFileListArray = list.get(dirListArray.get(curDirIndex));
            if (curFileListArray != null) {
                int curFileIndex = curFileListArray.indexOf(name);
                if (curFileIndex >= 0) {
                    if (curFileIndex < (curFileListArray.size() - 1)) {
                        return curFileListArray.get(curFileIndex + 1);
                    } else {
                        if (curDirIndex < (dirListArray.size() - 1)) {
                            String nextDirName = dirListArray.get(curDirIndex + 1);
                            List<String> fileListArrayOfNextDir = list.get(nextDirName);
                            if (fileListArrayOfNextDir != null && fileListArrayOfNextDir.size() > 0) {
                                return fileListArrayOfNextDir.get(0);
                            }
                        } else {
                            return "last";
                        }
                    }
                }
            }
        }

        return null;
    }

    private interface MyListAdapter {
        void notifyChanged();
    }

    private void initList() {
        mListCenterView = findViewById(R.id.list_center);
        mListSwitcher = findViewById(R.id.list_switcher);
        mNoFileView = findViewById(R.id.no_file);

        mFilePathView = findViewById(R.id.file_path_str);
        mFilePathView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int items_id;
                if (VIDEO_NUM == 2) {
                    items_id = R.array.channel_items_2;
                } else if (VIDEO_NUM == 3) {
                    items_id = R.array.channel_items_3;
                } else if (VIDEO_NUM == 4) {
                    items_id = R.array.channel_items_4;
                } else {
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle(R.string.channel_select);
                builder.setSingleChoiceItems(items_id, mCurVideoId,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (switchVideo(which)) {
                                    stopPlaying();
                                    resetPlayState();
                                    updateListInfo();
                                }
                                dialog.cancel();
                            }
                        });
                builder.setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.create().show();
            }
        });
        if (VIDEO_NUM > 1) {
            mFilePathView.setVisibility(View.VISIBLE);
        } else {
            mFilePathView.setVisibility(View.GONE);
        }

        mListBackView = findViewById(R.id.list_back);
        mListBackView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listSwitchEnabled == false) {
                    return;
                }

                listBack();
                listSwitchEnabled = false;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        listSwitchEnabled = true;
                    }
                }, 500);
            }
        });

        mListRefreshView = findViewById(R.id.list_refresh);
        mListRefreshView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Animation animRotate = AnimationUtils.loadAnimation(mActivity,
                        R.anim.icon_rotate);
                final Animation animFadeOut = AnimationUtils.loadAnimation(mActivity,
                        R.anim.view_fade_out);
                final Animation animFadeIn = AnimationUtils.loadAnimation(mActivity,
                        R.anim.view_fade_in);

                animFadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        updateListInfo();
                        mListCenterView.startAnimation(animFadeIn);
                    }
                });
                animFadeIn.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mListRefreshView.setClickable(true);
                    }
                });

                mListRefreshView.startAnimation(animRotate);
                mListRefreshView.setClickable(false);
                mListCenterView.startAnimation(animFadeOut);
                listSwitchEnabled = false;
            }
        });

        mListDeleteBtn = findViewById(R.id.list_delete);
        mListDeleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int fListCnt;
                final int fListId = mListNavId;

                if (fListId == 0) {
                    fListCnt = mDirListSelectionCnt;
                } else if (fListId == 1) {
                    fListCnt = mFileListSelectionCnt;
                } else {
                    fListCnt = 0;
                }

                if (listDeleting || fListCnt <= 0) {
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle(R.string.delete_file);
                builder.setMessage(
                        mActivity.getString(R.string.total_first) +
                                fListCnt +
                                mActivity.getString(R.string.item) +
                                mActivity.getString(R.string.will_be_deleted)
                );
                builder.setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listDeleting || fListCnt <= 0) {
                            return;
                        }

                        mListDeleteBtn.setEnabled(false);

                        final ProgressDialog progressDialog = new ProgressDialog(mActivity);
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        progressDialog.setCanceledOnTouchOutside(false);
                        progressDialog.setCancelable(false);
                        progressDialog.setMessage(
                                mActivity.getString(R.string.deleting) +
                                        mActivity.getString(R.string.total) +
                                        fListCnt +
                                        mActivity.getString(R.string.item_dot)
                        );
                        progressDialog.show();

                        final DVRFileList fList = mList;
                        final ArrayList<String> fVideoListArray = mVideoListArray;
                        final ArrayList<String> fPicListArray = mPicListArray;
                        final ArrayList<String> fFileListArray = mFileListArray;
                        final boolean[] fVideoListSelection;
                        final boolean[] fPicListSelection;
                        final boolean[] fFileListSelection;

                        if (fListId == 0) {
                            fVideoListSelection = new boolean[mVideoListSelection.length];
                            fPicListSelection = new boolean[mPicListSelection.length];
                            fFileListSelection = null;
                            System.arraycopy(mVideoListSelection, 0, fVideoListSelection, 0,
                                    fVideoListSelection.length);
                            System.arraycopy(mPicListSelection, 0, fPicListSelection, 0,
                                    fPicListSelection.length);
                        } else if (fListId == 1) {
                            fVideoListSelection = null;
                            fPicListSelection = null;
                            fFileListSelection = new boolean[mFileListSelection.length];
                            System.arraycopy(mFileListSelection, 0, fFileListSelection, 0,
                                    fFileListSelection.length);
                        } else {
                            fVideoListSelection = null;
                            fPicListSelection = null;
                            fFileListSelection = null;
                        }

                        listDeleting = true;
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {

                                }

                                final String[] fVideoListItemsDeleted;
                                final String[] fPicListItemsDeleted;
                                final String[] fFileListItemsDeleted;

                                if (fVideoListSelection != null) {
                                    fVideoListItemsDeleted = new String[fVideoListSelection.length];
                                    for (int i = 0; i < fVideoListSelection.length; i++) {
                                        if (fVideoListSelection[i]) {
                                            String item = fVideoListArray.get(i);
                                            fList.deleteVideoDirectory(item);
                                            fVideoListItemsDeleted[i] = item;
                                        } else {
                                            fVideoListItemsDeleted[i] = null;
                                        }
                                    }
                                } else {
                                    fVideoListItemsDeleted = null;
                                }

                                if (fPicListSelection != null) {
                                    fPicListItemsDeleted = new String[fPicListSelection.length];
                                    for (int i = 0; i < fPicListSelection.length; i++) {
                                        if (fPicListSelection[i]) {
                                            String item = fPicListArray.get(i);
                                            fList.deletePictureDirectory(item);
                                            fPicListItemsDeleted[i] = item;
                                        } else {
                                            fPicListItemsDeleted[i] = null;
                                        }
                                    }
                                } else {
                                    fPicListItemsDeleted = null;
                                }

                                if (fFileListSelection != null) {
                                    fFileListItemsDeleted = new String[fFileListSelection.length];
                                    for (int i = 0; i < fFileListSelection.length; i++) {
                                        if (fFileListSelection[i]) {
                                            String item = fFileListArray.get(i);
                                            fList.deleteFile(item);
                                            fFileListItemsDeleted[i] = item;
                                        } else {
                                            fFileListItemsDeleted[i] = null;
                                        }
                                    }
                                } else {
                                    fFileListItemsDeleted = null;
                                }

                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (fVideoListItemsDeleted != null) {
                                            for (String item : fVideoListItemsDeleted) {
                                                if (item != null) {
                                                    fVideoListArray.remove(item);
                                                }
                                            }
                                        }

                                        if (fPicListItemsDeleted != null) {
                                            for (String item : fPicListItemsDeleted) {
                                                if (item != null) {
                                                    fPicListArray.remove(item);
                                                }
                                            }
                                        }

                                        if (fFileListItemsDeleted != null) {
                                            for (String item : fFileListItemsDeleted) {
                                                if (item != null) {
                                                    fFileListArray.remove(item);
                                                }
                                            }
                                        }

                                        if (fVideoListSelection != null || fPicListSelection != null) {
                                            mVideoListSelection = null;
                                            mPicListSelection = null;
                                            mDirListSelectionCnt = 0;
                                            ((MyListAdapter) ((ExpandableListView) mListViews[0]).getExpandableListAdapter()).notifyChanged();
                                        }

                                        if (fFileListSelection != null) {
                                            mFileListSelection = null;
                                            mFileListSelectionCnt = 0;
                                            ((MyListAdapter) mListViews[1].getAdapter()).notifyChanged();
                                        }

                                        mListDeleteBtn.setVisibility(View.GONE);
                                        mListCancelBtn.setVisibility(View.GONE);
                                        mListBackView.setVisibility(View.VISIBLE);
                                        mListRefreshView.setVisibility(View.VISIBLE);
                                        listDeleting = false;
                                        progressDialog.dismiss();
                                    }
                                });

                            }
                        }.start();
                    }
                });
                builder.create().show();
            }
        });

        mListCancelBtn = (Button) findViewById(R.id.list_cancel);
        mListCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mVideoListSelection = null;
                mPicListSelection = null;
                mFileListSelection = null;
                mDirListSelectionCnt = 0;
                mFileListSelectionCnt = 0;

                if (mListViews[0] != null) {
                    ((MyListAdapter) ((ExpandableListView) mListViews[0]).getExpandableListAdapter()).notifyChanged();
                }
                if (mListViews[1] != null) {
                    ((MyListAdapter) mListViews[1].getAdapter()).notifyChanged();
                }

                mListDeleteBtn.setVisibility(View.GONE);
                mListCancelBtn.setVisibility(View.GONE);
                mListBackView.setVisibility(View.VISIBLE);
                mListRefreshView.setVisibility(View.VISIBLE);
            }
        });
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

        mListSwitcher.removeAllViews();

        if (hasList) {
            Iterator<Map.Entry<String, List<String>>> iterator;
            Map.Entry<String, List<String>> entry;
            String name;

            mVideoListSelection = null;
            mPicListSelection = null;
            mFileListSelection = null;
            mDirListSelectionCnt = 0;
            mFileListSelectionCnt = 0;

            mVideoList = mList.getVideoList();
            mVideoListArray = new ArrayList<String>();
            mVideoListArray.ensureCapacity(mVideoList.size());
            iterator = mVideoList.entrySet().iterator();
            while (iterator.hasNext()) {
                entry = iterator.next();
                name = entry.getKey();
                mVideoListArray.add(name);
            }

            mPicList = mList.getPictureList();
            mPicListArray = new ArrayList<String>();
            mPicListArray.ensureCapacity(mPicList.size());
            iterator = mPicList.entrySet().iterator();
            while (iterator.hasNext()) {
                entry = iterator.next();
                name = entry.getKey();
                mPicListArray.add(name);
            }

            View parent = getLayoutInflater().inflate(R.layout.dir_list, null);
            ExpandableListView mDirListView = parent.findViewById(R.id.dir_list_view);
            mDirListView.setAdapter(new DirListAdapter());
            mDirListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (listSwitchEnabled == false) {
                        return true;
                    }

                    if (mListViews[mListNavId] == null) {
                        return true;
                    }

                    if (mListViews[mListNavId] != parent) {
                        return true;
                    }

                    if (groupPosition == 0) {
                        if (mVideoListSelection != null) {
                            ImageView selBtn = (ImageView) v.findViewById(R.id.sel_btn);
                            if (mVideoListSelection[childPosition]) {
                                mVideoListSelection[childPosition] = false;
                                mDirListSelectionCnt--;
                                selBtn.setImageResource(R.mipmap.file_no_select_ic);
                            } else {
                                mVideoListSelection[childPosition] = true;
                                mDirListSelectionCnt++;
                                selBtn.setImageResource(R.mipmap.file_select_ic);
                            }

                            if (mListNavId == 0) {
                                if (mDirListSelectionCnt > 0) {
                                    mListDeleteBtn.setEnabled(true);
                                } else {
                                    mListDeleteBtn.setEnabled(false);
                                }
                            }

                            return true;
                        }
                    } else if (groupPosition == 1) {
                        if (mPicListSelection != null) {
                            ImageView selBtn = (ImageView) v.findViewById(R.id.sel_btn);
                            if (mPicListSelection[childPosition]) {
                                mPicListSelection[childPosition] = false;
                                mDirListSelectionCnt--;
                                selBtn.setImageResource(R.mipmap.file_no_select_ic);
                            } else {
                                mPicListSelection[childPosition] = true;
                                mDirListSelectionCnt++;
                                selBtn.setImageResource(R.mipmap.file_select_ic);
                            }

                            if (mListNavId == 0) {
                                if (mDirListSelectionCnt > 0) {
                                    mListDeleteBtn.setEnabled(true);
                                } else {
                                    mListDeleteBtn.setEnabled(false);
                                }
                            }

                            return true;
                        }
                    }

                    selectDir(groupPosition, childPosition);

                    listSwitchEnabled = false;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            listSwitchEnabled = true;
                        }
                    }, 500);

                    return true;
                }
            });
            mDirListView.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                                               long id) {
                    if (listSwitchEnabled == false) {
                        return true;
                    }

                    if (mListNavId != 0 || mListViews[mListNavId] == null) {
                        return true;
                    }

                    if (mListViews[mListNavId] != parent) {
                        return true;
                    }

                    if (mVideoListSelection == null || mPicListSelection == null) {
                        mDirListSelectionCnt = 0;
                        mVideoListSelection = new boolean[mVideoListArray.size()];
                        mPicListSelection = new boolean[mPicListArray.size()];
                        mListDeleteBtn.setVisibility(View.VISIBLE);
                        mListCancelBtn.setVisibility(View.VISIBLE);
                        mListBackView.setVisibility(View.GONE);
                        mListRefreshView.setVisibility(View.GONE);
                        mListDeleteBtn.setEnabled(false);
                        ((MyListAdapter) ((ExpandableListView) mListViews[mListNavId]).getExpandableListAdapter()).notifyChanged();
                    }

                    return true;
                }
            });

            mListSwitcher.addView(parent);
            listSwitchEnabled = true;
            mListNavId = 0;
            mListViews[mListNavId] = mDirListView;

            mNoFileView.setVisibility(View.GONE);
            mListSwitcher.setVisibility(View.VISIBLE);
        } else {
            mNoFileView.setVisibility(View.VISIBLE);
            mListSwitcher.setVisibility(View.GONE);
        }

        mListDeleteBtn.setVisibility(View.GONE);
        mListCancelBtn.setVisibility(View.GONE);
        mListBackView.setVisibility(View.VISIBLE);
        mListRefreshView.setVisibility(View.VISIBLE);
        mListBackView.setEnabled(false);

        if (VIDEO_NUM > 1) {
            int[] str_ids = {R.string.channel1, R.string.channel2, R.string.channel3,
                    R.string.channel4};
            mFilePathView.setText(str_ids[mCurVideoId]);
        }
    }


    private class DirListAdapter extends BaseExpandableListAdapter implements MyListAdapter {
        private int mVideoItemCnt;
        private int mPicItemCnt;

        public DirListAdapter() {
            mVideoItemCnt = mVideoListArray.size();
            mPicItemCnt = mPicListArray.size();
        }

        @Override
        public int getGroupCount() {
            return 2;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            if (groupPosition == 0) {
                return mVideoItemCnt;
            } else if (groupPosition == 1) {
                return mPicItemCnt;
            }

            return 0;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return null;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return null;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return 0;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                                 ViewGroup parent) {
            View view;
            TextView text;
            ImageView expandIcon;

            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.dir_group_item, null);
            } else {
                view = convertView;
            }

            text = (TextView) view.findViewById(R.id.item_text);
            expandIcon = (ImageView) view.findViewById(R.id.expand_icon);

            if (groupPosition == 0) {
                text.setText("video");
            } else if (groupPosition == 1) {
                text.setText("picture");
            }

            if (isExpanded) {
                expandIcon.setImageResource(R.mipmap.arrow_dn);
            } else {
                expandIcon.setImageResource(R.mipmap.arrow_left);
            }

            return view;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            View view;
            TextView text;
            ImageView selBtn;

            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.dir_list_item, null);
            } else {
                view = convertView;
            }

            text = (TextView) view.findViewById(R.id.item_text);
            selBtn = (ImageView) view.findViewById(R.id.sel_btn);

            if (groupPosition == 0) {
                text.setText(mVideoListArray.get(childPosition));
            } else if (groupPosition == 1) {
                text.setText(mPicListArray.get(childPosition));
            }

            if (groupPosition == 0) {
                if (mVideoListSelection != null) {
                    selBtn.setVisibility(View.VISIBLE);
                    if (mVideoListSelection[childPosition]) {
                        selBtn.setImageResource(R.mipmap.file_select_ic);
                    } else {
                        selBtn.setImageResource(R.mipmap.file_no_select_ic);
                    }
                } else {
                    selBtn.setVisibility(View.GONE);
                }
            } else if (groupPosition == 1) {
                if (mPicListSelection != null) {
                    selBtn.setVisibility(View.VISIBLE);
                    if (mPicListSelection[childPosition]) {
                        selBtn.setImageResource(R.mipmap.file_select_ic);
                    } else {
                        selBtn.setImageResource(R.mipmap.file_no_select_ic);
                    }
                } else {
                    selBtn.setVisibility(View.GONE);
                }
            }

            return view;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        @Override
        public void notifyChanged() {
            mVideoItemCnt = mVideoListArray.size();
            mPicItemCnt = mPicListArray.size();
            notifyDataSetChanged();
        }
    }

    private void selectDir(int groupPosition, int dirIndex) {
        Map<String, List<String>> list;
        List<String> dirListArray;
        String dirName;

        if (groupPosition == 0) {
            list = mVideoList;
            dirListArray = mVideoListArray;
        } else if (groupPosition == 1) {
            list = mPicList;
            dirListArray = mPicListArray;
        } else {
            return;
        }

        dirName = dirListArray.get(dirIndex);
        mFileListArray = (ArrayList<String>) list.get(dirName);

        View parent = getLayoutInflater().inflate(R.layout.file_list, null);
        FrameLayout container = parent.findViewById(R.id.file_list_container);

        mListNavId++;

        if (mFileListArray.size() > 0) {
            ListView mFileListView = parent.findViewById(R.id.file_list_view);
            mFileListView.setAdapter(new FileListAdapter());
            mFileListView.setOnItemClickListener(new ListView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (listSwitchEnabled == false) {
                        return;
                    }

                    if (mListViews[mListNavId] == null) {
                        return;
                    }

                    if (mListViews[mListNavId] != parent) {
                        return;
                    }

                    if (mFileListSelection != null) {
                        ImageView selBtn = (ImageView) view.findViewById(R.id.sel_btn);
                        if (mFileListSelection[position]) {
                            mFileListSelection[position] = false;
                            mFileListSelectionCnt--;
                            selBtn.setImageResource(R.mipmap.file_no_select_ic);
                        } else {
                            mFileListSelection[position] = true;
                            mFileListSelectionCnt++;
                            selBtn.setImageResource(R.mipmap.file_select_ic);
                        }

                        if (mListNavId == 1) {
                            if (mFileListSelectionCnt > 0) {
                                mListDeleteBtn.setEnabled(true);
                            } else {
                                mListDeleteBtn.setEnabled(false);
                            }
                        }
                    } else {
                        selectFile(position);

                        listSwitchEnabled = false;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                listSwitchEnabled = true;
                            }
                        }, 500);
                    }
                }
            });
            mFileListView.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                                               long id) {
                    if (listSwitchEnabled == false) {
                        return true;
                    }

                    if (mListNavId != 1 || mListViews[mListNavId] == null) {
                        return true;
                    }

                    if (mListViews[mListNavId] != parent) {
                        return true;
                    }

                    if (mFileListSelection == null) {
                        mFileListSelectionCnt = 0;
                        mFileListSelection = new boolean[mFileListArray.size()];
                        mListDeleteBtn.setVisibility(View.VISIBLE);
                        mListCancelBtn.setVisibility(View.VISIBLE);
                        mListBackView.setVisibility(View.GONE);
                        mListRefreshView.setVisibility(View.GONE);
                        mListDeleteBtn.setEnabled(false);
                        ((MyListAdapter) mListViews[mListNavId].getAdapter()).notifyChanged();
                    }

                    return true;
                }
            });
            container.removeAllViews();
            container.addView(mFileListView);
            mListViews[mListNavId] = mFileListView;
        } else {
            View noFileView = parent.findViewById(R.id.no_file);
            container.removeAllViews();
            container.addView(noFileView);
            mListViews[mListNavId] = null;
        }

        if (mListSwitcher.getChildAt(1) != null) {
            mListSwitcher.removeViewAt(1);
        }
        mListSwitcher.addView(parent);
        mListSwitcher.setInAnimation(mActivity, R.anim.slide_in_right);
        mListSwitcher.setOutAnimation(mActivity, R.anim.slide_out_left);
        mListSwitcher.showNext();
        mListBackView.setEnabled(true);
    }


    private class FileListAdapter extends BaseAdapter implements MyListAdapter {
        private int mItemCount;


        public FileListAdapter() {
            mItemCount = mFileListArray.size();
        }

        @Override
        public int getCount() {
            return mItemCount;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            TextView text;
            ImageView selBtn;

            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.file_list_item, null);
            } else {
                view = convertView;
            }

            text = (TextView) view.findViewById(R.id.item_text);
            selBtn = (ImageView) view.findViewById(R.id.sel_btn);

            text.setText(mFileListArray.get(position));

            if (mFileListSelection != null) {
                selBtn.setVisibility(View.VISIBLE);
                if (mFileListSelection[position]) {
                    selBtn.setImageResource(R.mipmap.file_select_ic);
                } else {
                    selBtn.setImageResource(R.mipmap.file_no_select_ic);
                }
            } else {
                selBtn.setVisibility(View.GONE);
            }

            return view;
        }

        @Override
        public void notifyChanged() {
            mItemCount = mFileListArray.size();
            notifyDataSetChanged();
        }
    }

    private void selectFile(int fileIndex) {
        if (mListNavId == 1 && mListViews[mListNavId] != null) {
            String item = mFileListArray.get(fileIndex);
            playFile(item);
        }
    }

    private void listBack() {
        if (mListNavId > 0) {
            mListNavId--;
            mListSwitcher.setInAnimation(mActivity, R.anim.slide_in_left);
            mListSwitcher.setOutAnimation(mActivity, R.anim.slide_out_right);
            mListSwitcher.showPrevious();
            mListBackView.setEnabled(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlaying();
    }
}
