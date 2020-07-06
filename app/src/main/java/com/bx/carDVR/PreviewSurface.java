package com.bx.carDVR;

import static android.view.WindowManagerPolicy.APPLICATION_MEDIA_SUBLAYER;

import android.graphics.PixelFormat;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceSession;


public class PreviewSurface {
    private final Surface mSurface = new Surface();
    private SurfaceSession mSurfaceSession;
    private SurfaceControl mSurfaceControl;
    private int mSubLayer = APPLICATION_MEDIA_SUBLAYER;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mSurfaceFlags = SurfaceControl.HIDDEN|SurfaceControl.OPAQUE;
    private boolean mIsDestroyed = false;


    public PreviewSurface(int width, int height) {
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        mSurfaceSession = new SurfaceSession();
        mSurfaceControl = new SurfaceControl(mSurfaceSession, "PreviewSurface",
                mSurfaceWidth, mSurfaceHeight, PixelFormat.RGB_565, mSurfaceFlags);
        mSurface.copyFrom(mSurfaceControl);
    }

    public Surface getSurface() {
        return mSurface;
    }

    public int getWidth() {
        return mSurfaceWidth;
    }

    public int getHeight() {
        return mSurfaceHeight;
    }

    public void attach(int locationX, int locationY) {
        SurfaceControl.openTransaction();
        try {
            mSurfaceControl.setLayer(mSubLayer);
            mSurfaceControl.setPosition(locationX, locationY);
            mSurfaceControl.setMatrix(1.0f, 0.0f, 0.0f, 1.0f);
            mSurfaceControl.show();
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    public void detach() {
        SurfaceControl.openTransaction();
        try {
            mSurfaceControl.hide();
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    public void destroy() {
        if (!mIsDestroyed) {
            mIsDestroyed = true;
            mSurface.release();
            mSurfaceControl.destroy();
        }
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }
}
