package com.bx.carDVR.bylym.model.tools;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;


import com.bx.carDVR.bylym.activity.DVRHomeActivity;

import java.util.ArrayList;
import java.util.List;

public class RequestPermissionTool {
    private AlertDialog mPermissionDialog;
    private Context mContext;
    private List<String> mPermissionList = new ArrayList<>();

    public RequestPermissionTool(Context context) {
        this.mContext = context;
    }

    //权限判断和申请
    public void initPermission(String[] permissions, DVRHomeActivity activity) {
        mPermissionList.clear();//清空没有通过的权限
        //逐个判断你要的权限是否已经通过
        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(mContext, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[i]);//添加还未授予的权限
            }
        }
        Log.d("taaaa",""+mPermissionList.size());
        //申请权限
        if (mPermissionList.size() > 0) {//有权限没有通过，需要申请
            ActivityCompat.requestPermissions(activity, permissions, 1000);
        } else {

        }
    }


    /**
     * 不再提示权限时的展示对话框
     */
    public void showPermissionDialog() {
        if (mPermissionDialog == null) {
            String mPackName = "com.bixin.launcher_tw";
            mPermissionDialog = new AlertDialog.Builder(mContext)
                    .setMessage("已禁用权限，请手动授予")
                    .setPositiveButton("设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            cancelPermissionDialog();
                            Uri packageURI = Uri.parse("package:" + mPackName);
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                            mContext.startActivity(intent);
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //关闭页面或者做其他操作
                            cancelPermissionDialog();
                        }
                    })
                    .create();
        }
        mPermissionDialog.show();
    }

    //关闭对话框
    private void cancelPermissionDialog() {
        mPermissionDialog.cancel();
    }
}
