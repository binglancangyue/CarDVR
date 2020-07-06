package com.bx.carDVR.bylym.model.tools;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bx.carDVR.DvrApplication;


public class ToastTool {
    private static Toast toast = null;

    public static ToastTool getInstance() {
        return ToastTool.SingletonHolder.sInstance;
    }

    private static class SingletonHolder {
        private static final ToastTool sInstance = new ToastTool();
    }

    public static void showToast(int text) {
        if (toast == null) {
            toast = Toast.makeText(DvrApplication.getInstance(), text, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM,0,50);

            LinearLayout linearLayout = (LinearLayout) toast.getView();
            TextView messageTextView = (TextView) linearLayout.getChildAt(0);
            messageTextView.setTextSize(24);
        } else {
            toast.setText(text);
            toast.setDuration(Toast.LENGTH_SHORT);
        }
        toast.show();
    }
}
