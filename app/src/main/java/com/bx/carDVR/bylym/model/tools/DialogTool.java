package com.bx.carDVR.bylym.model.tools;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.bx.carDVR.R;
import com.bx.carDVR.bylym.model.NotifyMessageManager;

import java.lang.reflect.Field;

/**
 * @author Altair
 * @date :2020.05.29 下午 04:47
 * @description:
 */
public class DialogTool {
    private AlertDialog stopRecordDialog;
    private AlertDialog formatSDCardDialog;
    private AlertDialog settingDialog;

    public void showStopRecordingDialog(Context context) {
        if (stopRecordDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.dialog_title_stop_recording);
            builder.setMessage(R.string.dialog_message_stop_recording);
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissStopRecordDialog();
                }
            });
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissStopRecordDialog();
                    NotifyMessageManager.getInstance().updateDVRUI(0);
                }
            });
            stopRecordDialog = builder.create();
        }
        showDialog(stopRecordDialog);
    }

    public void showFormatDialog(Context context) {
        if (formatSDCardDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.dialog_title_format);
            builder.setMessage(R.string.dialog_message_format_sd_card);
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissFormatDialog();
                }
            });
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissFormatDialog();
                    NotifyMessageManager.getInstance().updateDVRUI(1);
                }
            });
            formatSDCardDialog = builder.create();
        }
        showDialog(formatSDCardDialog);
    }

    public void showSettingDialog(Context context) {
        if (settingDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.dialog_title_setting);
            builder.setMessage(R.string.dialog_message_setting_stop_recording);
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissSettingDialog();
                }
            });
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissSettingDialog();
                    NotifyMessageManager.getInstance().updateDVRUI(2);
                }
            });
            settingDialog = builder.create();
        }
        showDialog(settingDialog);
    }

    private void showDialog(AlertDialog alertDialog) {
        if (!alertDialog.isShowing()) {
            focusNotAle(alertDialog.getWindow());
            alertDialog.show();
            setDialogTextSize(alertDialog);
            hideNavigationBar(alertDialog.getWindow());
            clearFocusNotAle(alertDialog.getWindow());
        }
    }


    public void dismissStopRecordDialog() {
        if (stopRecordDialog != null) {
            stopRecordDialog.dismiss();
        }
    }

    public void dismissSettingDialog() {
        if (settingDialog != null) {
            settingDialog.dismiss();
        }
    }

    public void dismissFormatDialog() {
        if (formatSDCardDialog != null) {
            formatSDCardDialog.dismiss();
        }
    }

    public void dismissDialog() {
        dismissStopRecordDialog();
        dismissFormatDialog();
        dismissSettingDialog();
        settingDialog = null;
        stopRecordDialog = null;
        formatSDCardDialog = null;
    }

    /**
     * dialog 需要全屏的时候用，和clearFocusNotAle() 成对出现
     * 在show 前调用  focusNotAle   show后调用clearFocusNotAle
     *
     * @param window
     */
    public void focusNotAle(Window window) {
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    /**
     * dialog 需要全屏的时候用，focusNotAle() 成对出现
     * 在show 前调用  focusNotAle   show后调用clearFocusNotAle
     *
     * @param window
     */
    public void clearFocusNotAle(Window window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    public void hideNavigationBar(Window window) {
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        window.getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        //布局位于状态栏下方
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        //全屏
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        //隐藏导航栏
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                if (Build.VERSION.SDK_INT >= 19) {
                    uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                } else {
                    uiOptions |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
                }
                window.getDecorView().setSystemUiVisibility(uiOptions);
            }
        });
    }

    private void setDialogTextSize(AlertDialog builder) {
        builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(27);
        builder.getButton(DialogInterface.BUTTON_NEGATIVE).setTextSize(27);
        try {
            //获取mAlert对象
            Field mAlert = AlertDialog.class.getDeclaredField("mAlert");
            mAlert.setAccessible(true);
            Object mAlertController = mAlert.get(builder);

            //获取mTitleView并设置大小颜色
            Field mTitle = mAlertController.getClass().getDeclaredField("mTitleView");
            mTitle.setAccessible(true);
            TextView mTitleView = (TextView) mTitle.get(mAlertController);
            mTitleView.setTextSize(30);

            //获取mMessageView并设置大小颜色
            Field mMessage = mAlertController.getClass().getDeclaredField("mMessageView");
            mMessage.setAccessible(true);
            TextView mMessageView = (TextView) mMessage.get(mAlertController);
            mMessageView.setTextSize(27);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

}
