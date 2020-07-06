package com.bx.carDVR.bylym.model.tools;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;

import com.bx.carDVR.DvrApplication;

/**
 * @author Altair
 * @date :2020.04.13 上午 10:38
 * @description:
 */
public class CheckCamera {

    private static boolean checkCameraFacing(final int facing) throws CameraAccessException {

        final int cameraCount = Camera.getNumberOfCameras();
        Log.d("TAG", "checkCameraFacing: " + cameraCount);
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, info);
            if (facing == info.facing) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasBackFacingCamera(int id) {
        try {
            return checkCameraFacing(id);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }
}
